package com.simibubi.create.content.equipment.symmetryWand;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.*;

/** Real inventory material transfers at the actual native section write, before native callbacks. */
public final class SymmetryPlacement {
    private static final ThreadLocal<Payment> ACTIVE=new ThreadLocal<>();
    private static final ThreadLocal<Break> BREAK=new ThreadLocal<>();
    private static final class Break {final ServerPlayer player;final BlockPos target;boolean claimed;Break(ServerPlayer player,BlockPos target){this.player=player;this.target=target;}}
    private static final ThreadLocal<BreakCapture> BREAK_CAPTURE=new ThreadLocal<>();
    public static final class BreakCapture implements AutoCloseable {
        private final BreakCapture previous;private final ServerPlayer player;private final BlockPos target;private final boolean mirrored;
        private BreakCapture(ServerPlayer player,BlockPos target){this.player=player;this.target=target.immutable();previous=BREAK_CAPTURE.get();var request=BREAK.get();mirrored=request!=null&&!request.claimed&&request.player==player&&request.target.equals(target);if(mirrored)request.claimed=true;BREAK_CAPTURE.set(this);}
        @Override public void close(){if(previous==null)BREAK_CAPTURE.remove();else BREAK_CAPTURE.set(previous);}
    }
    public static BreakCapture captureBreak(ServerPlayer player,BlockPos target){return new BreakCapture(player,target);}
    private static final class Payment {
        final ServerPlayer player;final Level level;final boolean creative;final BlockPos target;final BlockState desired;final BlockState before;final ItemStack paid;final List<ItemStack> originals,witnesses;final List<ItemStack> owners;BlockPlaceContext context;boolean accepted,placing;
        Payment(ServerPlayer player,BlockPos target,BlockState desired,List<ItemStack> originals){this.player=player;this.level=player.level();this.creative=player.isCreative();this.target=target;this.desired=desired;this.before=LoadedLevelAccess.blockState(level,target);this.originals=List.copyOf(originals);this.witnesses=originals.stream().map(ItemStack::copy).toList();this.paid=originals.getFirst();this.owners=SymmetryHandler.currentOwners();}
        boolean current(){for(int i=0;i<originals.size();i++)if(!ItemStack.matches(originals.get(i),witnesses.get(i)))return false;return true;}
    }
    public static boolean internal(BlockPlaceContext context){var payment=ACTIVE.get();return payment!=null&&payment.context==context;}
    public static List<ItemStack> placementOwners(BlockPlaceContext context){var payment=ACTIVE.get();return payment!=null&&payment.context==context?payment.owners:null;}
    public static boolean internalBreak(ServerPlayer player,BlockPos pos){var scope=BREAK_CAPTURE.get();return scope!=null&&scope.mirrored&&scope.player==player&&scope.target.equals(pos);}
    public static BlockState placementState(BlockPlaceContext context,BlockState original){
        var payment=ACTIVE.get();if(payment==null||context.getItemInHand()!=payment.paid)return original;
        if(payment.context!=context)return null;
        if(original==null||!context.getClickedPos().equals(payment.target))return null;
        return payment.desired;
    }
    public static boolean allowNative(BlockPlaceContext context,BlockState state){
        var payment=ACTIVE.get();return payment==null||context.getItemInHand()!=payment.paid||payment.context==context&&state==payment.desired&&context.getClickedPos().equals(payment.target)&&context.getLevel()==payment.level&&payment.player.level()==payment.level&&LoadedLevelAccess.blockState(payment.level,payment.target)==payment.before&&targetReady(payment.player,payment.target)&&payment.current();
    }
    public static boolean nativePlace(BlockPlaceContext context,BlockState state,java.util.function.BooleanSupplier nativeCall){
        if(!allowNative(context,state))return false;var payment=ACTIVE.get();boolean owns=payment!=null&&payment.context==context;
        if(owns)payment.placing=true;try{return nativeCall.getAsBoolean();}finally{if(owns)payment.placing=false;}
    }
    /** Called only by the real LevelChunkSection.setBlockState invocation, before onPlace. */
    public static void published(LevelChunk chunk,BlockPos pos,BlockState state,BlockState previous){
        var payment=ACTIVE.get();if(payment==null||!payment.placing||payment.accepted||chunk.getLevel()!=payment.level||!pos.equals(payment.target)||state!=payment.desired||previous==state)return;
        payment.accepted=true;
        // Every detached actual original is consumed exactly here. Different native component
        // variants are never grown into one another; native tail consume cannot double-debit.
        // State-only symmetry deliberately does not install paid/source item components in a BE.
        for(var original:payment.originals)original.setCount(0);
    }
    private static boolean loadedNeighborhood(Level level,BlockPos pos){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!LoadedLevelAccess.isLoaded(level,pos.offset(x,0,z)))return false;return true;}
    private static boolean targetReady(ServerPlayer player,BlockPos pos){
        var level=player.level();if(!SymmetryWandNetworking.actual(player)||!level.isInValidBounds(pos)||!loadedNeighborhood(level,pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.mayInteract(player,pos))return false;
        var state=LoadedLevelAccess.blockState(level,pos);return state.canBeReplaced()&&!state.is(BlockTags.PORTALS)&&!state.hasBlockEntity()&&LoadedLevelAccess.blockEntity(level,pos)==null&&state.getDestroySpeed(level,pos)>=0;
    }
    private static boolean containsStock(ContainerComponent<?> contents){return contents!=null&&contents.itemCopies().anyMatch(stack->!stack.isEmpty());}
    private static boolean simple(ItemStack stack,Item item){
        // Source material matching is by item identity, including non-presentation components.
        // Only real registered cargo/owner payloads need a separate transfer protocol.
        if(stack.isEmpty()||!stack.is(item)||stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
            ||stack.has(net.minecraft.core.component.DataComponents.ENTITY_DATA)||stack.has(net.minecraft.core.component.DataComponents.CONTAINER_LOOT)
            ||stack.has(AllDataComponents.MINECRAFT_CONTRAPTION_DATA)||stack.has(AllDataComponents.TOOLBOX_UUID)
            ||stack.getOrDefault(AllDataComponents.BACKTANK_AIR,0)>0)return false;
        if(containsStock(stack.get(net.minecraft.core.component.DataComponents.CONTAINER))
            ||containsStock(stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS))
            ||containsStock(stack.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES))
            ||containsStock(stack.get(AllDataComponents.PACKAGE_CONTENTS)))return false;
        var bees=stack.get(net.minecraft.core.component.DataComponents.BEES);if(bees!=null&&!bees.bees().isEmpty())return false;
        var inert=stack.get(AllDataComponents.INERT_HIVE_SLOTS);if(inert!=null&&!inert.isEmpty())return false;
        var toolbox=stack.get(AllDataComponents.TOOLBOX_INVENTORY);if(toolbox!=null&&!toolbox.isEmpty())return false;
        var polishing=stack.get(AllDataComponents.SAND_PAPER_POLISHING);return polishing==null||polishing.isEmpty();
    }
    private static boolean defaultPlacement(BlockItem item){
        try{
            if(item.getClass().getMethod("updatePlacementContext",BlockPlaceContext.class).getDeclaringClass()!=BlockItem.class)return false;
            var owner=item.getClass().getMethod("place",BlockPlaceContext.class).getDeclaringClass();
            if(owner!=BlockItem.class&&owner!=com.simibubi.create.content.fluids.tank.FluidTankItem.class&&owner!=com.simibubi.create.content.logistics.vault.ItemVaultItem.class)return false;
            for(var name:List.of("getPlacementState","placeBlock")){
                Class<?> declared=null;for(Class<?> type=item.getClass();type!=null;type=type.getSuperclass())try{declared=(name.equals("placeBlock")?type.getDeclaredMethod(name,BlockPlaceContext.class,BlockState.class):type.getDeclaredMethod(name,BlockPlaceContext.class)).getDeclaringClass();break;}catch(NoSuchMethodException ignored){}
                if(declared!=BlockItem.class)return false;
            }
            return true;
        }catch(NoSuchMethodException missing){return false;}
    }
    private static int materialCount(BlockState state){
        int sourceCount=1;
        if(state.hasProperty(BlockStateProperties.SLAB_TYPE)&&state.getValue(BlockStateProperties.SLAB_TYPE)==net.minecraft.world.level.block.state.properties.SlabType.DOUBLE)sourceCount*=2;
        for(var property:List.of(BlockStateProperties.EGGS,BlockStateProperties.PICKLES,BlockStateProperties.CANDLES))if(state.hasProperty(property))sourceCount*=state.getValue(property);
        if(state.is(Blocks.VINE)||state.is(Blocks.GLOW_LICHEN)){int faces=0;for(var property:List.of(BlockStateProperties.UP,BlockStateProperties.NORTH,BlockStateProperties.EAST,BlockStateProperties.SOUTH,BlockStateProperties.WEST,BlockStateProperties.DOWN))if(state.hasProperty(property)&&state.getValue(property))faces++;sourceCount+=faces-1;}
        return sourceCount;
    }
    private record Material(Item item,int amount){}
    private static Item sourceItem(BlockState state){return state.is(Blocks.FARMLAND)||state.is(Blocks.DIRT_PATH)?Items.DIRT:state.getBlock().asItem();}
    private static List<Integer> slots(ServerPlayer player){var slots=new ArrayList<Integer>();int selected=player.getInventory().getSelectedSlot();slots.add(selected);for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(slot!=selected)slots.add(slot);return slots;}
    private static long available(ServerPlayer player,Item item){long count=0;for(int slot:slots(player)){var stack=player.getInventory().getItem(slot);if(simple(stack,item))count+=stack.getCount();}return count;}
    private static List<ItemStack> reserve(ServerPlayer player,List<Material> materials){
        var result=new ArrayList<ItemStack>();
        for(var material:materials)if(material.amount<1)return List.of();
        if(player.isCreative()){for(var material:materials)result.add(new ItemStack(material.item,material.amount));return result;}
        for(var material:materials)if(material.amount<1||available(player,material.item)<material.amount)return List.of();
        for(var material:materials){int remaining=material.amount;for(int slot:slots(player)){var stock=player.getInventory().getItem(slot);if(!simple(stock,material.item))continue;var actual=stock.split(Math.min(remaining,stock.getCount()));result.add(actual);remaining-=actual.getCount();if(remaining==0)break;}}
        player.getInventory().setChanged();return result;
    }
    private static boolean directPlace(Payment payment){
        if(!payment.current()||payment.player.level()!=payment.level||LoadedLevelAccess.blockState(payment.level,payment.target)!=payment.before||!targetReady(payment.player,payment.target))return false;
        payment.placing=true;try{payment.level.setBlockAndUpdate(payment.target,payment.desired);}finally{payment.placing=false;}
        if(payment.accepted)SymmetryHandler.completedStatePlacement(payment.player,payment.target,payment.desired,payment.owners);
        return payment.accepted;
    }
    public static boolean place(ServerPlayer player,BlockPos pos,BlockState desired){
        if(ACTIVE.get()!=null||!targetReady(player,pos)||desired.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)||desired.hasProperty(BlockStateProperties.BED_PART))return false;
        // Pinned apply uses the block-item mapping, not the unrelated schematic recipe API.
        if(!(Item.BY_BLOCK.get(desired.getBlock()) instanceof BlockItem))return false;
        var level=player.level();
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        // Fabric protections observe the real player, actual hand and real target.
        if(com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.protection(player,level,InteractionHand.MAIN_HAND,hit)!=InteractionResult.PASS||!targetReady(player,pos))return false;
        desired=Block.updateFromNeighbourShapes(desired,level,pos);if(desired.isAir()||!(Item.BY_BLOCK.get(desired.getBlock()) instanceof BlockItem)||!level.isUnobstructed(desired,pos,net.minecraft.world.phys.shapes.CollisionContext.placementContext(player)))return false;
        var materials=new ArrayList<Material>();boolean assembler=desired.getBlock() instanceof CartAssemblerBlock;
        if(assembler){var rail=CartAssemblerBlock.getRailBlock(desired);var railItem=sourceItem(rail);if(!player.isCreative()&&available(player,railItem)<1)return false;materials.add(new Material(railItem,1));if(player.isCreative()||available(player,sourceItem(desired))>=1)materials.add(new Material(sourceItem(desired),1));else desired=rail;}
        else materials.add(new Material(sourceItem(desired),materialCount(desired)));
        if(!assembler&&!desired.canSurvive(level,pos)||!targetReady(player,pos))return false;
        var originals=reserve(player,materials);if(originals.isEmpty())return false;
        var payment=new Payment(player,pos.immutable(),desired,originals);ACTIVE.set(payment);
        Throwable primary=null;
        try{
            // Ordinary native callbacks stay intact. Custom core placement algorithms are
            // not mirror eligibility guards: the source directly publishes reflected state.
            var item=sourceItem(desired);if(assembler||!(item instanceof BlockItem blockItem)||!defaultPlacement(blockItem))return directPlace(payment);
            var context=new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,payment.paid,hit);payment.context=context;blockItem.place(context);return payment.accepted;
        }
        catch(RuntimeException|Error failure){primary=failure;throw failure;}
        finally{
            ACTIVE.remove();
            // Final AIR or a replacement is not rejection evidence: onPlace may have
            // published drops or another owner. Refund only a never-published payment.
            if(!payment.creative&&!payment.accepted){
                Throwable cleanupFailure=null;
                for(var original:payment.originals)if(!original.isEmpty())try{player.getInventory().placeItemBackInInventory(original,net.minecraft.util.Prediction.SERVER_ONLY);}
                catch(RuntimeException|Error cleanup){if(primary!=null&&primary!=cleanup)primary.addSuppressed(cleanup);else if(cleanupFailure==null)cleanupFailure=cleanup;else if(cleanupFailure!=cleanup)cleanupFailure.addSuppressed(cleanup);}
                if(primary==null&&cleanupFailure instanceof RuntimeException failure)throw failure;
                if(primary==null&&cleanupFailure instanceof Error failure)throw failure;
            }
        }
    }
    public static boolean destroy(ServerPlayer player,BlockPos target,BlockState original){
        var level=player.level();if(!SymmetryWandNetworking.actual(player)||!level.isInValidBounds(target)||!loadedNeighborhood(level,target)||!level.mayInteract(player,target))return false;
        var state=LoadedLevelAccess.blockState(level,target);if(state.isAir()||state.is(BlockTags.PORTALS)||!player.isCreative()&&state.getBlock()!=original.getBlock())return false;
        var previous=BREAK.get();BREAK.set(new Break(player,target.immutable()));try{return player.gameMode.destroyBlock(target);}finally{if(previous==null)BREAK.remove();else BREAK.set(previous);}
    }
    private SymmetryPlacement(){}
}
