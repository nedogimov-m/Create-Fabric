package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.transmission.sequencer.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/** Ordinary native editor input -> actual two-port solver -> moving payload; real midphase unload/rejoin. UNRUN. */
final class SequencedGearshiftRenderingTest {
    private static final BlockPos S=new BlockPos(9007,5,5),MOTOR=S.west(2),B=S.east(),PAYLOAD=B.east(),POWER=S.south();
    private record Saved(SequencePhase phase,int timer,double remaining,UUID owner){}
    private record Replacement(SequencePhase phase,float angle,double remaining,UUID owner){}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 9007.5 5 8.5");
        context.waitFor(client->Math.abs(client.player.getX()-9007.5)<.1&&client.level.isLoaded(PAYLOAD),500);
        server.runCommand("fill 9000 4 0 9017 4 13 minecraft:smooth_quartz");
        block(world,MOTOR,"create:creative_motor[facing=east]");block(world,S.west(),"create:shaft[axis=x]");
        block(world,S,"create:sequenced_gearshift[axis=x,vertical=false,state=0]");block(world,B,"create:mechanical_bearing[facing=east]");block(world,PAYLOAD,"minecraft:barrel");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((KineticBlockEntity)LoadedLevelAccess.blockEntity(level,MOTOR)).setGeneratedSpeed(4);bearing(level).setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);});
        waitServer(context,world,level->seq(level)!=null&&seq(level).getSpeed()==4&&seq(level).getPortSpeed(Direction.EAST)==0,200,"Idle sequencer must keep actual input4 and disconnect output");
        server.runCommand("gamemode creative @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
        context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().isEmpty(),200);
        open(context);clickType(context,1);
        // Both identical90 phases use2x. All edits go through native widgets and the real config packet.
        for(int row=0;row<2;row++)for(int click=0;click<3;click++)clickModifier(context,row);
        var firstProgram=List.of(new Instruction(SequencerInstructions.TURN_ANGLE,InstructionSpeedModifiers.FORWARD_FAST,90),new Instruction(SequencerInstructions.TURN_ANGLE,InstructionSpeedModifiers.FORWARD_FAST,90),new Instruction(SequencerInstructions.END));
        verifyEditor(context,"two identical90degree2x phases before submit",firstProgram);
        screenshot(context,"create-vulkan-sequencer-native-two-identical-angle-instructions");context.clickScreenButton("✓");
        context.waitFor(client->client.gui.screen()==null,200);
        waitConfiguration(context,world,firstProgram,"Real native editor did not commit both2x phases");
        block(world,POWER,"minecraft:redstone_block");
        waitServer(context,world,level->!seq(level).isIdle()&&seq(level).getSpeed()==4&&seq(level).getSourceFacing()==Direction.WEST&&seq(level).getPortSpeed(Direction.EAST)==8&&bearing(level).isRunning(),200,"Actual provenance must choose input4 before output8");
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,S)instanceof SequencedGearshiftBlockEntity entity&&entity.getPortSpeed(Direction.EAST)==8,200);
        verifyHalves(context,4,8);look(context,new Vec3(9008.5,6,5.5));screenshot(context,"create-vulkan-sequencer-two-native-shaft-halves-and-real-bearing");
        waitServer(context,world,level->seq(level).isIdle()&&bearing(level).getMovedContraption()!=null&&Math.abs(bearing(level).getMovedContraption().getAngle(1)-180)<.001,300,"Equal RPM/context phases did not reset the real consumer to reach180degrees");
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,S)instanceof SequencedGearshiftBlockEntity entity&&entity.getPortSpeed(Direction.EAST)==0,200);verifyHalves(context,4,0);
        screenshot(context,"create-vulkan-sequencer-ended-output-stopped-input-still-powered");
        block(world,POWER,"minecraft:air");waitServer(context,world,level->seq(level).getBlockState().getValue(SequencedGearshiftBlock.STATE)==0,100,"Falling redstone must clear the retained end state");
        // Configure a long real angle phase so both owner chunks can actually unload before it ends.
        open(context);editFirstValue(context,"360");for(int click=0;click<3;click++)clickType(context,1);clickModifier(context,0);
        var longProgram=List.of(new Instruction(SequencerInstructions.TURN_ANGLE,InstructionSpeedModifiers.FORWARD,360),new Instruction(SequencerInstructions.AWAIT),new Instruction(SequencerInstructions.END));
        verifyEditor(context,"360degree/FORWARD/AWAIT after all widget rebuilds before submit",longProgram);
        screenshot(context,"create-vulkan-sequencer-native-long-angle-and-await-editor");context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,200);
        waitConfiguration(context,world,longProgram,"Ordinary editor values/type/modifier did not persist");
        server.runOnServer(ignored->((KineticBlockEntity)LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),MOTOR)).setGeneratedSpeed(1));block(world,POWER,"minecraft:redstone_block");
        waitServer(context,world,level->seq(level).getInstructionIndex()==0&&bearing(level).getSequencedAngleLimit()>0&&bearing(level).getSequencedAngleLimit()<360,200,"Long phase did not consume real native motion before unload");
        var seqRef=new AtomicReference<SequencedGearshiftBlockEntity>();var bearingRef=new AtomicReference<MechanicalBearingBlockEntity>();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();seqRef.set(seq(level));bearingRef.set(bearing(level));});
        var beforeReload=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState()));
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();
        context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState())==beforeReload)throw new AssertionError("Sequencer kept stale baked block models");});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 10000 14 10000");
        waitServer(context,world,level->level.getChunkSource().getChunkNow(S.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(B.getX()>>4,0)==null,800,"Actual sequencer and bearing chunks did not unload");
        var saved=new AtomicReference<Saved>();server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var old=seqRef.get();var consumer=bearingRef.get();var data=consumer.saveWithoutMetadata(level.registryAccess());
            var phase=old.getSequencePhase();double remaining=data.getDoubleOr("SequenceLimitRemaining",data.getDoubleOr("SequencedAngleLimit",-1));
            var owner=data.read("MovedContraption",UUIDUtil.CODEC).orElseThrow();
            if(phase==null||remaining<=0||remaining>=360||old.isIdle())throw new AssertionError("Midphase unload lost its consumed travel or active instruction");saved.set(new Saved(phase,old.getTimer(),remaining,owner));
        });
        server.runCommand("tp @p 9007.5 5 8.5");
        waitServer(context,world,level->{var sequencer=seq(level);var consumer=bearing(level);var snapshot=saved.get();return sequencer!=null&&consumer!=null&&snapshot.phase.equals(sequencer.getSequencePhase())&&snapshot.phase.equals(consumer.getSequencePhase())
            &&sequencer.getTimer()>=snapshot.timer&&consumer.getMovedContraption()!=null&&snapshot.owner.equals(consumer.getMovedContraption().getUUID())&&consumer.getSequencedAngleLimit()>0&&consumer.getSequencedAngleLimit()<=snapshot.remaining+.001;},600,"True chunk return replayed a phase, reset remaining angle, or replaced the physical owner");
        context.waitFor(client->client.level.isLoaded(S)&&LoadedLevelAccess.blockEntity(client.level,S)instanceof SequencedGearshiftBlockEntity entity&&entity.getSpeed()==1&&entity.getPortSpeed(Direction.EAST)==1,300);
        verifyHalves(context,1,1);look(context,Vec3.atCenterOf(B));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-sequencer-midphase-two-chunk-return-same-owner-and-budget");
        server.runOnServer(ignored->((KineticBlockEntity)LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),MOTOR)).setGeneratedSpeed(0));
        waitServer(context,world,level->seq(level).isIdle()&&bearing(level).getSpeed()==0&&bearing(level).sequenceContext==null,200,"Removing real input failed to abort sequence and clear output context");
        // A physical replacement must not inherit the previous controller's consumed phase-one receipt.
        // First create a new controller and consume part of its default first90degree command.
        block(world,POWER,"minecraft:air");block(world,S,"minecraft:air");
        waitServer(context,world,level->seq(level)==null&&bearing(level).getSpeed()==0&&bearing(level).getMovedContraption()!=null&&saved.get().owner.equals(bearing(level).getMovedContraption().getUUID()),100,"Breaking original sequencer replaced the retained bearing owner");
        block(world,S,"create:sequenced_gearshift[axis=x,vertical=false,state=0]");server.runOnServer(ignored->((KineticBlockEntity)LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),MOTOR)).setGeneratedSpeed(4));
        waitServer(context,world,level->seq(level)!=null&&seq(level).isIdle()&&seq(level).getSpeed()==4,150,"New native controller did not acquire real input4");
        server.runCommand("gamemode creative @p");context.waitFor(client->!client.player.isSpectator(),150);var defaultProgram=List.of(new Instruction(SequencerInstructions.TURN_ANGLE,90),new Instruction(SequencerInstructions.END));
        open(context);verifyEditor(context,"first replacement native default90 program",defaultProgram);context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,150);waitConfiguration(context,world,defaultProgram,"First replacement GUI did not retain actual default90 program");
        block(world,POWER,"minecraft:redstone_block");waitServer(context,world,level->seq(level).getInstructionIndex()==0&&seq(level).getSequencePhase()!=null&&seq(level).getSequencePhase().epoch()==1&&bearing(level).getSequencedAngleLimit()>0&&bearing(level).getSequencedAngleLimit()<60,150,"First replacement did not consume part of its actual phase-one90degree budget");
        var replacedPhase=new AtomicReference<SequencePhase>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();replacedPhase.set(seq(level).getSequencePhase());((KineticBlockEntity)LoadedLevelAccess.blockEntity(level,MOTOR)).setGeneratedSpeed(0);});
        waitServer(context,world,level->seq(level).isIdle()&&bearing(level).getSpeed()==0&&bearing(level).getSequencePhase()==null,150,"Stopping real source did not leave a dormant first-phase receipt");
        // The END-tick solver sets RPM0 and rounds the bearing's stored angle. Its native BE ticker
        // publishes that final stopped pose to the real owner next tick; sample only after it settles.
        var stoppedTick=new AtomicLong();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();stoppedTick.set(level.getGameTime());Create.LOGGER.info("Sequencer stop before native pose settle: {}",replacementDiagnostics(level,null));});
        waitServer(context,world,level->{var consumer=bearing(level);var owner=consumer.getMovedContraption();float stored=consumer.saveWithoutMetadata(level.registryAccess()).getFloatOr("Angle",Float.NaN);return level.getGameTime()>=stoppedTick.get()+2&&consumer.getSpeed()==0&&consumer.getSequencePhase()==null&&owner!=null&&saved.get().owner.equals(owner.getUUID())&&Float.compare(stored,owner.getAngle(1))==0;},100,"Real stopped bearing did not publish its settled native pose before replacement baseline");
        var replacement=new AtomicReference<Replacement>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var consumer=bearing(level);var owner=consumer.getMovedContraption();var data=consumer.saveWithoutMetadata(level.registryAccess());double remaining=data.getDoubleOr("SequenceLimitRemaining",-1);if(owner==null||!saved.get().owner.equals(owner.getUUID())||remaining<=0||remaining>=90||Float.compare(data.getFloatOr("Angle",Float.NaN),owner.getAngle(1))!=0)throw new AssertionError("Midphase settled real stop lost original owner/receipt/pose: "+replacementDiagnostics(level,null));replacement.set(new Replacement(replacedPhase.get(),owner.getAngle(1),remaining,owner.getUUID()));Create.LOGGER.info("Sequencer before real same-position replacement: {}, settled={}",replacement.get(),replacementDiagnostics(level,replacement.get()));});
        block(world,POWER,"minecraft:air");block(world,S,"minecraft:air");waitServer(context,world,level->seq(level)==null&&bearing(level).getSpeed()==0,100,"Actual phase-one sequencer did not break");block(world,S,"create:sequenced_gearshift[axis=x,vertical=false,state=0]");
        server.runOnServer(ignored->((KineticBlockEntity)LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),MOTOR)).setGeneratedSpeed(4));waitServer(context,world,level->seq(level)!=null&&seq(level).getSpeed()==4&&seq(level).isIdle(),150,"Second actual replacement failed to get live input");
        open(context);verifyEditor(context,"second replacement native fresh90 program",defaultProgram);context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,150);waitConfiguration(context,world,defaultProgram,"Second replacement native default program did not persist");
        block(world,POWER,"minecraft:redstone_block");waitServer(context,world,level->{var phase=seq(level).getSequencePhase();var before=replacement.get();return phase!=null&&phase.origin().equals(before.phase.origin())&&phase.epoch()==1&&before.phase.epoch()==1&&!phase.generation().equals(before.phase.generation())&&bearing(level).getMovedContraption()!=null&&before.owner.equals(bearing(level).getMovedContraption().getUUID());},150,"Same-position new first phase reused the old controller generation/owner");
        waitReplacementCompletion(context,world,replacement.get());
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();Create.LOGGER.info("Sequencer real replacement completed: before={}, newGeneration={}, finalAngle={}, expected={}",replacement.get(),seq(level).getSequenceGeneration(),bearing(level).getMovedContraption().getAngle(1),(replacement.get().angle+90)%360);});
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,S)instanceof SequencedGearshiftBlockEntity entity&&entity.getSpeed()==4&&entity.getPortSpeed(Direction.EAST)==0,150);verifyHalves(context,4,0);look(context,Vec3.atCenterOf(B));screenshot(context,"create-vulkan-sequencer-real-same-position-replacement-fresh-ninety-same-owner");
        verifyModels(context);server.runCommand("gamemode spectator @p");
    }
    private static void verifyModels(ClientGameTestContext context){context.runOnClient(client->{var set=client.getModelManager().getBlockStateModelSet();for(var axis:new Direction.Axis[]{Direction.Axis.X,Direction.Axis.Z})for(boolean vertical:new boolean[]{false,true})for(int phase=0;phase<=5;phase++){
        var state=AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS,axis).setValue(SequencedGearshiftBlock.VERTICAL,vertical).setValue(SequencedGearshiftBlock.STATE,phase);
        if(set.get(state)==set.missingModel())throw new AssertionError("Missing native sequencer state model: "+state);
    }});}
    private static void verifyHalves(ClientGameTestContext context,float input,float output){context.runOnClient(client->{
        var entity=(SequencedGearshiftBlockEntity)LoadedLevelAccess.blockEntity(client.level,S);if(entity==null||entity.getSpeed()!=input||entity.getPortSpeed(Direction.WEST)!=input||entity.getPortSpeed(Direction.EAST)!=output)throw new AssertionError("Native synchronized input/output port RPM mismatch");
        var state=(KineticRenderState)client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,false);
        if(state==null||state.ports.size()!=2||state.ports.stream().anyMatch(port->!(port.model()instanceof PartialModelRenderState partial)||partial.mesh==null||partial.mesh.size()==0))throw new AssertionError("Native sequencer must extract two genuine shaft-half meshes");
    });}
    private static void open(ClientGameTestContext context){look(context,Vec3.atCenterOf(S));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(S),150);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen()instanceof SequencedGearshiftScreen&&client.player.containerMenu instanceof SequencedGearshiftMenu,200);}
    private static void clickType(ClientGameTestContext context,int row){clickWidget(context,row,57,false);}
    private static void clickModifier(ClientGameTestContext context,int row){clickWidget(context,row,27,false);}
    private static void editFirstValue(ClientGameTestContext context,String text){
        clickWidget(context,0,29,true);
        context.runOnClient(client->{var screen=(SequencedGearshiftScreen)client.gui.screen();var field=firstValueField(screen);if(!field.isFocused())throw new AssertionError("Native value click did not focus field: "+editorDiagnostics(screen));Create.LOGGER.info("Sequencer editor before native value replacement: {}",editorDiagnostics(screen));});
        // Fabric TestInput emits KeyEvent.modifiers=0 even when holdControl holds Command on Mac.
        // Use ordinary unmodified editing keys, preserving actual focus/widget/character routing.
        context.getInput().pressKey(InputConstants.KEY_END);for(int i=0;i<3;i++)context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
        context.runOnClient(client->{var screen=(SequencedGearshiftScreen)client.gui.screen();if(!firstValueField(screen).getValue().isEmpty())throw new AssertionError("Native END/backspace did not clear value: "+editorDiagnostics(screen));});
        context.getInput().typeChars(text);
        context.runOnClient(client->{var screen=(SequencedGearshiftScreen)client.gui.screen();var field=firstValueField(screen);if(!field.isFocused()||!field.getValue().equals(text)||screen.instructions().getFirst().value()!=Integer.parseInt(text))throw new AssertionError("Native typed value did not reach editor model: expected="+text+", "+editorDiagnostics(screen));Create.LOGGER.info("Sequencer editor after native value replacement: {}",editorDiagnostics(screen));});
    }
    private static EditBox firstValueField(SequencedGearshiftScreen screen){return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).sorted(java.util.Comparator.comparingInt(EditBox::getY)).findFirst().orElseThrow();}
    private static String editorDiagnostics(SequencedGearshiftScreen screen){return "instructions="+screen.instructions()+", fields="+screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).map(field->"y="+field.getY()+" value='"+field.getValue()+"' focused="+field.isFocused()+" cursor="+field.getCursorPosition()).toList();}
    private static void verifyEditor(ClientGameTestContext context,String label,List<Instruction> expected){context.runOnClient(client->{var screen=(SequencedGearshiftScreen)client.gui.screen();var diagnostics=editorDiagnostics(screen);Create.LOGGER.info("Sequencer native editor {}: {}",label,diagnostics);if(!screen.instructions().equals(expected))throw new AssertionError("Native editor mismatch "+label+": expected="+expected+", "+diagnostics);});}
    private static void waitConfiguration(ClientGameTestContext context,TestSingleplayerContext world,List<Instruction> expected,String message){
        var actual=new AtomicReference<String>("no native server sample");var ready=new AtomicBoolean();
        for(int tick=0;tick<200&&!ready.get();tick++){world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var entity=seq(level);actual.set(entity==null?"missing loaded sequencer":"instructions="+entity.getInstructions()+", instruction="+entity.getInstructionIndex()+", timer="+entity.getTimer()+", input="+entity.getSpeed()+", output="+entity.getPortSpeed(Direction.EAST));ready.set(entity!=null&&entity.getInstructions().equals(expected));});if(!ready.get())context.waitTicks(1);}
        Create.LOGGER.info("Sequencer native server config: expected={}, actual={}",expected,actual.get());if(!ready.get())throw new AssertionError(message+": expected="+expected+", actual="+actual.get());
    }
    private static String replacementDiagnostics(ServerLevel level,Replacement before){
        var producer=seq(level);var consumer=bearing(level);var owner=consumer==null?null:consumer.getMovedContraption();var data=consumer==null?null:consumer.saveWithoutMetadata(level.registryAccess());
        return "tick="+level.getGameTime()+", baseline="+before+", expected="+(before==null?"unset":(before.angle+90)%360)+", seqInstruction="+(producer==null?"missing":producer.getInstructionIndex())+", seqGeneration="+(producer==null?"missing":producer.getSequenceGeneration())+", seqPhase="+(producer==null?"missing":producer.getSequencePhase())+", consumerRPM="+(consumer==null?"missing":consumer.getSpeed())+", storedAngle="+(data==null?"missing":data.getFloatOr("Angle",Float.NaN))+", owner="+(owner==null?"missing":owner.getUUID())+", ownerAngle="+(owner==null?"missing":owner.getAngle(1))+", remaining="+(data==null?"missing":data.getDoubleOr("SequenceLimitRemaining",-1))+", receipt="+(data==null?"missing":data.read("SequenceLimitPhase",SequencePhase.CODEC).orElse(null));
    }
    private static void waitReplacementCompletion(ClientGameTestContext context,TestSingleplayerContext world,Replacement before){
        var ready=new AtomicBoolean();var last=new AtomicReference<String>("no server sample");float expected=(before.angle+90)%360;
        for(int tick=0;tick<200&&!ready.get();tick++){world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var consumer=bearing(level);var owner=consumer.getMovedContraption();last.set(replacementDiagnostics(level,before));ready.set(seq(level).isIdle()&&consumer.getSpeed()==0&&owner!=null&&before.owner.equals(owner.getUUID())&&Float.compare(consumer.saveWithoutMetadata(level.registryAccess()).getFloatOr("Angle",Float.NaN),owner.getAngle(1))==0&&Math.abs(Math.IEEEremainder(owner.getAngle(1)-expected,360))<.001);});if(!ready.get())context.waitTicks(1);}
        Create.LOGGER.info("Sequencer native replacement final state: {}",last.get());if(!ready.get())throw new AssertionError("Actual replacement did not grant a fresh full90degrees to the same native owner: "+last.get());
    }
    private static void clickWidget(ClientGameTestContext context,int row,int width,boolean value){
        var point=context.computeOnClient(client->{var widgets=client.gui.screen().children().stream().filter(widget->value?widget instanceof EditBox:widget instanceof Button).map(widget->(AbstractWidget)widget).filter(widget->widget.getWidth()==width).sorted(java.util.Comparator.comparingInt(AbstractWidget::getY)).toList();var widget=widgets.get(row);float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new Vec2((widget.getX()+widget.getWidth()/2f)*scaleX,(widget.getY()+widget.getHeight()/2f)*scaleY);});
        context.getInput().setCursorPos(point.x,point.y);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
    private static SequencedGearshiftBlockEntity seq(ServerLevel level){return LoadedLevelAccess.blockEntity(level,S)instanceof SequencedGearshiftBlockEntity value?value:null;}
    private static MechanicalBearingBlockEntity bearing(ServerLevel level){return LoadedLevelAccess.blockEntity(level,B)instanceof MechanicalBearingBlockEntity value?value:null;}
    private static void block(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
