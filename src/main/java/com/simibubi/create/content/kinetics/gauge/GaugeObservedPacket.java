package com.simibubi.create.content.kinetics.gauge;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
/** Original native observation request, now validated against real gear, native ray and exact loaded device. */
public record GaugeObservedPacket(BlockPos pos,long nonce) implements CustomPacketPayload {
 public static final Type<GaugeObservedPacket> TYPE=new Type<>(Create.asResource("observer_stressometer"));
 public static final StreamCodec<RegistryFriendlyByteBuf,GaugeObservedPacket> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeLong(p.nonce);},b->new GaugeObservedPacket(b.readBlockPos(),b.readLong()));
 public GaugeObservedPacket(BlockPos pos){this(pos,0);}
 public GaugeObservedPacket{pos=pos.immutable();}
 @Override public Type<GaugeObservedPacket> type(){return TYPE;}
 /** Exact native Entity.pick eye/view/OUTLINE/NONE semantics through a genuine nonloading block view.
  * Unknown cells defer observation; the real native clip never creates a chunk ticket. */
 public static BlockHitResult nativeRay(ServerPlayer player){double range=player.blockInteractionRange();if(!Double.isFinite(range)||range<0)return null;var start=player.getEyePosition(1);var end=start.add(player.getViewVector(1).scale(range));var view=new ObservationView(player.level());var result=view.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));return view.known?result:null;}
 private static final class ObservationView implements net.minecraft.world.level.BlockGetter {
  private final net.minecraft.world.level.Level level;private boolean known=true;
  private ObservationView(net.minecraft.world.level.Level level){this.level=level;}
  @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos){if(!LoadedLevelAccess.isLoaded(level,pos))known=false;return LoadedLevelAccess.blockState(level,pos);}
  @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){if(!LoadedLevelAccess.isLoaded(level,pos))known=false;return LoadedLevelAccess.blockEntity(level,pos);}
  @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
  @Override public int getHeight(){return level.getHeight();}
  @Override public int getMinY(){return level.getMinY();}
 }
 public boolean apply(ServerPlayer player){
  if(!player.isAlive()||player.isSpectator()||!player.mayBuild()||!GogglesItem.isWearingGoggles(player)||!player.isWithinBlockInteractionRange(pos,20)||!(LoadedLevelAccess.blockEntity(player.level(),pos) instanceof StressGaugeBlockEntity gauge))return false;
  var hit=nativeRay(player);if(hit==null||hit.getType()!=HitResult.Type.BLOCK)return false;
  var actual=hit.getBlockPos();if(!actual.equals(pos)){var state=LoadedLevelAccess.blockState(player.level(),actual);if(!(state.getBlock() instanceof IProxyHoveringInformation proxy)||!pos.equals(proxy.getInformationSource(player.level(),actual,state)))return false;}
  gauge.onObserved();return true;
 }
}
