package com.simibubi.create.content.equipment.zapper;
import com.simibubi.create.*;
import com.simibubi.create.content.schematics.SchematicNbt;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Objects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.HitResult;

public class ZapperInteractionHandler {
    public static boolean trySelect(ItemStack stack,Player player){
        if(!(stack.getItem() instanceof ZapperItem)||player.isShiftKeyDown()||player.isSpectator())return false;
        var hit=ZapperWorldAccess.clip(player,getRange(stack));if(hit.getType()==HitResult.Type.MISS)return false;var pos=hit.getBlockPos();if(!ZapperWorldAccess.loaded(player.level(),pos)||!player.level().mayInteract(player,pos))return false;
        var state=ZapperWorldAccess.read(player.level(),pos);
        if(new ItemStack(state.getBlock()).isEmpty()||state.hasBlockEntity()&&!state.is(ZapperItem.SAFE_NBT)||state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)||state.hasProperty(BlockStateProperties.ATTACHED)||state.hasProperty(BlockStateProperties.HANGING)||state.hasProperty(BlockStateProperties.BED_PART))return false;
        if(state.hasProperty(BlockStateProperties.STAIRS_SHAPE))state=state.setValue(BlockStateProperties.STAIRS_SHAPE,StairsShape.STRAIGHT);
        if(state.hasProperty(BlockStateProperties.PERSISTENT))state=state.setValue(BlockStateProperties.PERSISTENT,true);
        if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        var entity=LoadedLevelAccess.blockEntity(player.level(),pos);var data=entity==null?null:SchematicNbt.prepare(player.level(),state,entity);if(data!=null)data.remove("id");
        if(stack.get(AllDataComponents.SHAPER_BLOCK_USED)==state&&Objects.equals(data,stack.get(AllDataComponents.SHAPER_BLOCK_DATA)))return false;
        stack.set(AllDataComponents.SHAPER_BLOCK_USED,state);if(data==null)stack.remove(AllDataComponents.SHAPER_BLOCK_DATA);else stack.set(AllDataComponents.SHAPER_BLOCK_DATA,data.copy());
        player.level().destroyBlockProgress(player.getId(),pos,-1);player.level().playSound(null,player.blockPosition(),AllSoundEvents.CONFIRM,net.minecraft.sounds.SoundSource.PLAYERS,1,1);return true;
    }
    public static int getRange(ItemStack stack){return stack.getItem() instanceof ZapperItem zapper?zapper.getZappingRange(stack):0;}
    public ZapperInteractionHandler(){}
}
