package com.simibubi.create.content.equipment.zapper;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.SchematicNbt;
import com.simibubi.create.foundation.utility.*;
import java.util.function.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.*;

/** Source selection/activation/dual-hand contract, using real native state and actual server ray casts. */
public abstract class ZapperItem extends Item {
    public static final TagKey<Block> SAFE_NBT=TagKey.create(Registries.BLOCK,Create.asResource("safe_nbt"));
    public static BiConsumer<ItemStack,InteractionHand> OPEN_SCREEN;
    public static Consumer<InteractionHand> DONT_ANIMATE;
    public ZapperItem(Properties properties){super(properties.stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,display,tooltip,flag);var state=stack.get(AllDataComponents.SHAPER_BLOCK_USED);if(state!=null)tooltip.accept(Component.translatable("create.terrainzapper.usingBlock",state.getBlock().getName().plainCopy().withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));}
    public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged){var old=before.get(AllDataComponents.SHAPER_BLOCK_USED);var next=after.get(AllDataComponents.SHAPER_BLOCK_USED);return slotChanged||!isZapper(after)||old!=null&&next!=null&&old!=next;}
    public boolean isZapper(ItemStack stack){return stack.getItem() instanceof ZapperItem;}
    @Override public InteractionResult useOn(UseOnContext context){if(context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown())return use(context.getLevel(),context.getPlayer(),context.getHand());return super.useOn(context);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player.isShiftKeyDown()){if(level.isClientSide()){openHandgunGUI(stack,hand);player.getCooldowns().addCooldown(stack,10);}return InteractionResult.SUCCESS;}
        if(ShootableGadgetItemMethods.shouldSwap(player,stack,hand,this::isZapper))return InteractionResult.FAIL;
        var problem=validateUsage(stack);if(problem!=null){level.playSound(null,player.blockPosition(),AllSoundEvents.DENY,net.minecraft.sounds.SoundSource.PLAYERS,1,1);player.sendOverlayMessage(problem.plainCopy().withStyle(ChatFormatting.RED));return InteractionResult.FAIL;}
        var chosen=BlockHelper.setZeroAge(stack.getOrDefault(AllDataComponents.SHAPER_BLOCK_USED,Blocks.AIR.defaultBlockState()));
        var hit=ZapperWorldAccess.clip(player,getZappingRange(stack));
        if(hit.getType()==HitResult.Type.MISS||ZapperWorldAccess.read(level,hit.getBlockPos()).isAir()){ShootableGadgetItemMethods.applyCooldown(player,stack,hand,this::isZapper,getCooldownDelay(stack));return InteractionResult.SUCCESS;}
        if(level.isClientSide()){if(DONT_ANIMATE!=null)DONT_ANIMATE.accept(hand);return InteractionResult.SUCCESS;}
        if(!ZapperWorldAccess.canEdit(level,player,hit.getBlockPos()))return InteractionResult.FAIL;
        var data=chosen.is(SAFE_NBT)?stack.get(AllDataComponents.SHAPER_BLOCK_DATA):null;
        if(activate(level,player,stack,chosen,hit,data==null?null:data.copy())){ShootableGadgetItemMethods.applyCooldown(player,stack,hand,this::isZapper,getCooldownDelay(stack));var barrel=ShootableGadgetItemMethods.getGunBarrelVec(player,hand==InteractionHand.MAIN_HAND,new Vec3(.35,-.1,1));ShootableGadgetItemMethods.sendPackets(player,self->new ZapperBeamPacket(barrel,hand,self,hit.getLocation()));}
        return InteractionResult.SUCCESS_SERVER;
    }
    public Component validateUsage(ItemStack stack){return !canActivateWithoutSelectedBlock(stack)&&!stack.has(AllDataComponents.SHAPER_BLOCK_USED)?Component.translatable("create.terrainzapper.leftClickToSet"):null;}
    protected abstract boolean activate(Level level,Player player,ItemStack stack,BlockState state,BlockHitResult hit,CompoundTag data);
    protected abstract void openHandgunGUI(ItemStack stack,InteractionHand hand);
    protected abstract int getCooldownDelay(ItemStack stack);
    protected abstract int getZappingRange(ItemStack stack);
    protected boolean canActivateWithoutSelectedBlock(ItemStack stack){return false;}
    public boolean onEntitySwing(ItemStack stack,LivingEntity entity){return true;}
    public boolean canAttackBlock(BlockState state,Level level,BlockPos pos,Player player){return false;}
    @Override public boolean canDestroyBlock(ItemStack stack,BlockState state,Level level,BlockPos pos,LivingEntity entity){return entity instanceof Player player?canAttackBlock(state,level,pos,player):false;}
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack){return ItemUseAnimation.NONE;}
    public static void setBlockEntityData(Level level,BlockPos pos,BlockState state,CompoundTag data,Player player){
        if(data==null||!state.is(SAFE_NBT)||!ZapperWorldAccess.canEdit(level,player,pos)||ZapperWorldAccess.read(level,pos)!=state)return;
        var entity=LoadedLevelAccess.blockEntity(level,pos);if(entity==null)return;
        var safe=SchematicNbt.configurationForPlacement(state,entity,data);if(safe==null)return;
        safe.remove("id");safe.putInt("x",pos.getX());safe.putInt("y",pos.getY());safe.putInt("z",pos.getZ());entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),safe));
        if(LoadedLevelAccess.blockEntity(level,pos)==entity){entity.setChanged();level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
    }
}
