package com.simibubi.create.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.command.PonderActionPayload;
import net.createmod.ponder.command.PonderActionPayload.Action;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.phys.Vec3;

/** Actual registered server dispatcher, literal native players and real emitted S2C payloads. */
public final class PonderCommandGameTests {
    private static List<PonderActionPayload> drain(NativeSurvivalTestPlayer joined){
        var result=new ArrayList<PonderActionPayload>();Object message;
        while((message=joined.channel().readOutbound())!=null)if(message instanceof ClientboundCustomPayloadPacket packet&&packet.payload() instanceof PonderActionPayload ponder)result.add(ponder);
        return result;
    }
    private static CommandSourceStack source(GameTestHelper h,NativeSurvivalTestPlayer player,LevelBasedPermissionSet permissions){return h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withEntity(player.player()).withPermission(permissions);}
    private static void assertAction(GameTestHelper h,NativeSurvivalTestPlayer player,Action expected,Identifier scene){var packets=drain(player);h.assertValueEqual(packets.size(),1,"Actual native command emits one Ponder action for its selected real player");h.assertValueEqual(packets.getFirst(),new PonderActionPayload(expected,scene),"Real S2C action preserves the intended typed operation and exact scene ID");}
    private static void denied(GameTestHelper h,CommandSourceStack source,String text){
        boolean denied=false;try{h.getLevel().getServer().getCommands().getDispatcher().execute(text,source);}catch(CommandSyntaxException expected){denied=true;}h.assertTrue(denied,"Actual server dispatcher must reject inaccessible target command: "+text);
    }
    @GameTest(structure="create_test:reservoir")
    public void realPonderCommandTreeKeepsSelfZeroTargetTwoAndMissingSceneServerDispatch(GameTestHelper h)throws Exception{
        var server=h.getLevel().getServer();var dispatcher=server.getCommands().getDispatcher();var root=dispatcher.getRoot().getChild("ponder");h.assertTrue(root!=null,"Actual Fabric bootstrap installs /ponder in the genuine native server dispatcher");
        for(var child:List.of("index","tags","reload","scene"))h.assertTrue(root.getChild(child)!=null,"Pinned Ponder branch is registered: "+child);
        h.assertTrue(root.getChild("scene").getChild("targets")!=null,"Pinned scene target selector is registered");
        var at=Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(3,4,3)));
        try(var self=NativeSurvivalTestPlayer.join(h,at);var target=NativeSurvivalTestPlayer.join(h,at.add(3,0,0))){
            drain(self);drain(target);var zero=source(h,self,LevelBasedPermissionSet.ALL);var one=source(h,self,LevelBasedPermissionSet.MODERATOR);var two=source(h,self,LevelBasedPermissionSet.GAMEMASTER);
            h.assertTrue(root.canUse(zero)&&!root.getChild("scene").getChild("targets").canUse(zero)&&!root.getChild("scene").getChild("targets").canUse(one)&&root.getChild("scene").getChild("targets").canUse(two),"Native permission sets preserve self level0 and target threshold exactly2");
            for(var text:List.of("ponder","ponder tags","ponder index","ponder reload")){
                h.assertValueEqual(dispatcher.execute(text,zero),1,"Level0 self command succeeds through actual dispatcher");assertAction(h,self,text.endsWith("reload")?Action.RELOAD:text.endsWith("index")?Action.INDEX:Action.TAGS,null);h.assertTrue(drain(target).isEmpty(),"Self actions never target another real connected player");
            }
            var subject=Identifier.fromNamespaceAndPath("create","cogwheel");h.assertValueEqual(dispatcher.execute("ponder "+subject,zero),1,"Level0 can open its own exact component");assertAction(h,self,Action.SCENE,subject);
            var missing=Identifier.fromNamespaceAndPath("create_test","missing_command_scene");h.assertValueEqual(dispatcher.execute("ponder "+missing,zero),1,"Server dispatch does not require a client-only scene registry");assertAction(h,self,Action.SCENE,missing);
            var targetCommand="ponder "+subject+" "+target.player().getGameProfile().name();denied(h,zero,targetCommand);denied(h,one,targetCommand);h.assertTrue(drain(self).isEmpty()&&drain(target).isEmpty(),"Rejected level0/1 target attempts emit no action");
            h.assertValueEqual(dispatcher.execute(targetCommand,two),1,"Level2 selects another genuine connected player");assertAction(h,target,Action.SCENE,subject);h.assertTrue(drain(self).isEmpty(),"Targeted action leaves operator's own screen untouched");
            h.assertValueEqual(dispatcher.execute("ponder ponder:index "+target.player().getGameProfile().name(),two),1,"Namespaced reserved source scene target alias succeeds");assertAction(h,target,Action.INDEX,null);
            var console=server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);denied(h,console,"ponder");h.assertValueEqual(dispatcher.execute(targetCommand,console),1,"Console retains source ability to target a player without opening its own screen");assertAction(h,target,Action.SCENE,subject);
            var fake=net.fabricmc.fabric.api.entity.FakePlayer.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ponder_fake"));h.assertValueEqual(dispatcher.execute("ponder "+subject,server.createCommandSourceStack().withEntity(fake).withPermission(LevelBasedPermissionSet.ALL)),1,"Source fake-player open is ignored successfully without inventing a client connection");
        }
        h.succeed();
    }
    @GameTest
    public void boundedTypedPonderPayloadPreservesOnlySourceActionsAndRejectsInvalidSceneShape(GameTestHelper h){
        var actions=List.of(PonderActionPayload.open("index"),PonderActionPayload.open("ponder:index"),PonderActionPayload.open("ponder:tags"),PonderActionPayload.open("create:cogwheel"),PonderActionPayload.reload());
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            for(var action:actions){buffer.clear();PonderActionPayload.CODEC.encode(buffer,action);h.assertValueEqual(PonderActionPayload.CODEC.decode(buffer),action,"Actual registry-friendly typed S2C codec preserves each source operation");h.assertValueEqual(buffer.readableBytes(),0,"Typed decoder consumes the complete single action");}
            buffer.clear();buffer.writeVarInt(99);boolean invalid=false;try{PonderActionPayload.CODEC.decode(buffer);}catch(RuntimeException expected){invalid=true;}h.assertTrue(invalid,"Wire decoder rejects action ordinal outside the four permitted Ponder actions");
            boolean missing=false;try{new PonderActionPayload(Action.SCENE,null);}catch(IllegalArgumentException expected){missing=true;}h.assertTrue(missing,"SCENE cannot omit its typed component identifier");
            boolean extra=false;try{new PonderActionPayload(Action.RELOAD,Identifier.fromNamespaceAndPath("create","cogwheel"));}catch(IllegalArgumentException expected){extra=true;}h.assertTrue(extra,"RELOAD cannot smuggle an extra scene/action string");
        }finally{buffer.release();}h.succeed();
    }
}
