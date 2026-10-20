package com.simibubi.create.test;

import com.simibubi.create.content.decoration.RoofBlockCTBehaviour;
import com.simibubi.create.content.decoration.copycat.*;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.decoration.palettes.*;
import com.simibubi.create.content.kinetics.NativeKineticsCTModel;
import com.simibubi.create.foundation.block.connected.OmnidirectionalCT;
import java.util.Objects;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Authored read-only checks of an ACTUAL native view supplied by a future caller.
 * No replacement world/BE/attachment, material injection, admission change, scene or automatic route.
 * No caller is registered by this package. Compilation is not runtime or visual proof. */
public final class CopycatCTBoundaryChecks {
 private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static BlockState material(BlockAndTintGetter view,BlockPos pos){var raw=view.getBlockState(pos);return raw.getBlock() instanceof CopycatBlock?CopycatModel.getMaterial(view,pos):raw;}
 private static void sideIsolation(BlockAndTintGetter view,BlockPos pos){for(var offset:new Direction[]{Direction.WEST,Direction.UP,Direction.DOWN})require(view.getBlockState(pos.relative(offset)).isAir(),"Actual source side fixture requires empty remaining neighbors");}
 private static BlockState pillar(BlockAndTintGetter view,BlockPos pos){var state=material(view,pos);require(state.getBlock() instanceof ConnectedPillarBlock&&state.getValue(LayeredBlock.AXIS)==Direction.Axis.Y,"Actual source Y pillar material required");return state;}
 private static void noHorizontalMasks(BlockState state){for(var side:Direction.Plane.HORIZONTAL)require(!ConnectedPillarBlock.getConnection(state,side),"Actual source fixture must have unset native pillar side masks");}

 /** Source getAppearance must return the wrapper on an ignored side, material on an admitted side. */
 public static void actualAppearanceHonorsIgnoredSideBeforeMaterialLookup(BlockAndTintGetter view,BlockPos copycatPos,BlockPos queryPos,Direction face){
  var host=view.getBlockState(copycatPos);require(host.getBlock() instanceof CopycatBlock,"Actual Copycat owner required");var block=(CopycatBlock)host.getBlock();var data=view.getBlockEntityRenderData(copycatPos);require(data instanceof CopycatBlockEntity.RenderData,"Actual immutable native material attachment required");var original=((CopycatBlockEntity.RenderData)data).material();var ignored=block.isIgnoredConnectivitySide(view,host,face,copycatPos,queryPos);var appearance=host.getAppearance(view,copycatPos,face,view.getBlockState(queryPos),queryPos);require(appearance==(ignored?host:original),"Ignored-side barrier precedes actual attachment material");require(((CopycatBlockEntity.RenderData)data).material()==original,"Read-only appearance must retain the original immutable attachment");
 }

 /** Exercise the real source directional predicate and production filtered view, with no unmasking. */
 public static void actualDirectionalFilterKeepsDeniedStateFluidAndAttachmentInvisible(BlockAndTintGetter view,BlockPos copycatPos,BlockPos target){
  var host=view.getBlockState(copycatPos);require(host.getBlock() instanceof CopycatBlock,"Actual Copycat source required");var block=(CopycatBlock)host.getBlock();var origin=copycatPos.immutable();var filtered=new FilteredBlockAndTintGetter(view,p->block.canConnectTexturesToward(view,origin,p,host));boolean admitted=block.canConnectTexturesToward(view,origin,target,host);
  if(admitted){require(filtered.getBlockState(target)==view.getBlockState(target)&&Objects.equals(filtered.getBlockEntityRenderData(target),view.getBlockEntityRenderData(target)),"Actual permitted state and immutable attachment are forwarded");}
  else require(filtered.getBlockState(target).isAir()&&filtered.getFluidState(target).isEmpty()&&filtered.getBlockEntityRenderData(target)==null&&filtered.getBlockEntity(target)==null,"Source denial remains AIR/empty/null; no barrier or attachment bypass");
 }

 /** A genuine copied Y pillar may connect east despite the material's four false side masks. */
 public static void copiedOriginBypassesPillarMasksAfterSameBlockAndAxisGuards(BlockAndTintGetter view,BlockPos pos){
  sideIsolation(view,pos);require(view.getBlockState(pos).getBlock() instanceof CopycatBlock,"Actual copied origin required");var state=pillar(view,pos);var other=OmnidirectionalCT.appearance(view,view.getBlockState(pos),Direction.NORTH,pos,pos.east());require(other.getBlock()==state.getBlock()&&other.getValue(LayeredBlock.AXIS)==Direction.Axis.Y,"Actual visible same-block/same-axis neighbor required");noHorizontalMasks(state);noHorizontalMasks(other);require(PaletteCTModel.textureIndex(view,pos,state,Direction.NORTH)==15,"Pinned copycat origin bypass produces rectangle tile15");
 }

 /** The same ordinary native material states retain their pillar-mask barrier. */
 public static void ordinaryPillarsKeepTheirOriginalMaskBoundary(BlockAndTintGetter view,BlockPos pos){
  sideIsolation(view,pos);require(!(view.getBlockState(pos).getBlock() instanceof CopycatBlock)&&!(view.getBlockState(pos.east()).getBlock() instanceof CopycatBlock),"Actual ordinary endpoints required");var state=pillar(view,pos);var other=pillar(view,pos.east());require(state.getBlock()==other.getBlock(),"Actual same source family required");noHorizontalMasks(state);noHorizontalMasks(other);require(PaletteCTModel.textureIndex(view,pos,state,Direction.NORTH)==12,"Ordinary pillars retain source mask rejection, tile12");
 }

