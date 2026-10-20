package com.simibubi.create.content.equipment.sandPaper;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.ClipContext;

public final class SandPaperItem extends Item {
    public SandPaperItem(Properties properties) { super(properties.durability(8)); }
    public static void spawnParticles(net.minecraft.world.phys.Vec3 location, ItemStack polishedStack, Level world) {
        if (!world.isClientSide() || polishedStack.isEmpty()) return;
        var data = new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
            net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(polishedStack));
        for (int i = 0; i < 20; i++) {
            var motion = net.createmod.catnip.math.VecHelper.offsetRandomly(net.minecraft.world.phys.Vec3.ZERO, world.getRandom(), 1 / 8f);
            world.addParticle(data, location.x, location.y, location.z, motion.x, motion.y, motion.z);
        }
    }
    private static boolean canPolish(Level level, ItemStack stack) {
        return !stack.isEmpty() && level instanceof ServerLevel server && server.getServer().getRecipeManager()
                .getRecipeFor(AllRecipeTypes.SANDPAPER_POLISHING, new SingleRecipeInput(stack), level).isPresent();
    }
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack paper = player.getItemInHand(hand);
        if (level.isClientSide()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (!paper.has(AllDataComponents.SAND_PAPER_POLISHING)) {
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            var input = player.getItemInHand(other);
            if (canPolish(level, input)) {
                paper.set(AllDataComponents.SAND_PAPER_POLISHING, input.split(1));
            } else {
                var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
                ItemEntity candidate = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(hit.getLocation(), hit.getLocation()).inflate(1))
                        .stream().filter(item -> !item.isRemoved() && item.distanceToSqr(player) <= 9 && canPolish(level, item.getItem())).findFirst().orElse(null);
                if (candidate == null) return InteractionResult.FAIL;
                paper.set(AllDataComponents.SAND_PAPER_POLISHING, candidate.getItem().split(1));
                if (candidate.getItem().isEmpty()) candidate.discard();
            }
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.EAT; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 32; }
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!stack.has(AllDataComponents.SAND_PAPER_POLISHING)) return;
        int elapsed = getUseDuration(stack, entity) - remaining;
        if (!level.isClientSide() && elapsed >= 6 && (elapsed - 6) % 7 == 0) {
            level.playSound(null, entity.blockPosition(), AllSoundEvents.SANDING_SHORT, net.minecraft.sounds.SoundSource.PLAYERS, 1, 1);
        }
    }
    @Override
    public ItemStack finishUsingItem(ItemStack paper, Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player)) return paper;
        ItemStack input = paper.get(AllDataComponents.SAND_PAPER_POLISHING);
        if (input == null) return paper;
        var match = server.getServer().getRecipeManager().getRecipeFor(AllRecipeTypes.SANDPAPER_POLISHING, new SingleRecipeInput(input), level);
        if (match.isPresent()) {
            for (var output : match.get().value().results()) {
                player.getInventory().placeItemBackInInventory(output.rollOutput(level.getRandom()), Prediction.SERVER_ONLY);
            }
            var remainder = input.getItem().getCraftingRemainder();
            if (remainder != null) player.getInventory().placeItemBackInInventory(remainder.create(), Prediction.SERVER_ONLY);
            paper.hurtAndBreak(1, player, player.getUsedItemHand());
        } else player.getInventory().placeItemBackInInventory(input, Prediction.SERVER_ONLY);
        paper.remove(AllDataComponents.SAND_PAPER_POLISHING);
        return paper;
    }
    @Override
    public boolean releaseUsing(ItemStack paper, Level level, LivingEntity entity, int remaining) {
        if (!level.isClientSide() && entity instanceof Player player) {
            ItemStack input = paper.remove(AllDataComponents.SAND_PAPER_POLISHING);
            if (input != null) player.getInventory().placeItemBackInInventory(input, Prediction.SERVER_ONLY);
        }
        return true;
    }
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        if (player == null || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        var state = level.getBlockState(pos);
        var previous = WeatheringCopper.getPrevious(state).orElse(null);
        int event = net.minecraft.world.level.block.LevelEvent.PARTICLES_SCRAPE;
        if (previous == null) {
            var unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock());
            if (unwaxed == null) return InteractionResult.PASS;
            previous = unwaxed.withPropertiesOf(state);
            event = net.minecraft.world.level.block.LevelEvent.PARTICLES_WAX_OFF;
        }
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, previous);
            level.levelEvent(player, event, pos, 0);
            level.playSound(null, pos, AllSoundEvents.SANDING_LONG, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
            context.getItemInHand().hurtAndBreak(1, player, context.getHand());
        }
        return InteractionResult.SUCCESS;
    }
}
