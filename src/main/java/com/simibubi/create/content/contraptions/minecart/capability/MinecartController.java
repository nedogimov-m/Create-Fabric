package com.simibubi.create.content.contraptions.minecart.capability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.minecart.*;
import com.simibubi.create.foundation.utility.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Actual native per-cart persistent attachment; topology never contains a second paid coupling owner. */
public final class MinecartController {
    private record StallData(Vec3 position,Vec3 motion,float yaw,float pitch,boolean motionOnly){
        StallData(Vec3 position,Vec3 motion,float yaw,float pitch){this(position,motion,yaw,pitch,false);}
        static final Codec<StallData> CODEC=RecordCodecBuilder.create(i->i.group(Vec3.CODEC.fieldOf("Pos").forGetter(StallData::position),Vec3.CODEC.fieldOf("Motion").forGetter(StallData::motion),Codec.FLOAT.fieldOf("Yaw").forGetter(StallData::yaw),Codec.FLOAT.fieldOf("Pitch").forGetter(StallData::pitch),Codec.BOOL.optionalFieldOf("MotionOnly",false).forGetter(StallData::motionOnly)).apply(i,StallData::new));
        StallData{if(!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)||!Double.isFinite(motion.x)||!Double.isFinite(motion.y)||!Double.isFinite(motion.z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Nonfinite saved minecart stall");}
        static StallData of(AbstractMinecart cart){return new StallData(cart.position(),cart.getDeltaMovement(),cart.getYRot(),cart.getXRot());}
        void hold(AbstractMinecart cart){cart.setDeltaMovement(Vec3.ZERO);if(!motionOnly){cart.setYRot(yaw);cart.setXRot(pitch);}}
    }
    private record Link(UUID main,UUID connected,float length,boolean contraption,boolean creative,ItemStack paid,boolean legacyUnreceipted){
        static final Codec<Link> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Main").forGetter(Link::main),UUIDUtil.CODEC.fieldOf("Connected").forGetter(Link::connected),Codec.FLOAT.fieldOf("Length").forGetter(Link::length),Codec.BOOL.fieldOf("Contraption").forGetter(Link::contraption),Codec.BOOL.optionalFieldOf("Creative",false).forGetter(Link::creative),ItemStack.OPTIONAL_CODEC.optionalFieldOf("PaidItem",ItemStack.EMPTY).forGetter(Link::paid),Codec.BOOL.optionalFieldOf("LegacyUnreceipted",false).forGetter(Link::legacyUnreceipted)).apply(i,Link::new));
        Link{if(main.equals(connected)||!Float.isFinite(length)||length<1||!paid.isEmpty()&&paid.getCount()!=1||(contraption||creative)&&!paid.isEmpty()||contraption&&creative||legacyUnreceipted&&(creative||!paid.isEmpty()))throw new IllegalArgumentException("Invalid actual minecart link/receipt");}
        UUID other(boolean leading){return leading?connected:main;}
        Link withoutPaid(){return new Link(main,connected,length,contraption,creative,ItemStack.EMPTY,legacyUnreceipted);}
    }
    public enum BaseCustodyKind { RESERVED,RETIRED }
    /** An actual saved BE position or actual owner entity plus actor-local position; no fake BE UUID. */
    public record PickupDestination(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,BlockPos pos,Optional<UUID> actorOwner){
        static final Codec<PickupDestination> CODEC=RecordCodecBuilder.create(i->i.group(net.minecraft.resources.ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION).fieldOf("Dimension").forGetter(PickupDestination::dimension),BlockPos.CODEC.fieldOf("Pos").forGetter(PickupDestination::pos),UUIDUtil.CODEC.optionalFieldOf("ActorOwner").forGetter(PickupDestination::actorOwner)).apply(i,PickupDestination::new));
        public PickupDestination{Objects.requireNonNull(dimension);pos=pos.immutable();actorOwner=Objects.requireNonNull(actorOwner);}
    }
    public record BaseCustody(Optional<UUID> actualEntityOwner,UUID receipt,BaseCustodyKind kind,Optional<PickupDestination> pickupDestination){
        static final Codec<BaseCustody> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.optionalFieldOf("ActualOwner").forGetter(BaseCustody::actualEntityOwner),UUIDUtil.CODEC.fieldOf("Receipt").forGetter(BaseCustody::receipt),Codec.STRING.xmap(BaseCustodyKind::valueOf,BaseCustodyKind::name).fieldOf("Kind").forGetter(BaseCustody::kind),PickupDestination.CODEC.optionalFieldOf("PickupDestination").forGetter(BaseCustody::pickupDestination)).apply(i,BaseCustody::new));
        public BaseCustody(UUID actualOwner,UUID receipt,BaseCustodyKind kind){this(Optional.of(actualOwner),receipt,kind,Optional.empty());}
        public BaseCustody{Objects.requireNonNull(actualEntityOwner);Objects.requireNonNull(receipt);Objects.requireNonNull(kind);Objects.requireNonNull(pickupDestination);if(kind==BaseCustodyKind.RESERVED&&(actualEntityOwner.isEmpty()||pickupDestination.isPresent())||kind==BaseCustodyKind.RETIRED&&actualEntityOwner.isEmpty()&&pickupDestination.isEmpty())throw new IllegalArgumentException("Actual native receiver or explicit canonical Deployer endpoint required");if(pickupDestination.isPresent()&&!pickupDestination.get().actorOwner.equals(actualEntityOwner))throw new IllegalArgumentException("Actual actor receiver identity disagrees with its endpoint");}
        /** RESERVED/native ItemEntity compatibility; a real stationary BE deliberately has no entity UUID. */
        public UUID actualOwner(){return actualEntityOwner.orElse(null);}
    }
    private record Data(Optional<StallData> internal,Optional<StallData> external,Optional<Link> main,Optional<Link> connected,ItemStack packed,List<ItemStack> refunds,Optional<UUID> root,Optional<BaseCustody> custody,boolean legacyEmpty){
        Data(Optional<StallData> internal,Optional<StallData> external,Optional<Link> main,Optional<Link> connected,ItemStack packed,List<ItemStack> refunds,Optional<UUID> root,Optional<BaseCustody> custody){this(internal,external,main,connected,packed,refunds,root,custody,false);}
        static final Codec<Data> CODEC=RecordCodecBuilder.create(i->i.group(StallData.CODEC.optionalFieldOf("InternalStallData").forGetter(Data::internal),StallData.CODEC.optionalFieldOf("StallData").forGetter(Data::external),Link.CODEC.optionalFieldOf("MainCoupling").forGetter(Data::main),Link.CODEC.optionalFieldOf("ConnectedCoupling").forGetter(Data::connected),ItemStack.OPTIONAL_CODEC.optionalFieldOf("PackedReceipt",ItemStack.EMPTY).forGetter(Data::packed),ItemStack.CODEC.listOf().optionalFieldOf("PendingRefunds",List.of()).forGetter(Data::refunds),UUIDUtil.CODEC.optionalFieldOf("MountedRoot").forGetter(Data::root),BaseCustody.CODEC.optionalFieldOf("NativeBaseCustody").forGetter(Data::custody),Codec.BOOL.optionalFieldOf("LegacyEmpty",false).forGetter(Data::legacyEmpty)).apply(i,Data::new));
        Data{refunds=List.copyOf(refunds);if(legacyEmpty&&(internal.isPresent()||external.isPresent()||main.isPresent()||connected.isPresent()||!packed.isEmpty()||!refunds.isEmpty()||root.isPresent()||custody.isPresent()))throw new IllegalArgumentException("Source EMPTY controller cannot own live data");if(custody.isPresent()&&root.isPresent())throw new IllegalArgumentException("An inactive native base cannot claim an active root");if(!packed.isEmpty()&&(root.isPresent()||custody.isPresent()))throw new IllegalArgumentException("One actual packed receipt cannot also claim an active root or retired native base");if(!packed.isEmpty()&&packed.getCount()!=1||refunds.stream().anyMatch(s->s.isEmpty()||s.getCount()!=1))throw new IllegalArgumentException("Cart receipts must retain one exact original item");}
        static Data empty(){return new Data(Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),ItemStack.EMPTY,List.of(),Optional.empty(),Optional.empty());}
    }
    public static final Codec<MinecartController> CODEC=Data.CODEC.xmap(data->new MinecartController(null,data),controller->controller.data);
    public static final AttachmentType<MinecartController> ATTACHMENT=AttachmentRegistry.<MinecartController>builder().persistent(CODEC).initializer(()->new MinecartController(null)).buildAndRegister(Create.asResource("minecart_controller"));
    public static final MinecartController EMPTY=new MinecartController(null,new Data(Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),ItemStack.EMPTY,List.of(),Optional.empty(),Optional.empty(),true));
    /** Initialize the persistent attachment during mod bootstrap, before saved entities are read. */
    public static void register() {}
    private WeakReference<AbstractMinecart> cart;
    private Data data;
    private double knownMaxSpeed;
    public MinecartController(AbstractMinecart cart){this(cart,Data.empty());}
    private MinecartController(AbstractMinecart cart,Data data){this.cart=new WeakReference<>(cart);this.data=data;}
    public static MinecartController get(AbstractMinecart cart){var controller=((AttachmentTarget)cart).getAttachedOrCreate(ATTACHMENT);controller.bind(cart);return controller;}
    public void bind(AbstractMinecart actual){
        if(this==EMPTY)throw new IllegalStateException("Shared EMPTY sentinel cannot bind an actual owner");var previous=cart();if(previous!=null&&previous!=actual)throw new IllegalStateException("Cannot bind one controller to another actual cart");
        if(data.main.isPresent()&&!data.main.get().main.equals(actual.getUUID())||data.connected.isPresent()&&!data.connected.get().connected.equals(actual.getUUID()))throw new IllegalArgumentException("Saved coupling endpoint disagrees with actual native cart UUID");
        if(nativeBaseIsInactive()&&!com.simibubi.create.content.contraptions.mounted.NativeCartState.cargoIsActuallyEmpty(actual))throw new IllegalStateException("An inactive native base cannot expose a second cargo/fuel owner beside its real receipt");
        cart=new WeakReference<>(actual);
    }
    public AbstractMinecart cart(){return cart.get();}
    public boolean isEmpty(){return data.legacyEmpty;}
    public boolean isPresent(){if(isEmpty())return false;var actual=cart();if(actual==null||!actual.isAlive()||!LoadedEntityAccess.isReady(actual.level(),actual.blockPosition()))return false;return actual.level() instanceof ServerLevel server?server.getEntity(actual.getUUID())==actual:actual.level().getEntity(actual.getId())==actual;}
    private void requireServer(){if(isEmpty())throw new IllegalStateException("Source EMPTY controller cannot mutate live ownership");if(!(cart()!=null&&cart().level() instanceof ServerLevel server&&server.getEntity(cart().getUUID())==cart()))throw new IllegalStateException("Actual accepted server cart required");}
    public boolean isLeadingCoupling(){return data.main.isPresent();}
    public boolean isConnectedToCoupling(){return data.connected.isPresent();}
    public boolean isFullyCoupled(){return isLeadingCoupling()&&isConnectedToCoupling();}
    public boolean isCoupledThroughContraption(){return hasContraptionCoupling(true)||hasContraptionCoupling(false);}
    private Optional<Link> link(boolean leading){return leading?data.main:data.connected;}
    public UUID getCoupledCart(boolean leading){return link(leading).map(l->l.other(leading)).orElse(null);}
    public float getCouplingLength(boolean leading){return link(leading).map(Link::length).orElse(0f);}
    public boolean hasContraptionCoupling(boolean leading){return link(leading).map(Link::contraption).orElse(false);}
    public boolean hasPaidCoupling(boolean leading){return link(leading).map(l->!l.paid.isEmpty()).orElse(false);}
    public static final class LinkSnapshot {
        private final MinecartController owner;private final boolean leading;private final Link original;private final ItemStack expectedReceipt;
        private LinkSnapshot(MinecartController owner,boolean leading,Link original){this.owner=owner;this.leading=leading;this.original=original;expectedReceipt=original.paid.copy();}
        public MinecartController owner(){return owner;}public boolean leading(){return leading;}
        public UUID main(){return original.main;}public UUID connected(){return original.connected;}
        public float length(){return original.length;}public boolean contraption(){return original.contraption;}public boolean creative(){return original.creative;}public boolean legacyUnreceipted(){return original.legacyUnreceipted;}
        public ItemStack actualReceipt(){return original.paid;}
        public boolean validate(){return owner.isPresent()&&owner.link(leading).orElse(null)==original&&ItemStack.matches(original.paid,expectedReceipt);}
    }
    public LinkSnapshot inspectLink(boolean leading){return link(leading).map(value->new LinkSnapshot(this,leading,value)).orElse(null);}
    public boolean ownsPackedReceipt(){return !data.packed.isEmpty();}
    public boolean hasPendingRefunds(){return !data.refunds.isEmpty();}
    public Optional<BaseCustody> baseCustody(){return data.custody;}
    public boolean nativeBaseIsInactive(){return ownsPackedReceipt()||data.custody.isPresent();}
    /** Read-only prospective endpoint identity; caller must not mutate or publish while owned here. */
    public ItemStack actualPackedReceipt(){return data.packed;}
    public boolean ownsPackedReceipt(ItemStack exact){return nativeBaseIsActuallyRegistered()&&data.packed==exact&&!exact.isEmpty()&&exact.getCount()==1;}
    private boolean nativeBaseIsActuallyRegistered(){
        var actual=cart();if(actual==null||actual.isRemoved()||!(actual.level() instanceof ServerLevel server))return false;
        return server.getEntity(actual.getUUID())==actual||com.simibubi.create.foundation.utility.NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(actual);
    }
    private void requirePackedSource(){if(!nativeBaseIsActuallyRegistered()||!ownsPackedReceipt())throw new IllegalStateException("Actual registered canonical packed receipt owner required");}
    public ItemStack observePackedReceipt(){return com.simibubi.create.content.contraptions.mounted.PackedCartData.detachedItemCopy(data.packed);}
    public UUID getMountedRoot(){return data.root.orElse(null);}
    public boolean missingMountedRoot(){
        if(data.root.isEmpty())return false;if(!(cart().level() instanceof ServerLevel server))return false;
        if(!(server.getEntity(data.root.get()) instanceof OrientedContraptionEntity root)||!root.hasOwnedStructure()||!LoadedEntityAccess.isReady(server,root.blockPosition()))return true;
        if(root.getVehicle()==cart())return false;
        var pair=root.getCoupledCartsIfPresent();return pair==null||pair.getFirst().cart()!=cart()&&pair.getSecond().cart()!=cart();
    }
    public boolean isStalled(){return data.internal.isPresent()||data.external.isPresent()||nativeBaseIsInactive()||missingMountedRoot();}
    public double getKnownMaxSpeed(){return knownMaxSpeed;}
    public void tick(){
        if(isEmpty()||!isPresent())return;var actual=cart();data.internal.ifPresent(s->s.hold(actual));data.external.ifPresent(s->s.hold(actual));
        if(actual.level().isClientSide())return;boolean stall=false;
        for(boolean leading:new boolean[]{true,false}){var id=getCoupledCart(leading);if(id==null)continue;var other=CapabilityMinecartController.getIfPresent(actual.level(),id);stall|=other==null||!other.isPresent()||other.data.external.isPresent()||other.ownsPackedReceipt();}
        setStalled(stall,true);double speed=MinecartSim2020.maxSpeed(actual);if(Double.compare(speed,knownMaxSpeed)!=0){knownMaxSpeed=speed;sendData();}
    }
    public void setStalledExternally(boolean stall){setStalled(stall,false);}
    private void setStalled(boolean stall,boolean internal){
        if(isEmpty())return;var old=internal?data.internal:data.external;if(old.isPresent()==stall)return;var next=stall?Optional.of(StallData.of(cart())):Optional.<StallData>empty();
        if(!stall&&(internal?data.external:data.internal).isEmpty())old.ifPresent(s->cart().setDeltaMovement(s.motion));
        data=new Data(internal?next:data.internal,internal?data.external:next,data.main,data.connected,data.packed,data.refunds,data.root,data.custody);sendData();
    }
    /** Pure commit building blocks; caller validates the complete real chain before the first mutation. */
    public void installLink(boolean leading,UUID peer,float length,boolean contraption,ItemStack paid){installLink(leading,peer,length,contraption,false,paid);}
    public void installLink(boolean leading,UUID peer,float length,boolean contraption,boolean creative,ItemStack paid){installLink(leading,peer,length,contraption,creative,false,paid);}
    /** Chain reversal retains proven original NeoForge topology without inventing paid/creative custody. */
    public void installLink(boolean leading,UUID peer,float length,boolean contraption,boolean creative,boolean legacyUnreceipted,ItemStack paid){
        requireServer();if(link(leading).isPresent()||leading&&!contraption&&!creative&&!legacyUnreceipted&&paid.isEmpty()||!leading&&!paid.isEmpty())throw new IllegalStateException("Actual single coupling receipt owner required");
        var value=new Link(leading?cart().getUUID():peer,leading?peer:cart().getUUID(),length,contraption,creative,paid,legacyUnreceipted);data=new Data(data.internal,data.external,leading?Optional.of(value):data.main,leading?data.connected:Optional.of(value),data.packed,data.refunds,data.root,data.custody);
    }
    public ItemStack detachLink(boolean leading){requireServer();var old=link(leading).orElseThrow();data=new Data(data.internal,data.external,leading?Optional.empty():data.main,leading?data.connected:Optional.empty(),data.packed,data.refunds,data.root,data.custody);return old.paid;}
    public void setMountedRoot(UUID actual){requireServer();data=new Data(data.internal,data.external,data.main,data.connected,data.packed,data.refunds,Optional.ofNullable(actual),data.custody);}
    /** Only after actual world restoration retired this mounted owner; no tracking callback here. */
    public void releaseMountedRootPure(UUID expectedRoot){
        requireServer();if(!data.root.equals(Optional.of(expectedRoot)))throw new IllegalStateException("Actual mounted root changed before retirement");
        if(data.internal.isEmpty())data.external.ifPresent(stall->cart().setDeltaMovement(stall.motion));
        data=new Data(data.internal,Optional.empty(),data.main,data.connected,data.packed,data.refunds,Optional.empty(),data.custody);
    }
    public void installPackedReceipt(ItemStack original){requireServer();if(nativeBaseIsInactive()||original.isEmpty()||original.getCount()!=1)throw new IllegalStateException("One actual packed item receipt required");data=new Data(data.internal,data.external,data.main,data.connected,original,data.refunds,Optional.empty(),Optional.empty());}
    /** Only the inspected native section-insertion commit may precede visible-index registration. */
    public void installPackedReceiptAtInsertion(com.simibubi.create.foundation.utility.NativeEntityAcceptance.Insertion proof,ItemStack original){
        proof.requireCommitting(cart());if(isEmpty())throw new IllegalStateException("Source EMPTY cannot own a packed receipt");if(nativeBaseIsInactive()||original.isEmpty()||original.getCount()!=1)throw new IllegalStateException("One actual packed item receipt required");data=new Data(data.internal,data.external,data.main,data.connected,original,data.refunds,Optional.empty(),Optional.empty());
    }
    public ItemStack detachPackedReceipt(){requireServer();var original=data.packed;data=new Data(data.internal,data.external,data.main,data.connected,ItemStack.EMPTY,data.refunds,data.root,data.custody);return original;}
    public void retainRefund(ItemStack original){requireServer();if(original.isEmpty()||original.getCount()!=1)throw new IllegalArgumentException("One original refund receipt required");var list=new ArrayList<>(data.refunds);list.add(original);data=new Data(data.internal,data.external,data.main,data.connected,data.packed,list,data.root,data.custody);}
    /** Read-only identity for preparing an actual endpoint; never publish or mutate this while still owned here. */
    public ItemStack actualFirstRefund(){return data.refunds.isEmpty()?ItemStack.EMPTY:data.refunds.getFirst();}
    public boolean ownsFirstRefund(ItemStack exact){return isPresent()&&!data.refunds.isEmpty()&&data.refunds.getFirst()==exact&&!exact.isEmpty()&&exact.getCount()==1;}
    public ItemStack detachRefund(){requireServer();if(data.refunds.isEmpty())return ItemStack.EMPTY;var list=new ArrayList<>(data.refunds);var original=list.removeFirst();data=new Data(data.internal,data.external,data.main,data.connected,data.packed,list,data.root,data.custody);return original;}
    public void reserveNativeBaseAtInsertion(com.simibubi.create.foundation.utility.NativeEntityAcceptance.Insertion proof,AbstractMinecart actualReceiptOwner,UUID receipt){
        proof.requireCommitting(cart());
        if(isEmpty())throw new IllegalStateException("Source EMPTY cannot reserve a native base");
        if(nativeBaseIsInactive()||data.root.isPresent()||actualReceiptOwner==cart()||actualReceiptOwner.level()!=cart().level())throw new IllegalStateException("Actual empty secondary base reservation required");
        var owner=MinecartController.get(actualReceiptOwner);
        if(!owner.ownsPackedReceipt(owner.data.packed)||!com.simibubi.create.content.contraptions.mounted.PackedCartData.hasReceipt(owner.data.packed,receipt))throw new IllegalStateException("Actual primary packed receipt is unavailable");
        data=new Data(data.internal,data.external,data.main,data.connected,data.packed,data.refunds,data.root,Optional.of(new BaseCustody(actualReceiptOwner.getUUID(),receipt,BaseCustodyKind.RESERVED)));
    }
    /** Final item-to-native-base commit only; all owners and original receipt must have passed preflight. */
    public void claimReservedNativeBasePure(UUID actualOwner,UUID receipt){
        requireServer();var reservation=data.custody.orElseThrow();
        if(reservation.kind!=BaseCustodyKind.RESERVED||!reservation.actualEntityOwner.equals(Optional.of(actualOwner))||!reservation.receipt.equals(receipt))throw new IllegalStateException("Actual paid native base reservation changed");
        data=new Data(data.internal,data.external,data.main,data.connected,data.packed,data.refunds,data.root,Optional.empty());
    }
    public ItemStack retirePackedToActualItem(com.simibubi.create.foundation.utility.NativeEntityAcceptance.Insertion proof,net.minecraft.world.entity.item.ItemEntity target){
        proof.requireCommitting(target);requirePackedSource();var original=data.packed;
        if(target.level()!=cart().level()||target.getItem()!=original)throw new IllegalStateException("Actual accepted target must retain the original packed receipt reference");
        var receipt=com.simibubi.create.content.contraptions.mounted.PackedCartData.receiptId(original);
        data=new Data(data.internal,data.external,data.main,data.connected,ItemStack.EMPTY,data.refunds,Optional.empty(),Optional.of(new BaseCustody(target.getUUID(),receipt,BaseCustodyKind.RETIRED)));return original;
    }
    /** Fully prepared source-side half of a canonical inventory/Deployer publication. */
    public final class PreparedPackedRetirement {
        private final Data before,next;
        private final ItemStack original,expected;
        private final java.util.function.BooleanSupplier receiverValid;
        private boolean committed;
        private PreparedPackedRetirement(BaseCustody destination,java.util.function.BooleanSupplier receiverValid){
            requirePackedSource();before=data;original=before.packed;expected=com.simibubi.create.content.contraptions.mounted.PackedCartData.detachedItemCopy(original);this.receiverValid=receiverValid;
            next=new Data(before.internal,before.external,before.main,before.connected,ItemStack.EMPTY,before.refunds,Optional.empty(),Optional.of(destination));
        }
        public ItemStack original(){return original;}
        public boolean validate(){return !committed&&data==before&&ownsPackedReceipt(original)&&ItemStack.matches(original,expected)&&receiverValid.getAsBoolean();}
        /** Receiver installation must follow immediately, without any callback or serialization. */
        public void commitPure(){if(!validate())throw new IllegalStateException("Actual original packed source or canonical destination changed");data=next;committed=true;}
        public boolean sourceWasRetired(){return committed;}
    }
    public PreparedPackedRetirement preparePackedRetirement(com.simibubi.create.content.contraptions.mounted.CartInventoryPublication.Plan sink){
        Objects.requireNonNull(sink);requirePackedSource();var receipt=com.simibubi.create.content.contraptions.mounted.PackedCartData.receiptId(data.packed);
        return new PreparedPackedRetirement(new BaseCustody(sink.actualPlayer().getUUID(),receipt,BaseCustodyKind.RETIRED),sink::validate);
    }
    public PreparedPackedRetirement preparePackedRetirement(com.simibubi.create.content.kinetics.deployer.DeployerCartPickupSink.Prepared sink){
        Objects.requireNonNull(sink);requirePackedSource();var receipt=com.simibubi.create.content.contraptions.mounted.PackedCartData.receiptId(data.packed);
        PickupDestination destination;
        if(sink.endpoint() instanceof com.simibubi.create.content.kinetics.deployer.DeployerCartPickupSink.BlockEndpoint block)destination=new PickupDestination(block.dimension(),block.pos(),Optional.empty());
        else if(sink.endpoint() instanceof com.simibubi.create.content.kinetics.deployer.DeployerCartPickupSink.ActorEndpoint actor)destination=new PickupDestination(actor.dimension(),actor.localPos(),Optional.of(actor.actualEntity()));
        else throw new IllegalArgumentException("Actual stationary BE or owned moving actor endpoint required");
        return new PreparedPackedRetirement(new BaseCustody(destination.actorOwner(),receipt,BaseCustodyKind.RETIRED,Optional.of(destination)),sink::validate);
    }
    /** Packing's secondary native base retires to an already installed actual primary receipt. */
    public void retireNativeBaseToPackedOwner(AbstractMinecart actualOwner,UUID receipt){
        requireServer();if(nativeBaseIsInactive())throw new IllegalStateException("Native base was already transferred");
        var owner=MinecartController.get(actualOwner);
        if(actualOwner.level()!=cart().level()||!owner.ownsPackedReceipt(owner.data.packed)||!com.simibubi.create.content.contraptions.mounted.PackedCartData.hasReceipt(owner.data.packed,receipt))throw new IllegalStateException("Actual complete packed owner is unavailable");
        data=new Data(data.internal,data.external,data.main,data.connected,data.packed,data.refunds,Optional.empty(),Optional.of(new BaseCustody(actualOwner.getUUID(),receipt,BaseCustodyKind.RETIRED)));
    }
    public void sendData(){var actual=cart();if(actual==null||actual.level().isClientSide())return;((AttachmentTarget)actual).setAttached(ATTACHMENT,this);MinecartControllerUpdatePacket.send(this);}
    public CompoundTag serializeNBT(HolderLookup.Provider registries){return (CompoundTag)CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),this).getOrThrow();}
    public void deserializeNBT(HolderLookup.Provider registries,CompoundTag tag){if(this==EMPTY)throw new IllegalStateException("Shared EMPTY sentinel cannot acquire mutable data");var decoded=CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();data=decoded.data;}
    /** Actual tracking data contains topology/pose only; paid cargo/receipts never enter the client controller. */
    public CompoundTag publicData(){var actual=cart();var tag=new CompoundTag();if(isEmpty())tag.putBoolean("LegacyEmpty",true);tag.putDouble("MaxSpeed",actual!=null&&actual.level() instanceof ServerLevel?MinecartSim2020.maxSpeed(actual):knownMaxSpeed);for(boolean leading:new boolean[]{true,false}){var link=link(leading);if(link.isEmpty())continue;var l=link.get();var value=new CompoundTag();value.store("Main",UUIDUtil.CODEC,l.main);value.store("Connected",UUIDUtil.CODEC,l.connected);value.putFloat("Length",l.length);value.putBoolean("Contraption",l.contraption);value.putBoolean("Creative",l.creative);tag.put(leading?"MainCoupling":"ConnectedCoupling",value);}data.internal.ifPresent(s->tag.store("InternalStallData",StallData.CODEC,s));data.external.ifPresent(s->tag.store("StallData",StallData.CODEC,s));return tag;}
    public static void rejectPrivateData(CompoundTag tag){
        if(tag.contains("PackedReceipt")||tag.contains("PendingRefunds")||tag.contains("NativeBaseCustody")||tag.contains("MountedRoot"))throw new IllegalArgumentException("Private native cart custody cannot enter a public snapshot");
        for(var key:List.of("MainCoupling","ConnectedCoupling"))if(tag.getCompoundOrEmpty(key).contains("PaidItem"))throw new IllegalArgumentException("Paid coupling receipts are not public topology");
    }
    public void applyPublicData(CompoundTag tag){
        if(cart()==null||!cart().level().isClientSide())throw new IllegalStateException("Public controller snapshots are client-only");rejectPrivateData(tag);
        var decoded=CODEC.parse(cart().registryAccess().createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();data=decoded.data;knownMaxSpeed=tag.getDoubleOr("MaxSpeed",0);if(!Double.isFinite(knownMaxSpeed)||knownMaxSpeed<0)throw new IllegalArgumentException("Invalid native cart speed snapshot");bind(cart());
    }
}
