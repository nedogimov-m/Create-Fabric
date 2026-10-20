package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.chassis.ChassisSettings;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.*;

/** Real source cells include the controller, whose moving head/rod is a virtual structural part. */
public class PistonContraption extends TranslatingContraption {
    private record Extension(BlockPos source,BlockState moving){}
    private final List<Extension> extensions=new ArrayList<>();
    private BlockPos controller=BlockPos.ZERO;
    private Direction orientation=Direction.NORTH;
    public int extensionLength,initialExtensionProgress;
    private boolean retract;
    private AABB extensionBounds=new AABB(BlockPos.ZERO);
    public PistonContraption(){}
    public PistonContraption(Direction direction,boolean retract){orientation=direction;this.retract=retract;}
    @Override public Vec3 positionAtProgress(float progress){return Vec3.atLowerCornerOf(anchor).add(orientation.getUnitVec3().scale(progress));}
    @Override public StructureTransform restorationAtProgress(float progress){return new StructureTransform(anchor.relative(orientation,Math.clamp(Math.round(progress),0,extensionLength)),0,0,0);}
    public Direction getOrientation(){return orientation;}
    public BlockPos getControllerPos(){return controller;}
    public BlockPos removalOffset(){return BlockPos.ZERO.relative(orientation,initialExtensionProgress);}
    public BlockPos controllerLocal(){return controller.subtract(anchor).relative(orientation,-initialExtensionProgress);}
    @Override public ContraptionType getType(){return AllContraptionTypes.PISTON.value();}
    private static BlockState required(Level level,BlockPos pos)throws AssemblyException{
        if(!level.isInValidBounds(pos))return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        if(!LoadedLevelAccess.isLoaded(level,pos))throw AssemblyException.entitiesNotReady(pos);
        return LoadedLevelAccess.blockState(level,pos);
    }
    private boolean collectExtensions(Level level,BlockPos base)throws AssemblyException{
        extensions.clear();controller=base.immutable();initialExtensionProgress=extensionLength=0;
        var state=required(level,base);if(!isPiston(state)||state.getValue(STATE)==PistonState.MOVING)return false;
        orientation=state.getValue(FACING);var headType=isStickyPiston(state)?PistonType.STICKY:PistonType.DEFAULT;
        if(state.getValue(STATE)==PistonState.EXTENDED){
            for(int step=1;;step++){
                var pos=base.relative(orientation,step);var next=required(level,pos);
                boolean head=isPistonHead(next)&&next.getValue(FACING)==orientation;
                if(!head&&(!isExtensionPole(next)||next.getValue(FACING).getAxis()!=orientation.getAxis()))break;
                if(step>maxAllowedPistonPoles())throw AssemblyException.tooManyPistonPoles();
                extensions.add(new Extension(pos,next.setValue(FACING,orientation)));initialExtensionProgress++;
                if(head)break;
            }
            // An extended controller without its actual final head is a changed structure.
            if(initialExtensionProgress==0||!isPistonHead(extensions.getLast().moving()))throw AssemblyException.structureChanged(base);
        }
        var virtual=initialExtensionProgress==0?AllBlocks.MECHANICAL_PISTON_HEAD.defaultBlockState().setValue(MechanicalPistonHeadBlock.TYPE,headType):AllBlocks.PISTON_EXTENSION_POLE.defaultBlockState();
        extensions.add(new Extension(base,virtual.setValue(FACING,orientation)));
        int behind=0;
        for(int step=1;;step++){
            var pos=base.relative(orientation.getOpposite(),step);var next=required(level,pos);
            if(!isExtensionPole(next)||next.getValue(FACING).getAxis()!=orientation.getAxis())break;
            if(step+initialExtensionProgress>maxAllowedPistonPoles())throw AssemblyException.tooManyPistonPoles();
            extensions.add(new Extension(pos,next.setValue(FACING,orientation)));behind++;
        }
        extensionLength=initialExtensionProgress+behind;if(extensionLength==0)throw AssemblyException.noPistonPoles();
        anchor=base.relative(orientation);
        extensionBounds=new AABB(Vec3.atLowerCornerOf(BlockPos.ZERO.relative(orientation,-1)),Vec3.atLowerCornerOf(BlockPos.ZERO.relative(orientation,-extensionLength-1))).expandTowards(1,1,1);
        return true;
    }
    @Override public boolean assemble(Level level,BlockPos pos)throws AssemblyException{
        if(!collectExtensions(level,pos))return false;
        if(!searchMovedStructure(level,anchor,retract?orientation.getOpposite():orientation))return false;
        bounds=bounds.minmax(extensionBounds);invalidateColliders();startMoving(level);return true;
    }
    @Override protected BlockPos toLocalPos(BlockPos pos){return pos.subtract(anchor).relative(orientation,-initialExtensionProgress);}
    @Override protected boolean isAnchoringBlockAt(BlockPos pos){return extensionBounds.contains(Vec3.atCenterOf(toLocalPos(pos)));}
    @Override protected boolean addToInitialFrontier(Level level,BlockPos pos,Direction forced,Queue<BlockPos> frontier)throws AssemblyException{
        frontier.clear();
        // searchMovedStructure has reset the ordinary capture. Seed through capture() so
        // the private common source maps still record exact real state and BE identity.
        for(var part:extensions){
            required(level,part.source());LoadedEntityAccess.requireReady(level,part.source());
            super.capture(level,part.source());
            super.addBlock(level,part.source(),Pair.of(new StructureBlockInfo(part.source(),part.moving(),null),(BlockEntity)null));
            if(blocks.size()>ContraptionSettings.maxBlocksMoved)throw AssemblyException.structureTooLarge();
        }
        boolean sticky=isStickyPiston(required(level,controller));
        if(retract&&!sticky)return true;
        for(int distance=0;distance<=ChassisSettings.maxRange;distance++){
            if(retract&&distance==1)return true;
            var target=anchor.relative(orientation,initialExtensionProgress+distance);var state=required(level,target);
            if(!BlockMovementChecks.isMovementNecessary(state,level,target))return true;
            if(BlockMovementChecks.isBrittle(state)&&!(state.getBlock() instanceof WoolCarpetBlock))return true;
            if(isPistonHead(state)&&state.getValue(FACING)==forced.getOpposite())return true;
            if(!BlockMovementChecks.isMovementAllowed(state,level,target)){if(retract)return true;throw AssemblyException.unmovableBlock(target,state);}
            if(retract&&state.getPistonPushReaction()==PushReaction.PUSH)return true;
            frontier.add(target.immutable());if(BlockMovementChecks.isNotSupportive(state,orientation))return true;
        }return true;
    }
    /** Exact new common payload/storage hook is documented in patchnotes; sources remain fully validated. */
    @Override protected boolean shouldMoveBlockEntity(Level level,BlockPos local,BlockState state,BlockEntity source){return !local.equals(controllerLocal());}
    @Override protected boolean customBlockRemoval(LevelAccessor accessor,BlockPos pos,BlockState captured){
        if(!pos.equals(controller)||!(accessor instanceof Level level))return false;
        var actual=LoadedLevelAccess.blockState(level,pos);
        if(!isPiston(actual)||actual.getValue(FACING)!=orientation)throw new IllegalStateException("Piston controller changed during world transfer");
        if(!LoadedLevelAccess.setBlockQuiet(level,pos,actual.setValue(STATE,PistonState.MOVING)))throw new IllegalStateException("Piston controller could not enter moving state");
        return true;
    }
    @Override protected boolean customBlockPlacement(LevelAccessor accessor,BlockPos pos,BlockState moving){
        if(!pos.equals(controller)||!(accessor instanceof Level level))return false;
        var actual=LoadedLevelAccess.blockState(level,pos);
        if(isPiston(actual)&&LoadedLevelAccess.blockEntity(level,pos) instanceof MechanicalPistonBlockEntity piston&&(entity==null||piston.isAttachedTo(entity))&&isPistonHead(moving))
            LoadedLevelAccess.setBlockQuiet(level,pos,actual.setValue(STATE,PistonState.RETRACTED));
        // This virtual part came from the controller's own head. Never replace an unrelated
        // world block with it when the original controller was removed during movement.
        return true;
    }
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries,boolean spawn){var tag=super.writeNBT(registries,spawn);tag.putInt("InitialLength",initialExtensionProgress);tag.putInt("ExtensionLength",extensionLength);tag.putInt("Orientation",orientation.get3DDataValue());tag.store("PistonController",BlockPos.CODEC,controller);return tag;}
    @Override public void readNBT(Level level,CompoundTag tag,boolean spawn){
        orientation=Direction.from3DDataValue(tag.getIntOr("Orientation",Direction.NORTH.get3DDataValue()));extensionLength=tag.getIntOr("ExtensionLength",0);initialExtensionProgress=tag.getIntOr("InitialLength",0);
        if(extensionLength<1||initialExtensionProgress<0||initialExtensionProgress>extensionLength)throw new IllegalArgumentException("Invalid piston range");
        super.readNBT(level,tag,spawn);controller=tag.read("PistonController",BlockPos.CODEC).orElse(anchor.relative(orientation.getOpposite()));
        if(!controller.relative(orientation).equals(anchor))throw new IllegalArgumentException("Piston controller/anchor mismatch");
        // Assembly limits may decrease while this native owner is saved. Its actual spindle,
        // rather than the current new-assembly config, bounds the saved owned range.
        if(extensionLength>=blocks.size())throw new IllegalArgumentException("Incomplete saved piston spindle");
        for(int step=1;step<=extensionLength+1;step++){
            var part=blocks.get(BlockPos.ZERO.relative(orientation,-step));
            if(part==null||(step==1?!isPistonHead(part.state()):!isExtensionPole(part.state()))
                ||part.state().getValue(FACING)!=orientation)
                throw new IllegalArgumentException("Invalid saved piston spindle");
        }
        extensionBounds=new AABB(Vec3.atLowerCornerOf(BlockPos.ZERO.relative(orientation,-1)),Vec3.atLowerCornerOf(BlockPos.ZERO.relative(orientation,-extensionLength-1))).expandTowards(1,1,1);invalidateColliders();
    }
}
