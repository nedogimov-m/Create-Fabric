package com.simibubi.create.content.equipment.potatoCannon;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType;
import com.simibubi.create.content.equipment.EquipmentConfig;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetItemMethods;
import com.simibubi.create.foundation.utility.GlobalRegistryAccess;
import java.util.List;
import java.util.function.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PotatoCannonItem extends ProjectileWeaponItem {
    private static final Predicate<ItemStack> AMMO_PREDICATE=stack->PotatoCannonProjectileType.getTypeForItem(GlobalRegistryAccess.getOrThrow(),stack.getItem()).isPresent();
    public static Supplier<Player> CLIENT_PLAYER=()->null;
    public static Consumer<InteractionHand> DONT_ANIMATE=hand->{};
    public PotatoCannonItem(Properties properties){super(properties.durability(100));}
    @Nullable public static Ammo getAmmo(Player player,ItemStack cannon){
        var stack=GlobalRegistryAccess.with(player.registryAccess(),()->player.getProjectile(cannon));
        return stack.isEmpty()?null:PotatoCannonProjectileType.getTypeForItem(player.registryAccess(),stack.getItem()).map(type->new Ammo(stack,type.value())).orElse(null);
    }
    // Source owns firing in use(), never vanilla bow load/shoot hooks.
    @Override protected void shootProjectile(LivingEntity shooter,Projectile projectile,int index,float velocity,float inaccuracy,float angle,@Nullable LivingEntity target){}
    @Override protected void shoot(ServerLevel level,LivingEntity shooter,InteractionHand hand,ItemStack weapon,List<ItemStack> projectiles,float velocity,float inaccuracy,boolean critical,@Nullable LivingEntity target){}
    @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        ItemStack cannon=player.getItemInHand(hand);
        if(ShootableGadgetItemMethods.shouldSwap(player,cannon,hand,stack->stack.getItem() instanceof PotatoCannonItem))return InteractionResult.FAIL;
        var ammo=getAmmo(player,cannon);if(ammo==null)return InteractionResult.PASS;
        if(level.isClientSide()){DONT_ANIMATE.accept(hand);return InteractionResult.SUCCESS;}
        var type=ammo.type;var ammoStack=ammo.stack;var firedStack=ammoStack.copy();
        Vec3 barrel=ShootableGadgetItemMethods.getGunBarrelVec(player,hand==InteractionHand.MAIN_HAND,new Vec3(.75,-.15,1.5));
        Vec3 correction=ShootableGadgetItemMethods.getGunBarrelVec(player,hand==InteractionHand.MAIN_HAND,new Vec3(-.05,0,0)).subtract(player.position().add(0,player.getEyeHeight(),0));
        Vec3 look=player.getLookAngle(),motion=look.add(correction).normalize().scale(2).scale(type.velocityMultiplier());
        float pitch=type.soundPitch()+(level.getRandom().nextFloat()-.5f)/4f;
        var sprayBase=ShootableGadgetItemMethods.rotateZ(new Vec3(0,.1,0),360*level.getRandom().nextFloat());float sprayChange=360f/type.split();
        for(int i=0;i<type.split();i++){
            var projectile=AllEntityTypes.POTATO_PROJECTILE.create(level,EntitySpawnReason.TRIGGERED);if(projectile==null)throw new IllegalStateException("Registered potato projectile could not be constructed");
            projectile.setItem(firedStack);projectile.setEnchantmentEffectsFromCannon(cannon);Vec3 splitMotion=motion;
            if(type.split()>1){float imperfection=40*(level.getRandom().nextFloat()-.5f);splitMotion=splitMotion.add(ShootableGadgetItemMethods.lookAt(ShootableGadgetItemMethods.rotateZ(sprayBase,i*sprayChange+imperfection),motion));}
            if(i!=0)projectile.setRecoveryChance(0);
            projectile.setPos(barrel.x,barrel.y,barrel.z);projectile.setDeltaMovement(splitMotion);projectile.setOwner(player);level.addFreshEntity(projectile);
        }
        if(!player.hasInfiniteMaterials()){ammoStack.shrink(1);if(ammoStack.isEmpty())player.getInventory().removeItem(ammoStack);}
        if(!BacktankUtil.canAbsorbDamage(player,EquipmentConfig.get(level).maxPotatoCannonShots()))cannon.hurtAndBreak(1,player,hand);
        ShootableGadgetItemMethods.applyCooldown(player,cannon,hand,stack->stack.getItem() instanceof PotatoCannonItem,type.reloadTicks());
        ShootableGadgetItemMethods.sendPackets(player,self->new PotatoCannonPacket(barrel,look.normalize(),firedStack,hand,pitch,self));return InteractionResult.SUCCESS_SERVER;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,display,tooltip,flag);var player=CLIENT_PLAYER.get();if(player==null)return;var ammo=getAmmo(player,stack);if(ammo==null||context.registries()==null)return;
        var lookup=context.registries().lookupOrThrow(Registries.ENCHANTMENT);int power=EnchantmentHelper.getItemEnchantmentLevel(lookup.getOrThrow(Enchantments.POWER),stack),punch=EnchantmentHelper.getItemEnchantmentLevel(lookup.getOrThrow(Enchantments.PUNCH),stack);float extra=1+power*.2f,knockback=punch*.5f,damage=ammo.type.damage()*extra;
        tooltip.accept(CommonComponents.EMPTY);tooltip.accept(Component.translatable(ammo.stack.getItem().getDescriptionId()).append(":").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(" ").append(Component.translatable("create.potato_cannon.ammo.attack_damage",Component.literal(damage==Mth.floor(damage)?""+Mth.floor(damage):""+damage).withStyle(extra>1?ChatFormatting.GREEN:ChatFormatting.DARK_GREEN)).withStyle(ChatFormatting.DARK_GREEN)));
        tooltip.accept(Component.literal(" ").append(Component.translatable("create.potato_cannon.ammo.reload_ticks",Component.literal(""+ammo.type.reloadTicks()).withStyle(ChatFormatting.DARK_GREEN)).withStyle(ChatFormatting.DARK_GREEN)));
        tooltip.accept(Component.literal(" ").append(Component.translatable("create.potato_cannon.ammo.knockback",Component.literal(""+(ammo.type.knockback()+knockback)).withStyle(knockback>0?ChatFormatting.GREEN:ChatFormatting.DARK_GREEN)).withStyle(ChatFormatting.DARK_GREEN)));
    }
    @Override public boolean canDestroyBlock(ItemStack stack,BlockState state,Level level,BlockPos pos,LivingEntity user){return false;}
    @Override public Predicate<ItemStack> getAllSupportedProjectiles(){return AMMO_PREDICATE;}
    @Override public int getDefaultProjectileRange(){return 15;}
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack){return ItemUseAnimation.NONE;}
    @Override public boolean isBarVisible(ItemStack stack){return BacktankUtil.isBarVisible(stack,EquipmentConfig.client().maxPotatoCannonShots());}
    @Override public int getBarWidth(ItemStack stack){return BacktankUtil.getBarWidth(stack,EquipmentConfig.client().maxPotatoCannonShots());}
    @Override public int getBarColor(ItemStack stack){return BacktankUtil.getBarColor(stack,EquipmentConfig.client().maxPotatoCannonShots());}
    public record Ammo(ItemStack stack,PotatoCannonProjectileType type){}
}
