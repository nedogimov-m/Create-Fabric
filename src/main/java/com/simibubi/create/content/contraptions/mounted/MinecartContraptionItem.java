package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.foundation.utility.NativeEntityAcceptance;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.CartForestTransfer;
import com.simibubi.create.content.contraptions.minecart.MinecartConfig;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/** The actual native cart is the first durable receiver, before forest decoding or actor callbacks. */
public final class MinecartContraptionItem extends Item {
    private final NativeCartKind kind;
    public static MinecartContraptionItem rideable(Properties properties){return new MinecartContraptionItem(NativeCartKind.RIDEABLE,properties);}
    public static MinecartContraptionItem chest(Properties properties){return new MinecartContraptionItem(NativeCartKind.CHEST,properties);}
    public static MinecartContraptionItem furnace(Properties properties){return new MinecartContraptionItem(NativeCartKind.FURNACE,properties);}
    private MinecartContraptionItem(NativeCartKind kind,Properties properties){
        super(properties);this.kind=kind;DispenserBlock.registerBehavior(this,this::dispense);
    }
    @Override public boolean canFitInsideContainerItems(){return MinecartConfig.insideContainers();}
    private static RailShape railShape(BlockState state){return state.getBlock() instanceof BaseRailBlock rail?state.getValue(rail.getShapeProperty()):RailShape.NORTH_SOUTH;}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();
        if(!LoadedLevelAccess.isLoaded(level,pos)||!LoadedLevelAccess.blockState(level,pos).is(BlockTags.RAILS))return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(level instanceof ServerLevel server)||context.getPlayer()==null)return InteractionResult.FAIL;
        var payment=CartItemPayment.prepare(context.getPlayer(),context.getHand()).orElse(null);
        if(payment==null||!payment.proposedReceipt().is(this))return InteractionResult.FAIL;
        double slope=railShape(LoadedLevelAccess.blockState(level,pos)).isSlope()?.5:0;
        try{return place(server,new Vec3(pos.getX()+.5,pos.getY()+.0625+slope,pos.getZ()+.5),context.getPlayer().getDirection(),payment)?InteractionResult.SUCCESS:InteractionResult.FAIL;}
        catch(AssemblyException refused){context.getPlayer().sendOverlayMessage(refused.component.copy().withStyle(net.minecraft.ChatFormatting.RED));return InteractionResult.SUCCESS;}
    }
    private boolean place(ServerLevel level,Vec3 position,Direction facing,CartItemPayment payment)throws AssemblyException{
        var offered=payment.proposedReceipt();var customName=offered.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);if(!offered.is(this)||!LoadedEntityAccess.isReady(level,BlockPos.containing(position)))return false;
        boolean packed=offered.has(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);
        if(packed){
            if(LegacyPackedCartPlan.isPlan(offered)){
                LegacyPackedCartPlan.parse(offered);if(LegacyPackedCartPlan.actualKind(offered)!=kind)return false;
            }else if(PackedCartForest.parse(offered).bases().getFirst().kind()!=kind)return false;
        }
        var cart=kind.createEmpty(level,position);var controller=MinecartController.get(cart);var empty=NativeCartSnapshot.capture(cart);
        // Native New behavior refuses overlapping cart placement, matching MinecartItem.useOn.
        if(AbstractMinecart.useExperimentalMovement(level)&&level.getEntities(null,cart.getBoundingBox()).stream().anyMatch(entity->entity instanceof AbstractMinecart))return false;
        try(var scope=NativeEntityAcceptance.prepare(cart,()->payment.validate()&&empty.matchesRaw()&&!controller.nativeBaseIsInactive()&&!controller.isLeadingCoupling()&&!controller.isConnectedToCoupling()&&controller.getMountedRoot()==null&&!controller.hasPendingRefunds(),proof->{
            proof.requireCommitting(cart);var original=payment.debit();
            if(packed)controller.installPackedReceiptAtInsertion(proof,original);else original.setCount(0);
        })){
            try{scope.add();}finally{if(scope.insertion()!=null)payment.notifyNative();}
            if(scope.insertion()==null)return false;
        }
        // Late native callbacks may already have consumed/destroyed the accepted receiver. That
        // never recreates the payer. A failed forest deployment retains its real primary receipt.
        if(cart.isRemoved()||level.getEntity(cart.getUUID())!=cart)return true;
        if(packed){if(controller.ownsPackedReceipt())CartForestTransfer.deploy(cart,facing);}
        else cart.setCustomName(customName);
        return true;
    }
    private ItemStack dispense(BlockSource source,ItemStack supplied){
        var payment=CartItemPayment.prepareDispenser(source,supplied).orElse(null);if(payment==null||!payment.proposedReceipt().is(this))return supplied;
        var direction=source.state().getValue(DispenserBlock.FACING);var level=source.level();var center=source.center();var front=source.pos().relative(direction);
        boolean onRail=false;double height=0;
        if(LoadedLevelAccess.isLoaded(level,front)){
            var state=LoadedLevelAccess.blockState(level,front);
            if(state.is(BlockTags.RAILS)){height=railShape(state).isSlope()?.6:.1;onRail=true;}
            else if(state.isAir()&&LoadedLevelAccess.isLoaded(level,front.below())){
                var below=LoadedLevelAccess.blockState(level,front.below());
                if(below.is(BlockTags.RAILS)){height=direction!=Direction.DOWN&&railShape(below).isSlope()?-.4:-.9;onRail=true;}
            }
        }
        try{
            if(onRail)place(level,new Vec3(center.x+direction.getStepX()*1.125,Math.floor(center.y)+direction.getStepY()+height,center.z+direction.getStepZ()*1.125),direction,payment);
            else drop(source,direction,payment);
        }catch(AssemblyException refused){/* The actual admitted primary still owns the complete item. */}
        level.levelEvent(1000,source.pos(),0);level.levelEvent(2000,source.pos(),direction.get3DDataValue());
        // Native dispenseFrom writes this return value back only after behavior callbacks. Read the
        // actual canonical remainder last, including changes made by native acceptance callbacks.
        return payment.currentRemainder();
    }
    public static InteractionResult pickup(net.minecraft.world.entity.player.Player player,net.minecraft.world.level.Level level,net.minecraft.world.InteractionHand hand,net.minecraft.world.entity.Entity selected){
        if(!player.getItemInHand(hand).is(com.simibubi.create.AllItems.WRENCH)||player.isSpectator()||!player.isAlive())return InteractionResult.PASS;
        if(!MinecartConfig.survivalPickup()&&!player.isCreative())return InteractionResult.PASS;
        if(selected instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity)selected=selected.getVehicle();
        if(!(selected instanceof AbstractMinecart cart)||!CartAssemblerBlock.canAssembleTo(cart)||!cart.isAlive())return InteractionResult.PASS;
        var controller=com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController.getIfPresent(level,cart.getUUID());
        if(controller==null||!controller.ownsPackedReceipt()&&controller.getMountedRoot()==null)return InteractionResult.PASS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(level instanceof ServerLevel server)||!player.isWithinEntityInteractionRange(cart,0)||!level.mayInteract(player,cart.blockPosition()))return InteractionResult.FAIL;
        boolean deployer=player instanceof net.fabricmc.fabric.api.entity.FakePlayer;
        if(deployer&&!com.simibubi.create.content.kinetics.deployer.DeployerCartItemSource.canPickup(player))return InteractionResult.FAIL;
        MinecartController source=controller;
        if(!controller.ownsPackedReceipt()){
            if(!(server.getEntity(controller.getMountedRoot()) instanceof com.simibubi.create.content.contraptions.OrientedContraptionEntity root)||!root.hasOwnedStructure()||!(server.getEntity(root.getMountedCartId()) instanceof AbstractMinecart primary))return InteractionResult.FAIL;
            try{
                var actualOutputOwner=deployer?com.simibubi.create.content.kinetics.deployer.DeployerCartItemSource.activeMovingOwner(player):null;
                int sourceBlockCount=root.getContraption().getBlocks().size();var packed=CartForestTransfer.pack(cart,actualOutputOwner);if(packed==null)return InteractionResult.FAIL;
                source=MinecartController.get(primary);CartAdvancements.pickedUp(player,sourceBlockCount);
                if(!primary.getUUID().equals(packed.actualSourceOwner()))throw new IllegalStateException("Packed source disagrees with the actual predeclared native primary");
                if(source.actualPackedReceipt()!=packed.originalReceipt())return InteractionResult.FAIL;
            }catch(AssemblyException refused){player.sendOverlayMessage(refused.component.copy().withStyle(net.minecraft.ChatFormatting.RED));return InteractionResult.FAIL;}
        }
        if(!source.ownsPackedReceipt(source.actualPackedReceipt()))return InteractionResult.FAIL;
        if(deployer){
            var sink=com.simibubi.create.content.kinetics.deployer.DeployerCartItemSource.preparePickup(player,source.actualPackedReceipt());
            return sink.isPresent()&&CartReceiptPublication.returnPackedReceipt(source,sink.get())?InteractionResult.SUCCESS:InteractionResult.FAIL;
        }
        return player instanceof net.minecraft.server.level.ServerPlayer actual&&CartReceiptPublication.returnPackedReceipt(source,actual)?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
    public static void register(){CartAdvancements.register();net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player,level,hand,entity,hit)->pickup(player,level,hand,entity));}
    private static boolean drop(BlockSource source,Direction direction,CartItemPayment payment){
        var position=DispenserBlock.getDispensePosition(source);double y=position.y()-(direction.getAxis()==Direction.Axis.Y?.125:.15625);
        var original=payment.proposedReceipt();var entity=new ItemEntity(source.level(),position.x(),y,position.z(),original);var random=source.level().getRandom();double speed=random.nextDouble()*.1+.2;
        entity.setDeltaMovement(random.triangle(direction.getStepX()*speed,.0172275*6),random.triangle(.2,.0172275*6),random.triangle(direction.getStepZ()*speed,.0172275*6));
        if(!LoadedEntityAccess.isReady(source.level(),entity.blockPosition()))return false;
        try(var scope=NativeEntityAcceptance.prepare(entity,()->payment.validate()&&entity.getItem()==original&&!entity.isRemoved(),proof->{proof.requireCommitting(entity);if(payment.debit()!=original)throw new IllegalStateException("Actual dispenser receipt changed");})){
            try{scope.add();return scope.insertion()!=null;}finally{if(scope.insertion()!=null)payment.notifyNative();}
        }
    }
}
