package com.simibubi.create.content.fluids.pipes;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public class FluidPipeBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements FluidTransportBehaviour.Provider, net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity, com.simibubi.create.api.contraption.transformable.TransformableBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"water_supply","cross_streams","honey_drain");
    private final com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour bracket =
        new com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour(this,com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour::eligiblePipe);
    public com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour bracketBehaviour(){return bracket;}
    @Override public void transform(BlockEntity owner,com.simibubi.create.content.contraptions.StructureTransform transform){bracket.transformBracket(transform);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){bracket.dropBracket();super.preRemoveSideEffects(pos,state);}
    @Override public void setBlockState(BlockState next){if(getBlockState()!=next&&bracket!=null)bracket.dropBracket();super.setBlockState(next);}
    private final FluidTransportBehaviour transport=createTransport();
    protected FluidTransportBehaviour createTransport(){return new FluidTransportBehaviour(this){
        @Override public boolean canHaveFlowToward(BlockState state,Direction side){return state.getBlock() instanceof FluidPipeBlock?FluidPipeBlock.isOpenAt(state,side):state.getBlock() instanceof AxisPipeBlock&&state.getValue(AxisPipeBlock.AXIS)==side.getAxis();}
    };}
    private BlockState previousState;
    private boolean needsPropagation=true;
    public FluidPipeBlockEntity(BlockPos pos,BlockState state){super(state.getBlock() instanceof AxisPipeBlock?AllBlockEntityTypes.STRAIGHT_PIPE:state.getBlock() instanceof EncasedPipeBlock?AllBlockEntityTypes.ENCASED_FLUID_PIPE:AllBlockEntityTypes.FLUID_PIPE,pos,state);}
    protected FluidPipeBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override public FluidTransportBehaviour transport(){return getBehaviour(FluidTransportBehaviour.TYPE);}
    @Override public Object getRenderData(){return transport.getRenderData();}
    public static void tick(Level level,BlockPos pos,BlockState state,FluidPipeBlockEntity pipe){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, pipe)) {
            if (!createTick.shouldTick()) return;

        if(pipe.previousState!=state){pipe.needsPropagation=true;pipe.previousState=state;}
        if(!level.isClientSide()&&pipe.needsPropagation){pipe.needsPropagation=false;FluidPropagator.propagateChangedPipe(level,pos,state);}
        
    
        }
    }
    public void updateNetwork(){needsPropagation=true;}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);needsPropagation=true;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        if (transport != null) behaviours.add(transport);
        super.addNativeBehaviours(behaviours);
        if (bracket != null) behaviours.add(bracket);
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
