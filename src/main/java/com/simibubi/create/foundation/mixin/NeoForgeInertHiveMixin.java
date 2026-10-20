package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.foundation.utility.NeoForgeInertHiveOwner;
import com.simibubi.create.foundation.utility.NeoForgeInertHiveSlots;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Preserve old codec-empty inert slots beside real native BeeData, never as fabricated typed bees. */
@Mixin(BeehiveBlockEntity.class)
public abstract class NeoForgeInertHiveMixin implements NeoForgeInertHiveOwner {
    @Shadow @Final private List<?> stored;
    @Unique private NeoForgeInertHiveSlots create$inert=NeoForgeInertHiveSlots.EMPTY;
    @Unique private List<?> create$lastNative=List.of();
    @Unique private boolean create$resetting;
    @Unique private void create$reconcile(){
        if(create$resetting)return;
        create$inert=create$inert.afterNativeChange(create$lastNative,stored);create$lastNative=List.copyOf(stored);
    }
    @Override public NeoForgeInertHiveSlots create$inertHiveSlots(){create$reconcile();return create$inert;}
    @Override public void create$tickInertHiveSlots(){create$reconcile();create$inert=create$inert.tick();}
    @Inject(method="loadAdditional",at=@At("HEAD"))
    private void create$beginLoad(ValueInput input,CallbackInfo callback){create$resetting=true;create$inert=NeoForgeInertHiveSlots.EMPTY;create$lastNative=List.of();}
    @Inject(method="loadAdditional",at=@At("RETURN"))
    private void create$loadInert(ValueInput input,CallbackInfo callback){
        create$inert=input.read(NeoForgeInertHiveSlots.KEY,NeoForgeInertHiveSlots.CODEC).orElse(NeoForgeInertHiveSlots.EMPTY);
        create$inert.validateNativeCount(stored.size());create$lastNative=List.copyOf(stored);create$resetting=false;
    }
    @Inject(method="saveAdditional",at=@At("RETURN"))
    private void create$saveInert(ValueOutput output,CallbackInfo callback){
        create$reconcile();if(!create$inert.isEmpty())output.store(NeoForgeInertHiveSlots.KEY,NeoForgeInertHiveSlots.CODEC,create$inert);
    }
    @Inject(method="applyImplicitComponents",at=@At("HEAD"))
    private void create$beginComponents(DataComponentGetter input,CallbackInfo callback){create$resetting=true;create$inert=NeoForgeInertHiveSlots.EMPTY;create$lastNative=List.of();}
    @Inject(method="applyImplicitComponents",at=@At("RETURN"))
    private void create$applyInert(DataComponentGetter input,CallbackInfo callback){
        create$inert=input.getOrDefault(AllDataComponents.INERT_HIVE_SLOTS,NeoForgeInertHiveSlots.EMPTY);
        create$inert.validateNativeCount(stored.size());create$lastNative=List.copyOf(stored);create$resetting=false;
    }
    @Inject(method="collectImplicitComponents",at=@At("RETURN"))
    private void create$collectInert(DataComponentMap.Builder output,CallbackInfo callback){
        create$reconcile();output.set(AllDataComponents.INERT_HIVE_SLOTS,create$inert.isEmpty()?null:create$inert);
    }
    @Inject(method="removeComponentsFromTag",at=@At("RETURN"))
    private void create$removeImplicitCopy(ValueOutput output,CallbackInfo callback){output.discard(NeoForgeInertHiveSlots.KEY);}
    @ModifyReturnValue(method="getOccupantCount",at=@At("RETURN"))
    private int create$combinedCount(int count){return Math.addExact(count,create$inert.size());}
    @ModifyReturnValue(method="isEmpty",at=@At("RETURN"))
    private boolean create$combinedEmpty(boolean empty){return empty&&create$inert.isEmpty();}
    @ModifyExpressionValue(method={"isFull","addOccupant"},at=@At(value="INVOKE",target="Ljava/util/List;size()I"),require=2)
    private int create$combinedCapacity(int count){return Math.addExact(count,create$inert.size());}
    @Inject(method="storeBee",at=@At("RETURN"))
    private void create$afterRealBee(CallbackInfo callback){create$reconcile();}
    @Inject(method="releaseAllOccupants",at=@At("RETURN"))
    private void create$afterRealRelease(CallbackInfoReturnable<List<?>> callback){create$reconcile();}
    @Inject(method="serverTick",at=@At("RETURN"))
    private static void create$tickInert(Level level,BlockPos position,BlockState state,BeehiveBlockEntity hive,CallbackInfo callback){
        ((NeoForgeInertHiveOwner)hive).create$tickInertHiveSlots();
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Ljava/util/List;isEmpty()Z"))
    private static boolean create$combinedWorkSound(List<?> actual,Operation<Boolean> original,Level level,BlockPos position,BlockState state,BeehiveBlockEntity hive){
        return original.call(actual)&&((NeoForgeInertHiveOwner)hive).create$inertHiveSlots().isEmpty();
    }
}