 /** A Copycat endpoint cannot waive the source same-axis test. */
 public static void differentAxisRejectsBeforeTheCopycatBypass(BlockAndTintGetter view,BlockPos pos){
  sideIsolation(view,pos);require(view.getBlockState(pos).getBlock() instanceof CopycatBlock,"Actual copied source required");var state=pillar(view,pos);var other=OmnidirectionalCT.appearance(view,view.getBlockState(pos),Direction.NORTH,pos,pos.east());require(other.getBlock()==state.getBlock()&&other.getValue(LayeredBlock.AXIS)!=Direction.Axis.Y,"Actual visible other-axis source neighbor required");noHorizontalMasks(state);require(PaletteCTModel.textureIndex(view,pos,state,Direction.NORTH)==12,"Source axis rejection must precede copied-endpoint mask bypass");
 }

 /** Use a real ordinary Y pillar and east Copycat facing DOWN, with an actual X-axis full blocker above it.
  * A copied origin's own directional filter can hide above neighbors; do not replace that filter here. */
 public static void rotatedSameBlockForwardBlockerStillPrecedesCopiedEndpointBypass(BlockAndTintGetter view,BlockPos pos){
  require(!(view.getBlockState(pos).getBlock() instanceof CopycatBlock)&&view.getBlockState(pos.east()).getBlock() instanceof CopycatBlock,"Actual ordinary origin and copied target required");var state=pillar(view,pos);var other=OmnidirectionalCT.appearance(view,view.getBlockState(pos),Direction.UP,pos,pos.east());require(other.getBlock()==state.getBlock()&&other.getValue(LayeredBlock.AXIS)==Direction.Axis.Y,"Actual cap-visible copied source target required");var blockerPos=pos.east().above();var blocker=view.getBlockState(blockerPos);require(blocker.getBlock()==state.getBlock()&&blocker.getValue(LayeredBlock.AXIS)!=Direction.Axis.Y&&Block.isFaceFull(blocker.getShape(view,blockerPos),Direction.DOWN),"Actual rotated full source blocker required");for(var side:new Direction[]{Direction.WEST,Direction.NORTH,Direction.SOUTH})require(view.getBlockState(pos.relative(side)).isAir(),"Remaining actual cap neighbors must be empty");require(PaletteCTModel.textureIndex(view,pos,state,Direction.UP)==0,"Source Base6-arg same-block blocker rejects before Copycat bypass, even with different blocker axis");
 }

 /** Actual full copper roofs north/south, west AIR, east downward Copycat with the same full roof material. */
 public static void roofTargetAppearanceIsResolvedBeforeHeightAndSoundConnectivity(BlockAndTintGetter view,BlockPos pos){
  require(view.getBlockState(pos.east()).getBlock() instanceof CopycatBlock&&view.getBlockState(pos.west()).isAir(),"Actual copied roof target and absent west neighbor required");var state=material(view,pos);require(state.getSoundType()==net.minecraft.world.level.block.SoundType.COPPER,"Actual native copper source required");var appearance=OmnidirectionalCT.appearance(view,view.getBlockState(pos),Direction.UP,pos,pos.east());require(appearance.getBlock()==state.getBlock(),"Actual permitted copied cap appearance required");for(var side:new Direction[]{Direction.NORTH,Direction.SOUTH})require(view.getBlockState(pos.relative(side)).is(state.getBlock())&&view.getBlockState(pos.relative(side).above()).isAir(),"Actual two source roof neighbors required");require(view.getBlockState(pos.east().above()).isAir(),"No actual forward high block may substitute for target appearance");require(RoofBlockCTBehaviour.textureIndex(view,pos,state)==4,"Source roof target appearance makes the original left-drop tile4");
 }

 /** Existing actual encased casing identity/predicate lookup must see the same filtered appearance. */
 public static void encasedContextUsesTheActualCasingIdentityWithoutAdmissionOverrides(BlockAndTintGetter view,BlockPos pos){
  sideIsolation(view,pos);require(view.getBlockState(pos).getBlock() instanceof CopycatBlock,"Actual copied casing source required");var state=material(view,pos);var entry=NativeKineticsCTModel.CASING_CONNECTIVITY.get(state);require(entry!=null&&entry.isSideValid(state,Direction.NORTH),"Actual registered source casing descriptor required");var target=OmnidirectionalCT.appearance(view,view.getBlockState(pos),Direction.NORTH,pos,pos.east());var other=NativeKineticsCTModel.CASING_CONNECTIVITY.get(target);require(other!=null&&other.getCasing()==entry.getCasing()&&other.isSideValid(target,Direction.NORTH),"Actual allowed neighboring casing identity required");require(view.getBlockState(pos.east().north()).isAir(),"Actual source forward-blocker location must be empty");var behavior=new EncasedCTBehaviour(NativeKineticsCTModel.CASING_CONNECTIVITY,entry.getCasing());require(behavior.buildContext(view,pos,state,Direction.NORTH).textureIndex()==8,"Existing native casing appearance produces exact source east-neighbor tile8");
 }
 private CopycatCTBoundaryChecks(){}
}
