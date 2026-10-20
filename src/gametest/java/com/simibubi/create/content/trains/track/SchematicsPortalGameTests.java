package com.simibubi.create.content.trains.track;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.cannon.*;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.test.SchematicBoundaryFixtures;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/** Real vanilla frames, item-paid native paired portal rails and source placement save guards. UNRUN. */
public final class SchematicsPortalGameTests {
    private static final AtomicInteger NEXT=new AtomicInteger();
    private static void prepare(ServerLevel level,BlockPos center){var chunk=ChunkPos.containing(center);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(chunk.x()+x,chunk.z()+z);level.waitForEntities(chunk,1);}
    private static void frame(ServerLevel level,BlockPos corner){
        for(var pos:BlockPos.betweenClosed(corner.offset(-4,-1,-4),corner.offset(4,5,4)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        for(int u=-1;u<=2;u++)for(int y=-1;y<=3;y++)if(u==-1||u==2||y==-1||y==3)level.setBlock(corner.south(u).above(y),Blocks.OBSIDIAN.defaultBlockState(),Block.UPDATE_ALL);
        for(int u=0;u<2;u++)for(int y=0;y<3;y++)level.setBlock(corner.south(u).above(y),Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,Direction.Axis.Z),Block.UPDATE_ALL);
    }
    private static long resync(TrackBlockEntity owner){try{var field=TrackBlockEntity.class.getDeclaredField("portalResyncAt");field.setAccessible(true);return field.getLong(owner);}catch(ReflectiveOperationException failure){throw new AssertionError("Actual native portal resync state changed",failure);}}
    @GameTest(structure="create_test:reservoir") public void actualPairedVanillaPortalOwnersAndResyncSaveSurviveRejectedPaidSchematic(GameTestHelper h){
        int base=3_000_000+NEXT.incrementAndGet()*4096;var source=h.getLevel().getServer().overworld();var other=source.getServer().getLevel(Level.NETHER);h.assertTrue(other!=null,"Genuine native Nether dimension exists");
        var portal=new BlockPos(base+8,100,8);var remote=new BlockPos(Math.floorDiv(base,8)+1,100,1);var first=portal.west();prepare(source,portal);prepare(other,remote);frame(source,portal);frame(other,remote);
        source.setBlock(first.below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(first).add(-2,1,0));player.setYRot(-90);player.setXRot(0);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.TRACK,8));
        h.assertTrue(player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(first.below()).add(0,.5,0),Direction.UP,first.below(),false))).consumesAction()&&player.getMainHandItem().getCount()==7,"Actual native Track item publishes and pays the physical source portal approach");
        TrackPortalConnection.tryConnect(source,first,source.getBlockState(first));var owner=(TrackBlockEntity)source.getBlockEntity(first);h.assertTrue(owner!=null&&owner.boundLocation!=null&&owner.getPortalBinding()!=null,"Actual native portal provider pairs genuine physical rails");
        var peer=(TrackBlockEntity)other.getBlockEntity(owner.boundLocation.getSecond());h.assertTrue(peer!=null&&owner.getPortalBinding().equals(peer.getPortalBinding())&&peer.boundLocation.getSecond().equals(first),"Literal native counterpart owns the same reciprocal portal binding");
        var saved=owner.saveWithFullMetadata(source.registryAccess());var peerSave=peer.saveWithFullMetadata(other.registryAccess());long sourceResync=resync(owner),targetResync=resync(peer);var sourceState=source.getBlockState(first);var targetState=other.getBlockState(peer.getBlockPos());
        var stock=new SchematicInventory(1,(slot,item)->true,slot->64,()->{},p->false);var original=new ItemStack(Items.STONE,41);original.set(DataComponents.CUSTOM_NAME,Component.literal("Original schematic refused by actual paired portal"));stock.setItem(0,original);
        var launch=new LaunchedItem.ForBlockState(first.west(3),first,new ItemStack(Items.STONE),Blocks.STONE.defaultBlockState(),null);
        try(var tx=Transaction.openOuter()){var payment=SchematicPayment.collect(ItemRequirement.of(Blocks.STONE.defaultBlockState(),null),List.of(stock.storage),tx);h.assertTrue(payment.complete(),"Real source stone is reserved from actual native Fabric stock");launch.paid(payment.receipt());tx.commit();}launch.totalTicks=launch.ticksRemaining=0;
        var tickets=SchematicBoundaryFixtures.tickets(source);var remoteTickets=SchematicBoundaryFixtures.tickets(other);h.assertFalse(launch.update(source),"Actual paired portal ownership refuses paid schematic replacement");
        var restored=LaunchedItem.fromNBT(launch.serializeNBT(source.registryAccess()),source.registryAccess(),source.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK));h.assertFalse(restored.update(source),"Saved paid source launch still refuses the actual live portal owner");
        h.assertTrue(source.getBlockEntity(first)==owner&&other.getBlockEntity(peer.getBlockPos())==peer&&source.getBlockState(first)==sourceState&&other.getBlockState(peer.getBlockPos())==targetState,"Both literal native portal owners and actual states remain unchanged");
        h.assertTrue(owner.saveWithFullMetadata(source.registryAccess()).equals(saved)&&peer.saveWithFullMetadata(other.registryAccess()).equals(peerSave)&&resync(owner)==sourceResync&&resync(peer)==targetResync,"Native paired portal saves, binding and pending resync clocks remain exact");
        h.assertTrue(restored.paidOriginals().size()==1&&ItemStack.matches(restored.paidOriginals().getFirst(),original.copyWithCount(1))&&ItemStack.matches(stock.getItem(0),original.copyWithCount(40)),"Exact original reserved material and source tail survive refusal/save/retry");h.assertValueEqual(SchematicBoundaryFixtures.tickets(source),tickets,"Refused source placement adds no native source ticket");h.assertValueEqual(SchematicBoundaryFixtures.tickets(other),remoteTickets,"Refused source placement adds no native counterpart ticket");h.succeed();
    }
}
