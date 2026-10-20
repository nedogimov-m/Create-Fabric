package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.graph.TrackNodeLocation.DiscoveredLocation;
import com.simibubi.create.foundation.block.IHaveBigOutline;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

/** Native world track. Straight/diagonal/slope/crossing geometry and reciprocal Bezier endpoints. */
public class TrackBlock extends Block implements EntityBlock,SimpleWaterloggedBlock,ITrackBlock,IHaveBigOutline {
    public static final EnumProperty<TrackShape> SHAPE=EnumProperty.create("shape",TrackShape.class);
    public static final BooleanProperty HAS_BE=BooleanProperty.create("turn"),WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private final TrackMaterial material;
    public TrackBlock(Properties properties,TrackMaterial material){
        super(properties.strength(.8f).sound(SoundType.METAL).noOcclusion().forceSolidOn().pushReaction(PushReaction.IMMOVEABLE));this.material=material;
        registerDefaultState(defaultBlockState().setValue(SHAPE,TrackShape.ZO).setValue(HAS_BE,false).setValue(WATERLOGGED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(SHAPE,HAS_BE,WATERLOGGED);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){
        if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        if(state.getValue(SHAPE).isPortal()&&side==TrackPortalConnection.facing(state)){
            if(level instanceof Level world&&!LoadedLevelAccess.isLoaded(world,next)){ticks.scheduleTick(pos,this,20);return state;}
            if(!com.simibubi.create.api.contraption.train.PortalTrackProvider.isSupportedPortal(neighbor))return Blocks.AIR.defaultBlockState();
        }
        return state;
    }
    /** Exact source portal cube color/size/velocity, through the genuine native six-face particle. */
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        if(!state.getValue(SHAPE).isPortal())return;
        var origin=Vec3.atLowerCornerOf(pos).subtract(.125f,0,.125f);
        var particle=new com.simibubi.create.content.trains.CubeParticleData(1,random.nextFloat(),1,.0125f+.0625f*random.nextFloat(),30,false);
        level.addParticle(particle,origin.x+random.nextFloat()*1.5f,origin.y+.25f,origin.z+random.nextFloat()*1.5f,0,.04,0);
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        if(!level.isClientSide()&&(old.getBlock()!=this||old!=state)){level.scheduleTick(pos,this,1);updateGirders(state,level,pos);}
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        TrackPropagator.onRailAdded(level,pos,state);
        if(!state.getValue(SHAPE).isPortal())TrackPortalConnection.tryConnect(level,pos,state);
        else if(TrackBlockEntityAccess.existing(level,pos) instanceof TrackBlockEntity owner)TrackPortalConnection.tick(owner);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder parameters){
        var be=parameters.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        // Legacy pairs must acquire their actual reciprocal identity before they can refund a rail.
        if(state.getValue(SHAPE).isPortal()&&(!(be instanceof TrackBlockEntity owner)||owner.getPortalBinding()==null))return List.of();
        if(be instanceof TrackBlockEntity owner&&(owner.portalCounterpartRemoval||owner.getPortalBinding()!=null&&TrackPortalCleanup.get(parameters.getLevel().getServer()).isCounterpartPending(GlobalPos.of(parameters.getLevel().dimension(),owner.getBlockPos()),owner.getPortalBinding())))return List.of();
        return super.getDrops(state,parameters);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moving){TrackPropagator.onRailRemoved(level,pos,state);updateGirders(state,level,pos);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(LoadedLevelAccess.blockEntity(level,pos) instanceof TrackBlockEntity track)track.validateConnections();
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.isCreative()&&LoadedLevelAccess.blockEntity(level,pos) instanceof TrackBlockEntity track)track.cancelDrops=true;
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(HAS_BE)?new TrackBlockEntity(pos,state):null;}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return type==AllBlockEntityTypes.TRACK?(world,pos,block,be)->{if(world.isClientSide())((TrackBlockEntity)be).tickClient();else ((TrackBlockEntity)be).tick();}:null;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return TrackShapes.outline(state.getValue(SHAPE));}
    @Override protected VoxelShape getInteractionShape(BlockState state,BlockGetter level,BlockPos pos){return TrackShapes.outline(state.getValue(SHAPE));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return TrackShapes.collision(state.getValue(SHAPE));}
    public net.minecraft.world.InteractionResult onWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext context){return net.minecraft.world.InteractionResult.SUCCESS;}
    public net.minecraft.world.InteractionResult onSneakWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext context){
        var player=context.getPlayer();if(player==null)return net.minecraft.world.InteractionResult.PASS;
        if(context.getLevel().isClientSide())return player.mayBuild()&&!player.isSpectator()?net.minecraft.world.InteractionResult.SUCCESS:net.minecraft.world.InteractionResult.PASS;
        return CurvedTrackServerInteraction.dismantleEndpoint(player,context)?net.minecraft.world.InteractionResult.SUCCESS:net.minecraft.world.InteractionResult.FAIL;
    }
    private void updateGirders(BlockState state,Level level,BlockPos pos){
        for(var axis:getTrackAxes(level,pos,state)){
            if(axis.length()>1||axis.y!=0||axis.lengthSqr()<1e-12)continue;
            for(int side:new int[]{-1,1}){var at=pos.below().offset(BlockPos.containing(axis.z*side,0,axis.x*side));if(!LoadedLevelAccess.isLoaded(level,at))continue;
                if(LoadedLevelAccess.blockState(level,at).getBlock() instanceof com.simibubi.create.content.decoration.girder.GirderBlock girder&&!level.getBlockTicks().hasScheduledTick(at,girder))level.scheduleTick(at,girder,1);
            }
        }
    }
    @Override public int getYOffsetAt(BlockGetter level,BlockPos pos,BlockState state,Vec3 end){
        var track=readOnlyOwner(level,pos,state);return track==null?0:track.tilt.getYOffsetForAxisEnd(end);
    }
    @Override public Collection<DiscoveredLocation> getConnected(BlockGetter worldIn,BlockPos pos,BlockState state,boolean linear,TrackNodeLocation connectedTo){
        BlockGetter world=connectedTo!=null&&worldIn instanceof ServerLevel server?server.getServer().getLevel(connectedTo.dimension):worldIn;
        if(world==null)throw new UnloadedTrackException(connectedTo.dimension,List.of(pos));
        if(world instanceof Level level&&!LoadedLevelAccess.isLoaded(level,pos))throw new UnloadedTrackException(level,pos);
        var owner=readOnlyOwner(world,pos,state);
        Collection<DiscoveredLocation> result;
        if(getTrackAxes(world,pos,state).size()>1){
            result=new ArrayList<>();var center=Vec3.atBottomCenterOf(pos).add(0,getElevationAtCenter(world,pos,state),0);
            for(var axis:getTrackAxes(world,pos,state))for(boolean fromCenter:new boolean[] {true, false})
                ITrackBlock.addToListIfConnected(connectedTo,result,(d,b)->axis.scale(b?0:fromCenter?-d:d).add(center),b->getUpNormal(world,pos,state),b->world instanceof Level l?l.dimension():Level.OVERWORLD,v->0,axis,null,(b,v)->ITrackBlock.getMaterialSimple(world,v));
        }else result=ITrackBlock.super.getConnected(world,pos,state,linear,connectedTo);
        if(linear||!state.getValue(HAS_BE))return result;
        if(owner!=null)for(var curve:owner.getConnections().values())
            ITrackBlock.addToListIfConnected(connectedTo,result,(d,b)->d==1?Vec3.atLowerCornerOf(curve.bePositions.get(b)):curve.starts.get(b),curve.normals::get,b->world instanceof Level l?l.dimension():Level.OVERWORLD,curve::yOffsetAt,null,curve,(b,v)->ITrackBlock.getMaterialSimple(world,v,curve.getMaterial()));
        if(owner!=null&&world instanceof ServerLevel server)TrackPortalConnection.appendConnections(server,pos,state,owner,connectedTo,result);
        return result;
    }
    private TrackBlockEntity readOnlyOwner(BlockGetter reader,BlockPos pos,BlockState state){
        var owner=TrackBlockEntityAccess.existing(reader,pos);
        if(state.getValue(HAS_BE)&&!(owner instanceof TrackBlockEntity)&&reader instanceof Level level){
            // Removal also discovers former track states. Only actual native owners can be unknown.
            var actual=LoadedLevelAccess.blockState(level,pos);
            if(actual.getBlock()==this&&actual.getValue(HAS_BE))throw new UnloadedTrackException(level,pos);
        }
        return owner instanceof TrackBlockEntity track?track:null;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockState stateForPlacement = defaultBlockState().setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).getType()==Fluids.WATER);

		if (ctx.getPlayer() == null)
			return stateForPlacement;

		Vec3 lookAngle = ctx.getPlayer()
			.getLookAngle();
		lookAngle = lookAngle.multiply(1, 0, 1);
		if (Mth.equal(lookAngle.length(), 0))
			lookAngle = new Vec3(0, 0, 1).yRot((float)Math.toRadians(-ctx.getPlayer().getYRot()));

		lookAngle = lookAngle.normalize();

		TrackShape best = TrackShape.ZO;
		double bestValue = Float.MAX_VALUE;
		for (TrackShape shape : TrackShape.values()) {
			if (shape.isJunction() || shape.isPortal())
				continue;
			Vec3 axis = shape.getAxes()
				.get(0);
			double distance = Math.min(axis.distanceToSqr(lookAngle), axis.normalize()
				.scale(-1)
				.distanceToSqr(lookAngle));
			if (distance > bestValue)
				continue;
			bestValue = distance;
			best = shape;
		}

		Level level = ctx.getLevel();
		Vec3 bestAxis = best.getAxes()
			.get(0);
		if (bestAxis.lengthSqr() == 1)
			for (boolean neg : new boolean[] {true, false}) {
				BlockPos offset = ctx.getClickedPos()
					.offset(BlockPos.containing(bestAxis.scale(neg ? -1 : 1)));

				if (LoadedLevelAccess.blockState(level,offset)
					.isFaceSturdy(level, offset, Direction.UP)
					&& !LoadedLevelAccess.blockState(level,offset.above())
					.isFaceSturdy(level, offset, Direction.DOWN)) {
					if (best == TrackShape.XO)
						best = neg ? TrackShape.AW : TrackShape.AE;
					if (best == TrackShape.ZO)
						best = neg ? TrackShape.AN : TrackShape.AS;
				}
			}

		return stateForPlacement.setValue(SHAPE, best);
	}

    @Override public boolean canSurvive(BlockState state, LevelReader reader, BlockPos pos) {
		return LoadedLevelAccess.blockState(reader,pos.below())
			.getBlock() != this;
	}

    @Override public Vec3 getUpNormal(BlockGetter world, BlockPos pos, BlockState state) {
		return state.getValue(SHAPE)
			.getNormal();
	}

    @Override public List<Vec3> getTrackAxes(BlockGetter world, BlockPos pos, BlockState state) {
		return state.getValue(SHAPE)
			.getAxes();
	}

    @Override public Vec3 getCurveStart(BlockGetter world, BlockPos pos, BlockState state, Vec3 axis) {
		boolean vertical = axis.y != 0;
		return Vec3.atCenterOf(pos)
			.add(0, (vertical ? 0 : -.5f), 0)
			.add(axis.scale(.5));
	}

    @Override public BlockState overlay(BlockGetter world, BlockPos pos, BlockState existing, BlockState placed) {
		if (placed.getBlock() != this)
			return existing;

		TrackShape existingShape = existing.getValue(SHAPE);
		TrackShape placedShape = placed.getValue(SHAPE);
		TrackShape combinedShape = null;

		for (boolean flip : new boolean[] {true, false}) {
			TrackShape s1 = flip ? existingShape : placedShape;
			TrackShape s2 = flip ? placedShape : existingShape;
			if (s1 == TrackShape.XO && s2 == TrackShape.ZO)
				combinedShape = TrackShape.CR_O;
			if (s1 == TrackShape.PD && s2 == TrackShape.ND)
				combinedShape = TrackShape.CR_D;
			if (s1 == TrackShape.XO && s2 == TrackShape.PD)
				combinedShape = TrackShape.CR_PDX;
			if (s1 == TrackShape.ZO && s2 == TrackShape.PD)
				combinedShape = TrackShape.CR_PDZ;
			if (s1 == TrackShape.XO && s2 == TrackShape.ND)
				combinedShape = TrackShape.CR_NDX;
			if (s1 == TrackShape.ZO && s2 == TrackShape.ND)
				combinedShape = TrackShape.CR_NDZ;
		}

		if (combinedShape != null)
			existing = existing.setValue(SHAPE, combinedShape);
		return existing;
	}

    @Override public BlockState rotate(BlockState state, Rotation pRotation) {
		return state.setValue(SHAPE, state.getValue(SHAPE)
			.rotate(pRotation));
	}

    @Override public BlockState mirror(BlockState state, Mirror pMirror) {
		return state.setValue(SHAPE, state.getValue(SHAPE)
			.mirror(pMirror));
	}

    @Override public BlockState getBogeyAnchor(BlockGetter world, BlockPos pos, BlockState state) {
		return AllBlocks.SMALL_BOGEY.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_AXIS, state.getValue(SHAPE) == TrackShape.XO ? Axis.X : Axis.Z);
	}

    @Override public boolean trackEquals(BlockState state1, BlockState state2) {
		return state1.getBlock() == this && state2.getBlock() == this
			&& state1.setValue(HAS_BE, false) == state2.setValue(HAS_BE, false);
	}

    @Override public TrackMaterial getMaterial() {
		return material;
	}
}
