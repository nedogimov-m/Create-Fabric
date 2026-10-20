package com.simibubi.create.content.materials;

import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ExperienceNuggetItem extends Item {
    public ExperienceNuggetItem(Properties properties) { super(properties.rarity(Rarity.UNCOMMON)); }
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) { level.playSound(player, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.5f, 1); return InteractionResult.SUCCESS; }
        int used = player.isShiftKeyDown() ? 1 : stack.getCount(), total = used * 3, maxOrbs = used == 1 ? 1 : 5;
        int perOrb = Math.max(1, 1 + total / maxOrbs);
        for (int i = 0; i < maxOrbs; i++) {
            int value = Math.min(perOrb, total - i * perOrb); if (value <= 0) continue;
            var random = level.getRandom();
            var offset = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1).normalize();
            var look = player.getLookAngle(); double yaw = Math.toRadians(player.getYRot());
            var cross = look.cross(new Vec3(-0.75 * Math.cos(yaw), 0, 0.75 * Math.sin(yaw)));
            var position = player.getEyePosition().add(look.scale(0.5)).add(cross);
            var orb = new ExperienceOrb(level, position, look.scale(0.2).add(0, 0.2, 0).add(offset.scale(0.1)), value); level.addFreshEntity(orb);
        }
        stack.shrink(used); if (stack.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
        return InteractionResult.SUCCESS;
    }
}
