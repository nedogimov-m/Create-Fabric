package com.simibubi.create.content.processing.burner;

import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import java.util.Map;

public final class BlazeBurnerBlockItem extends BlockItem {
    private static final TagKey<EntityType<?>> CAPTURABLE = TagKey.create(Registries.ENTITY_TYPE, Create.asResource("blaze_burner_capturable"));
    private final boolean captured;
    public BlazeBurnerBlockItem(Block block, Properties properties, boolean captured) { super(block, properties); this.captured = captured; }
    public boolean hasCapturedBlaze() { return captured; }
    @Override public void registerBlocks(Map<Block, Item> blocks, Item item) { if (captured) super.registerBlocks(blocks, item); }
    private boolean capturable(CompoundTag spawnData) {
        var entity = spawnData.getCompoundOrEmpty("entity");
        var id = net.minecraft.resources.Identifier.tryParse(entity.getStringOr("id", ""));
        return id != null && BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.builtInRegistryHolder().is(CAPTURABLE)).orElse(false);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (captured || player == null || !player.mayBuild()) return super.useOn(context);
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SpawnerBlockEntity spawner) {
            var tag = spawner.saveWithoutMetadata(context.getLevel().registryAccess());
            boolean valid = capturable(tag.getCompoundOrEmpty("SpawnData"));
            for (var potential : tag.getListOrEmpty("SpawnPotentials")) {
                if (potential instanceof CompoundTag compound) valid |= capturable(compound.getCompoundOrEmpty("data"));
            }
            if (valid) {
                if (!context.getLevel().isClientSide()) give(player, context.getItemInHand(), context.getHand());
                return InteractionResult.SUCCESS;
            }
        }
        return super.useOn(context);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (captured || !player.mayBuild() || !entity.getType().builtInRegistryHolder().is(CAPTURABLE)) return InteractionResult.PASS;
        if (!player.level().isClientSide()) { give(player, stack, hand); entity.discard(); }
        return InteractionResult.SUCCESS;
    }
    private void give(Player player, ItemStack stack, InteractionHand hand) {
        var filled = new ItemStack(AllItems.BLAZE_BURNER);
        stack.consume(1, player);
        if (stack.isEmpty()) player.setItemInHand(hand, filled);
        else player.getInventory().placeItemBackInInventory(filled, Prediction.SERVER_ONLY);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BLAZE_HURT, net.minecraft.sounds.SoundSource.BLOCKS, 0.25f, 0.75f);
    }
}
