package com.simibubi.create.content.fluids;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;

/** Native Minecraft implementation of Create's paired source/flowing fluids. */
public abstract class CreateFlowingFluid extends FlowingFluid {
    public static final class Family {
        public final boolean virtual;
        public final int viscosity;
        public final CreateFlowingFluid source,flowing;
        public Block block=Blocks.AIR;
        public Item bucket=Items.AIR;
        public Family(boolean virtual,int viscosity){this.virtual=virtual;this.viscosity=viscosity;source=new Source(this);flowing=new Flowing(this);}
    }
    protected final Family family;
    private CreateFlowingFluid(Family family){this.family=family;}
    @Override public Fluid getSource(){return family.source;}
    @Override public Fluid getFlowing(){return family.flowing;}
    @Override public Item getBucket(){return family.bucket;}
    @Override public boolean isSame(Fluid fluid){return fluid==family.source||fluid==family.flowing;}
    @Override protected BlockState createLegacyBlock(FluidState state){return family.virtual?Blocks.AIR.defaultBlockState():family.block.defaultBlockState().setValue(LiquidBlock.LEVEL,getLegacyLevel(state));}
    @Override protected boolean canConvertToSource(ServerLevel level){return false;}
    @Override protected int getSlopeFindDistance(LevelReader level){return 3;}
    @Override protected int getDropOff(LevelReader level){return 2;}
    @Override public int getTickDelay(LevelReader level){return 25;}
    @Override protected float getExplosionResistance(){return 100;}
    @Override protected boolean canBeReplacedWith(FluidState state,BlockGetter level,BlockPos pos,Fluid fluid,Direction side){return false;}
    @Override protected void beforeDestroyingBlock(LevelAccessor level,BlockPos pos,BlockState state){
        Block.dropResources(state,level,pos,state.hasBlockEntity()?level.getBlockEntity(pos):null);
    }
    @Override public java.util.Optional<net.minecraft.sounds.SoundEvent> getPickupSound(){return java.util.Optional.of(SoundEvents.BUCKET_FILL);}
    private static final class Source extends CreateFlowingFluid {
        Source(Family family){super(family);}
        @Override public boolean isSource(FluidState state){return true;}
        @Override public int getAmount(FluidState state){return family.virtual?0:8;}
    }
    private static final class Flowing extends CreateFlowingFluid {
        Flowing(Family family){super(family);registerDefaultState(defaultFluidState().setValue(LEVEL,7));}
        @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid,FluidState> builder){super.createFluidStateDefinition(builder);builder.add(LEVEL);}
        @Override public boolean isSource(FluidState state){return false;}
        @Override public int getAmount(FluidState state){return family.virtual?0:state.getValue(LEVEL);}
    }
}
