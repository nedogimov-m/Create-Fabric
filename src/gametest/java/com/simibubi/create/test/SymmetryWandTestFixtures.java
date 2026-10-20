package com.simibubi.create.test;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.*;

/** Real registered blocks execute the actual native onPlace/setPlacedBy callbacks. */
public final class SymmetryWandTestFixtures implements ModInitializer {
    public static Block THROW_ON_PLACE,REPLACE_ON_PLACE,DROP_ON_PLACE,THROW_SET_PLACED,REFUSE_STATE,REPLACE_BEFORE_STATE,NESTED_PAID_BEFORE_STATE,REFUSE_CONTEXT,REENTRANT_PLACE,REENTRANT_BREAK,THROW_NEIGHBOR;
    public static Item USE_FINAL_STATE,USE_PASS_AFTER_WRITE,USE_THROW_AFTER_WRITE,USE_RESULT_TRANSFORM,USE_SUCCESS_NO_WRITE,USE_FINAL_AIR,USE_NESTED,USE_BUCKET_EXCLUDED;
    public static final class Refusal extends Error{public Refusal(String message){super(message);}}
    private record Target(Level level,BlockPos pos){}
    private record Action(ServerPlayer player,Refusal failure,int cargoSlot){}
    private record NativeAction(ServerPlayer player,BlockPos target,int stockSlot){}
    private static final Map<Target,Action> ARMED=new HashMap<>();
    private static final Map<Target,NativeAction> PLACE_CALLBACKS=new HashMap<>(),BREAK_CALLBACKS=new HashMap<>();
    private static final Map<Target,ItemStack> USE_RESULTS=new HashMap<>();
    private static final Map<Target,NativeAction> ITEM_USE_CALLBACKS=new HashMap<>();
    private static final Set<Target> DENY_PLACE=new HashSet<>(),DENY_BREAK=new HashSet<>();
    private static final Map<Target,Integer> BREAK_VISITS=new HashMap<>(),PLACE_VISITS=new HashMap<>();
    public static void arm(ServerPlayer player,BlockPos pos,Refusal failure,int cargoSlot){ARMED.put(new Target(player.level(),pos.immutable()),new Action(player,failure,cargoSlot));}
    public static void denyPlace(Level level,BlockPos pos){DENY_PLACE.add(new Target(level,pos.immutable()));}
    public static void denyBreak(Level level,BlockPos pos){DENY_BREAK.add(new Target(level,pos.immutable()));}
    public static void nativePlaceFromCallback(ServerPlayer player,BlockPos callback,BlockPos target,int stockSlot){PLACE_CALLBACKS.put(new Target(player.level(),callback.immutable()),new NativeAction(player,target.immutable(),stockSlot));}
    public static void nativeBreakFromCallback(ServerPlayer player,BlockPos callback,BlockPos target){BREAK_CALLBACKS.put(new Target(player.level(),callback.immutable()),new NativeAction(player,target.immutable(),-1));}
    public static void watchPlace(Level level,BlockPos pos){PLACE_VISITS.put(new Target(level,pos.immutable()),0);}
    public static int nativePlaceVisits(Level level,BlockPos pos){return PLACE_VISITS.getOrDefault(new Target(level,pos),0);}
    public static void watchBreak(Level level,BlockPos pos){BREAK_VISITS.put(new Target(level,pos.immutable()),0);}
    public static int nativeBreakVisits(Level level,BlockPos pos){return BREAK_VISITS.getOrDefault(new Target(level,pos),0);}
    public static void returnActualUseResult(Level level,BlockPos pos,ItemStack actual){USE_RESULTS.put(new Target(level,pos.immutable()),actual);}
    public static void nativeItemUseFromCallback(ServerPlayer player,BlockPos source,BlockPos target,int stockSlot){ITEM_USE_CALLBACKS.put(new Target(player.level(),source.immutable()),new NativeAction(player,target.immutable(),stockSlot));}
    public static void clear(){USE_RESULTS.clear();ITEM_USE_CALLBACKS.clear();ARMED.clear();PLACE_CALLBACKS.clear();BREAK_CALLBACKS.clear();DENY_PLACE.clear();DENY_BREAK.clear();BREAK_VISITS.clear();PLACE_VISITS.clear();}
    private static Block register(String name,int kind){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test",name));var block=Registry.register(BuiltInRegistries.BLOCK,key,new Block(kind==7?BlockBehaviour.Properties.of().setId(key).strength(1).replaceable():BlockBehaviour.Properties.of().setId(key).strength(1)){
            @Override protected boolean canBeReplaced(BlockState state,net.minecraft.world.item.context.BlockPlaceContext context){var action=kind==7?ARMED.remove(new Target(context.getLevel(),context.getClickedPos())):null;if(action!=null)throw action.failure;return super.canBeReplaced(state,context);}
            @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){
                var action=kind>=4?ARMED.remove(new Target(context.getLevel(),context.getClickedPos())):null;
                if(action!=null){
                    var player=action.player;var level=context.getLevel();var pos=context.getClickedPos();
                    if(kind==4){var remainder=player.getInventory().removeItemNoUpdate(0);player.getInventory().setItem(6,remainder);player.getInventory().setItem(0,player.getInventory().removeItemNoUpdate(action.cargoSlot));throw action.failure;}
                    if(kind==5){level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());var barrel=(BarrelBlockEntity)level.getBlockEntity(pos);barrel.setItem(0,player.getInventory().removeItemNoUpdate(action.cargoSlot));return super.getStateForPlacement(context);}
                    if(kind==6){var stock=player.getInventory().getItem(action.cargoSlot);var nested=new net.minecraft.world.item.context.BlockPlaceContext(level,player,net.minecraft.world.InteractionHand.MAIN_HAND,stock,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));((BlockItem)stock.getItem()).place(nested);throw action.failure;}
                }
                return super.getStateForPlacement(context);
            }
            @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){
                super.onPlace(state,level,pos,old,moved);
                if(kind==8){var nested=PLACE_CALLBACKS.remove(new Target(level,pos));if(nested!=null){var stock=nested.player.getInventory().getItem(nested.stockSlot);var context=new net.minecraft.world.item.context.BlockPlaceContext(level,nested.player,net.minecraft.world.InteractionHand.MAIN_HAND,stock,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(nested.target),Direction.UP,nested.target,false));((BlockItem)stock.getItem()).place(context);}return;}
                if(kind>=3)return;var action=ARMED.remove(new Target(level,pos));if(action==null)return;
                if(kind==1){level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());var barrel=(BarrelBlockEntity)level.getBlockEntity(pos);var cargo=action.player.getInventory().removeItemNoUpdate(action.cargoSlot);barrel.setItem(0,cargo);barrel.setChanged();}
                if(kind==2)level.destroyBlock(pos,true);
                throw action.failure;
            }
            @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(kind==3){var action=ARMED.remove(new Target(level,pos));if(action!=null)throw action.failure;}}
            @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player){var result=super.playerWillDestroy(level,pos,state,player);if(kind==9){var nested=BREAK_CALLBACKS.remove(new Target(level,pos));if(nested!=null)nested.player.gameMode.destroyBlock(nested.target);}return result;}
            @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,net.minecraft.world.level.redstone.Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(kind==10){var action=ARMED.remove(new Target(level,pos));if(action!=null)throw action.failure;}}
        });var item=ResourceKey.create(Registries.ITEM,key.identifier());Registry.register(BuiltInRegistries.ITEM,item,new BlockItem(block,new Item.Properties().setId(item)));return block;
    }
    private static Item registerUse(String name,int kind){
        var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("create_test",name));
        return Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key)){
            @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
                var level=context.getLevel();var pos=context.getClickedPos();var player=context.getPlayer();if(level.isClientSide()||!(player instanceof ServerPlayer actual))return InteractionResult.PASS;
                if(kind==6){var nested=ITEM_USE_CALLBACKS.remove(new Target(level,pos));if(nested==null)throw new IllegalStateException("Missing actual nested item-use fixture owner");int selected=actual.getInventory().getSelectedSlot();actual.getInventory().setSelectedSlot(nested.stockSlot);try{var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(nested.target),Direction.UP,nested.target,false);if(!actual.gameMode.useItemOn(actual,level,actual.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,hit).consumesAction())throw new IllegalStateException("Genuine nested native item-use fixture refused");}finally{actual.getInventory().setSelectedSlot(selected);}}
                if(kind!=4){level.setBlockAndUpdate(pos,kind==3?Blocks.GOLD_BLOCK.defaultBlockState():Blocks.STONE.defaultBlockState());if(kind==0)level.setBlockAndUpdate(pos,Blocks.GOLD_BLOCK.defaultBlockState());if(kind==5)level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}
                if(kind==1)return InteractionResult.PASS;
                if(kind==3){var returned=USE_RESULTS.remove(new Target(level,pos));if(returned==null)throw new IllegalStateException("Missing actual native transformed result owner");return InteractionResult.SUCCESS.heldItemTransformedTo(returned);}
                if(!actual.isCreative())context.getItemInHand().shrink(1);
                if(kind==2){var action=ARMED.remove(new Target(level,pos));if(action==null)throw new IllegalStateException("Missing actual native item-use refusal");throw action.failure;}
                return InteractionResult.SUCCESS;
            }
        });
    }
    private static Item registerExcludedBucket(){
        var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("create_test","symmetry_excluded_bucket_use"));
        return Registry.register(BuiltInRegistries.ITEM,key,new BucketItem(net.minecraft.world.level.material.Fluids.WATER,new Item.Properties().setId(key)){
            @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){if(context.getLevel().isClientSide())return InteractionResult.PASS;context.getLevel().setBlockAndUpdate(context.getClickedPos(),Blocks.STONE.defaultBlockState());if(context.getPlayer()==null||!context.getPlayer().isCreative())context.getItemInHand().shrink(1);return InteractionResult.SUCCESS;}
        });
    }
    @Override public void onInitialize(){THROW_ON_PLACE=register("symmetry_throw_on_place",0);REPLACE_ON_PLACE=register("symmetry_replace_on_place",1);DROP_ON_PLACE=register("symmetry_drop_on_place",2);THROW_SET_PLACED=register("symmetry_throw_set_placed",3);REFUSE_STATE=register("symmetry_refuse_state",4);REPLACE_BEFORE_STATE=register("symmetry_replace_before_state",5);NESTED_PAID_BEFORE_STATE=register("symmetry_nested_before_state",6);REFUSE_CONTEXT=register("symmetry_refuse_context",7);REENTRANT_PLACE=register("symmetry_reentrant_place",8);REENTRANT_BREAK=register("symmetry_reentrant_break",9);THROW_NEIGHBOR=register("symmetry_throw_neighbor",10);
        USE_FINAL_STATE=registerUse("symmetry_use_final_state",0);USE_PASS_AFTER_WRITE=registerUse("symmetry_use_pass_after_write",1);USE_THROW_AFTER_WRITE=registerUse("symmetry_use_throw_after_write",2);USE_RESULT_TRANSFORM=registerUse("symmetry_use_result_transform",3);USE_SUCCESS_NO_WRITE=registerUse("symmetry_use_success_no_write",4);USE_FINAL_AIR=registerUse("symmetry_use_final_air",5);USE_NESTED=registerUse("symmetry_use_nested",6);USE_BUCKET_EXCLUDED=registerExcludedBucket();
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{var target=new Target(level,hit.getBlockPos());if(PLACE_VISITS.containsKey(target))PLACE_VISITS.computeIfPresent(target,(key,visits)->visits+1);return DENY_PLACE.contains(target)?InteractionResult.FAIL:InteractionResult.PASS;});
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,entity)->{var target=new Target(level,pos);if(BREAK_VISITS.containsKey(target))BREAK_VISITS.computeIfPresent(target,(key,visits)->visits+1);return !DENY_BREAK.contains(target);});
    }
}
