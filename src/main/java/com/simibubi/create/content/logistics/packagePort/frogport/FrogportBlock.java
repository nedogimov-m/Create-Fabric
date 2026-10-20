package com.simibubi.create.content.logistics.packagePort.frogport;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
public final class FrogportBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE=Shapes.or(box(0,0,0,16,4,16),box(2,2,2,14,14,14));
    public FrogportBlock(Properties properties){super(properties.strength(3,6).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_BLUE).noOcclusion());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FrogportBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.PACKAGE_FROGPORT,(l,p,s,port)->port.tick());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(placer!=null&&LoadedLevelAccess.blockEntity(level,pos) instanceof FrogportBlockEntity port){var diff=Vec3.atCenterOf(pos).subtract(placer.position());port.sourceAdvancements.place(placer);port.passiveYaw=Math.round((float)Math.toDegrees(Math.atan2(diff.x,diff.z))/11.25f)*11.25f;port.notifyUpdate();if(!level.isClientSide()&&placer instanceof Player player)com.simibubi.create.content.logistics.packagePort.PackagePortPlacement.placed(player,port);}}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.is(com.simibubi.create.AllItems.WRENCH))return InteractionResult.PASS;return use(level,pos,player);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;return LoadedLevelAccess.blockEntity(level,pos) instanceof FrogportBlockEntity port&&port.use(player)?InteractionResult.SUCCESS:InteractionResult.PASS;}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return LoadedLevelAccess.blockEntity(level,pos) instanceof FrogportBlockEntity port?port.getComparatorOutput():0;}
}
