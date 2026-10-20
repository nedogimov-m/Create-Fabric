package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.legacy.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real native ItemEntity ticks and save/load, replacing the source NeoForge item extension. */
@Mixin(ItemEntity.class)
public abstract class LegacyItemEntityMixin implements LegacyItemData {
 @Shadow @Final private static net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> DATA_ITEM;
 @Unique private CompoundTag create$legacy=new CompoundTag();
 @Override public CompoundTag create$legacyData(){return create$legacy;}
 @Override public void create$legacySyncStack(){var entity=(ItemEntity)(Object)this;entity.getEntityData().set(DATA_ITEM,entity.getItem(),true);}
 @Inject(method="tick",at=@At("HEAD"),cancellable=true)
 private void create$legacyTick(CallbackInfo callback){var entity=(ItemEntity)(Object)this;var stack=entity.getItem();if(stack.isEmpty()||entity.isRemoved())return;boolean handled=false;if(stack.getItem() instanceof ChromaticCompoundItem compound)handled=compound.onEntityItemUpdate(stack,entity);else if(stack.getItem() instanceof NoGravMagicalDohickyItem magical)handled=magical.onEntityItemUpdate(stack,entity);if(handled)callback.cancel();}
 @Inject(method="addAdditionalSaveData",at=@At("RETURN"))
 private void create$saveLegacy(ValueOutput output,CallbackInfo callback){if(!create$legacy.isEmpty())output.store("CreateLegacy",CompoundTag.CODEC,create$legacy);}
 @Inject(method="readAdditionalSaveData",at=@At("RETURN"))
 private void create$loadLegacy(ValueInput input,CallbackInfo callback){create$legacy=input.read("CreateLegacy",CompoundTag.CODEC).orElseGet(CompoundTag::new);var old=input.read("NeoForgeData",CompoundTag.CODEC).orElseGet(CompoundTag::new);for(var key:java.util.List.of("JustCreated","PlayEffects"))if(!create$legacy.contains(key)&&old.contains(key))create$legacy.putBoolean(key,old.getBooleanOr(key,false));}
}
