package com.simibubi.create.content.schematics.cannon;

import com.simibubi.create.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SchematicannonBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE=Shapes.or(box(1,0,1,15,8,15),box(.5,8,.5,15.5,11,15.5));
    public SchematicannonBlock(Properties properties){super(properties);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SchematicannonBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.SCHEMATICANNON,SchematicannonBlockEntity::tick);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(placer!=null&&com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos) instanceof SchematicannonBlockEntity cannon){cannon.defaultYaw=(-Mth.floor((placer.getYRot()+(placer.isShiftKeyDown()?180:0))*16/360+.5f)&15)*360/16f;cannon.notifyUpdate();}}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos) instanceof SchematicannonBlockEntity cannon&&cannon.canPlayerUse(player)){if(player instanceof ServerPlayer server)server.openMenu(cannon);return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,net.minecraft.world.level.redstone.Orientation orientation,boolean moved){if(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos) instanceof SchematicannonBlockEntity cannon)cannon.neighbourCheckCooldown=0;}
}
