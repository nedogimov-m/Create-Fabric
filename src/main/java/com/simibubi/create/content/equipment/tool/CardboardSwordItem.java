package com.simibubi.create.content.equipment.tool;
import com.simibubi.create.*;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
/** Actual native sword components plus the original harmless nonarthropod knockback interaction. */
public final class CardboardSwordItem extends Item {
 public CardboardSwordItem(Properties properties){super(properties.sword(AllToolMaterials.CARDBOARD,3,1).stacksTo(1)
  // MAX_DAMAGE's modern codec requires positive values. Source durability0 means no wear.
  // This declares native item defaults; no component is removed from a player's actual stack.
  .component(DataComponents.MAX_DAMAGE,null).component(DataComponents.DAMAGE,null)
  .component(DataComponents.COOKING_FUEL,com.simibubi.create.foundation.item.CreateCookingFuels.component("cardboard_sword")));}
 public boolean supportsEnchantment(ItemStack stack,Holder<Enchantment> enchantment){return enchantment.is(Enchantments.KNOCKBACK);}
 public boolean isBookEnchantable(ItemStack stack,ItemStack book){var enchantments=book.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY);for(var enchantment:enchantments.keySet())if(!enchantment.is(Enchantments.KNOCKBACK))return false;return true;}
 public static boolean cardboardSwordsCannotHurtYou(Player attacker,Entity entity){
  if(!(entity instanceof LivingEntity target)||target.getType().builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD))return false;
  var stack=attacker.getItemInHand(InteractionHand.MAIN_HAND);if(!(stack.getItem() instanceof CardboardSwordItem))return false;
  attacker.level().playSound(null,attacker.blockPosition(),AllSoundEvents.CARDBOARD_SWORD,SoundSource.PLAYERS,.75f,1.85f);
  float strength=(float)(attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK)+2);
  if(attacker.level() instanceof ServerLevel server)strength=EnchantmentHelper.modifyKnockback(server,stack,target,server.damageSources().playerAttack(attacker),strength);
  if(attacker.isSprinting()&&attacker.getAttackStrengthScale(.5f)>.9f)strength++;
  if(strength<=0)return true;float yaw=attacker.getYRot();knockback(target,strength,yaw,attacker.damageSources().playerAttack(attacker));
  if(target instanceof ServerPlayer player)ServerPlayNetworking.send(player,new KnockbackPacket(yaw,strength));
  var category=target.getType().getCategory();if(!(target instanceof Player)&&(category==MobCategory.MISC||category==MobCategory.CREATURE))target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,60,9,true,false,false));
  attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(.6,1,.6));attacker.setSprinting(false);return true;
 }
 public static void knockback(LivingEntity target,double strength,float yaw){knockback(target,strength,yaw,target.damageSources().generic());}
 private static void knockback(LivingEntity target,double strength,float yaw,net.minecraft.world.damagesource.DamageSource source){target.stopRiding();target.knockback(strength*.5,Mth.sin(yaw*Mth.DEG_TO_RAD),-Mth.cos(yaw*Mth.DEG_TO_RAD),source,0);}
 public static void register(){
  PayloadTypeRegistry.clientboundPlay().register(KnockbackPacket.TYPE,KnockbackPacket.CODEC);
  EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment,stack,context)->stack.getItem() instanceof CardboardSwordItem sword?TriState.of(sword.supportsEnchantment(stack,enchantment)):TriState.DEFAULT);
  AttackBlockCallback.EVENT.register((player,level,hand,pos,face)->{if(player.getItemInHand(hand).getItem() instanceof CardboardSwordItem)level.playSound(level.isClientSide()?player:null,pos,AllSoundEvents.CARDBOARD_SWORD,SoundSource.PLAYERS,.5f,1.85f);return InteractionResult.PASS;});
 }
}
