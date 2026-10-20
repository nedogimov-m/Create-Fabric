package com.simibubi.create.test;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/** Test-only callbacks on a real registered block after the actual native chunk accepted its state. */
public final class GameplayPrintTestFixtures implements ModInitializer {
    public static Block CALLBACK,NBT_CALLBACK;
    public static net.minecraft.world.level.block.entity.BlockEntityType<CallbackNbt> NBT_TYPE;
    private static final Map<Target,Runnable> AFTER_PERMISSION=new HashMap<>();
    private static final Map<Target,java.util.function.Consumer<CallbackNbt>> RECEIVER_PERMISSION=new HashMap<>();
    private static final ThreadLocal<Runnable> SAFE_WRITE=new ThreadLocal<>(),PAYMENT_COMMIT=new ThreadLocal<>();
    public static AutoCloseable afterPermissionBeforeSafeWrite(Level level,BlockPos pos,Runnable callback){var key=new Target(level,pos.immutable());if(AFTER_PERMISSION.putIfAbsent(key,callback)!=null)throw new IllegalStateException("Already armed native safe-NBT boundary");return ()->{AFTER_PERMISSION.remove(key);SAFE_WRITE.remove();};}
    public static AutoCloseable onReceiverPermission(Level level,BlockPos pos,java.util.function.Consumer<CallbackNbt> callback){var key=new Target(level,pos.immutable());if(RECEIVER_PERMISSION.putIfAbsent(key,callback)!=null)throw new IllegalStateException("Already armed actual receiver permission");return ()->RECEIVER_PERMISSION.remove(key);}
    public static AutoCloseable afterPaymentCommit(Runnable callback){if(PAYMENT_COMMIT.get()!=null)throw new IllegalStateException("Nested actual payment callback");PAYMENT_COMMIT.set(callback);return ()->PAYMENT_COMMIT.remove();}
    public static final class CallbackNbt extends net.minecraft.world.level.block.entity.BlockEntity {
        public int value;
        public CallbackNbt(BlockPos pos,BlockState state){super(NBT_TYPE,pos,state);}
        @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output){super.saveAdditional(output);output.putInt("Value",value);}
        @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input){super.loadAdditional(input);value=input.getIntOr("Value",0);}
    }
    private static final class CallbackNbtBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
        CallbackNbtBlock(Properties properties){super(properties);}
        @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CallbackNbt(pos,state);}
    }
    private static com.simibubi.create.content.schematics.requirement.ItemRequirement paymentRequirement(Block block){
        var requirement=new com.simibubi.create.content.schematics.requirement.ItemRequirement.StackRequirement(new ItemStack(block),com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME){
            @Override public boolean matches(ItemStack actual){
                boolean matches=super.matches(actual);var callback=PAYMENT_COMMIT.get();
                if(matches&&callback!=null&&net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.isOpen()){
                    var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getCurrentUnsafe();
                    if(transaction!=null){PAYMENT_COMMIT.remove();transaction.addOuterCloseCallback(result->{if(result.wasCommitted())callback.run();});}
                }
                return matches;
            }
        };
        return new com.simibubi.create.content.schematics.requirement.ItemRequirement(requirement);
    }
    private record Target(Level level,BlockPos pos){}
    private static final Map<Target,Runnable> CALLBACKS=new HashMap<>();
    private static final ThreadLocal<Runnable> BEFORE=new ThreadLocal<>();
    public static AutoCloseable beforePlace(Runnable callback){if(BEFORE.get()!=null)throw new IllegalStateException("Nested native print pre-publication callback");BEFORE.set(callback);return ()->BEFORE.remove();}
    private static final class CallbackBlock extends Block implements com.simibubi.create.api.schematic.state.SchematicStateFilter {
        CallbackBlock(Properties properties){super(properties);}
        @Override public BlockState filterStates(net.minecraft.world.level.block.entity.BlockEntity existing,BlockState state){var callback=BEFORE.get();if(callback!=null){BEFORE.remove();callback.run();}return state;}
        @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved){super.onPlace(state,level,pos,previous,moved);var callback=CALLBACKS.remove(new Target(level,pos));if(callback!=null)callback.run();}
    }
    public static AutoCloseable arm(Level level,BlockPos target,Runnable callback){var key=new Target(level,target.immutable());if(CALLBACKS.putIfAbsent(key,callback)!=null)throw new IllegalStateException("Already armed actual print callback");return ()->CALLBACKS.remove(key);}
    @Override public void onInitialize(){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test","gameplay_print_callback"));
        CALLBACK=Registry.register(BuiltInRegistries.BLOCK,key,new CallbackBlock(BlockBehaviour.Properties.of().setId(key)));
        var item=ResourceKey.create(Registries.ITEM,key.identifier());Registry.register(BuiltInRegistries.ITEM,item,new BlockItem(CALLBACK,new Item.Properties().setId(item)));
        var nbtKey=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test","gameplay_print_nbt_callback"));NBT_CALLBACK=Registry.register(BuiltInRegistries.BLOCK,nbtKey,new CallbackNbtBlock(BlockBehaviour.Properties.of().setId(nbtKey)));var nbtItem=ResourceKey.create(Registries.ITEM,nbtKey.identifier());Registry.register(BuiltInRegistries.ITEM,nbtItem,new BlockItem(NBT_CALLBACK,new Item.Properties().setId(nbtItem)));NBT_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,nbtKey.identifier(),net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(CallbackNbt::new,NBT_CALLBACK).build());
        com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry.REGISTRY.register(NBT_TYPE,(entity,tag,registries)->{tag.putInt("Value",((CallbackNbt)entity).value);var callback=SAFE_WRITE.get();if(callback!=null){SAFE_WRITE.remove();callback.run();}});
        com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries.BLOCKS.register(NBT_CALLBACK,(state,entity)->paymentRequirement(NBT_CALLBACK));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!(player instanceof com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer))return net.minecraft.world.InteractionResult.PASS;
            var target=new Target(level,hit.getBlockPos());var safe=AFTER_PERMISSION.remove(target);if(safe!=null)SAFE_WRITE.set(safe);
            if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,hit.getBlockPos()) instanceof CallbackNbt receiver){var callback=RECEIVER_PERMISSION.remove(target);if(callback!=null)callback.accept(receiver);}
            return net.minecraft.world.InteractionResult.PASS;
        });

    }
}
