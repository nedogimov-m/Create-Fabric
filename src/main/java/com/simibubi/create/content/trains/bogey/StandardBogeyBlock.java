package com.simibubi.create.content.trains.bogey;
import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class StandardBogeyBlock extends AbstractBogeyBlock<StandardBogeyBlockEntity> {
    public StandardBogeyBlock(Properties properties,BogeySizes.BogeySize size){super(properties,size);}
    @Override public TrackMaterial.TrackType getTrackType(BogeyStyle style){return TrackMaterial.TrackType.STANDARD;}
    @Override public double getWheelPointSpacing(){return 2;}
    @Override public double getWheelRadius(){return(size==BogeySizes.LARGE?12.5:6.5)/16d;}
    @Override public Vec3 getConnectorAnchorOffset(){return new Vec3(0,7/32f,1);}
    @Override public BogeyStyle getDefaultStyle(){return AllBogeyStyles.STANDARD;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StandardBogeyBlockEntity(pos,state);}
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){return new ItemStack(AllBlocks.RAILWAY_CASING);}
}
