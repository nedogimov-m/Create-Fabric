package com.simibubi.create.content.trains.station;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Actual source formation, accepted native forest transfer, full Train adoption and independent recovery. */
public final class StationTrainAssembly {
  public enum Status {NOT_CHECKED,ASSEMBLED,FAILED,RECOVERING,DISASSEMBLY_PENDING,DISASSEMBLED}
  public record Report(Status status,Train train,AssemblyException failure){
    public boolean assembled(){return status==Status.ASSEMBLED&&train!=null;}
  }
  private record Formation(StationAssemblyPreflight.Plan plan,List<CarriageContraption> bodies,List<Carriage> carriages,List<Integer> spacing,boolean doubleEnded){}

  private static AssemblyException sourceFailure(String key,Object...args){return new AssemblyException(Component.translatable("create.train_assembly."+key,args));}

  private static Formation capture(StationBlockEntity station,Player player,StationAssemblyPreflight.Plan plan)throws AssemblyException {
    if(!(station.getLevel() instanceof ServerLevel level)||!plan.stillCurrent(station,player))throw AssemblyException.structureChanged(station.getBlockPos());
    var area=plan.area();var bogeys=area.bogeys();var points=plan.wheelPoints();
    var bodies=new ArrayList<CarriageContraption>();var carriages=new ArrayList<Carriage>();var spacing=new ArrayList<Integer>();
    boolean hasForward=false,hasBackward=false;
    var claimed=new HashSet<BlockPos>();
    for(int index=0;index<bogeys.size();index++){
      var first=bogeys.get(index);int pointIndex=index*2;
      if(index>0)spacing.add(first.offset()-bogeys.get(index-1).offset());
      var body=new CarriageContraption(area.direction());
      if(!body.assemble(level,first.pos()))throw sourceFailure("nothing_attached",index+1);
      if(!body.anchor.equals(first.pos()))throw AssemblyException.structureChanged(first.pos());
      body.validateSourceBlocks(level,BlockPos.ZERO);
      body.setSoundQueueOffset(first.offset()+1);
      body.expandBoundsAroundAxis(Direction.Axis.Y);
      hasForward|=body.hasForwardControls();hasBackward|=body.hasBackwardControls();
      var firstBogey=new CarriageBogey(first.block(),first.upsideDown(),first.data(),points.get(pointIndex),points.get(pointIndex+1));
      CarriageBogey secondBogey=null;int bogeySpacing=0;
      var second=body.getSecondBogeyPos();
      if(second!=null){
        if(index+1>=bogeys.size()||!second.equals(bogeys.get(index+1).pos()))throw sourceFailure("not_connected_in_order");
        var metadata=bogeys.get(index+1);bogeySpacing=metadata.offset()-first.offset();
        secondBogey=new CarriageBogey(metadata.block(),metadata.upsideDown(),metadata.data(),points.get(pointIndex+2),points.get(pointIndex+3));index++;
      }else if(!first.block().allowsSingleBogeyCarriage())throw sourceFailure("single_bogey_carriage");
      for(var info:body.getBlocks().values()){
        var world=body.anchor.offset(info.pos());
        if(!claimed.add(world)||world.equals(station.getBlockPos()))throw AssemblyException.structureChanged(world);
        LoadedEntityAccess.requireReady(level,world);
        if(!level.mayInteract(player,world)||!level.getWorldBorder().isWithinBounds(world)
            ||!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,player,world,info.state(),LoadedLevelAccess.blockEntity(level,world)))
          throw AssemblyException.unmovableBlock(world,info.state());
      }
      body.validateSourceBlocks(level,BlockPos.ZERO);
      bodies.add(body);carriages.add(new Carriage(firstBogey,secondBogey,bogeySpacing));
    }
    if(!hasForward)throw sourceFailure("no_controls");
    if(bodies.isEmpty())throw sourceFailure("no_bogeys");
    if(!plan.stillCurrent(station,player))throw AssemblyException.structureChanged(station.getBlockPos());
    return new Formation(plan,List.copyOf(bodies),List.copyOf(carriages),List.copyOf(spacing),hasBackward);
  }

  public static Report assemble(StationBlockEntity station,Player player){
    if(!(station.getLevel() instanceof ServerLevel level)||!StationPermissions.canEdit(player,station,true)||!station.isAssembling())
      return new Report(Status.FAILED,null,AssemblyException.structureChanged(station.getBlockPos()));
    var point=station.getStation();
    if(point==null||point.getPresentTrain()!=null)return new Report(Status.FAILED,null,AssemblyException.structureChanged(station.getBlockPos()));
    var checked=station.preflight(player);
    if(!checked.readyForCapture())return new Report(Status.FAILED,null,null);
    var reservations=new ArrayList<CarriageContraptionEntity>();
    Train adopted=null;
    boolean published=false;
    try{
      var formation=capture(station,player,checked.plan());
      for(var body:formation.bodies()){
        var front=CarriageContraptionEntity.reserve(level,body);
        if(front==null)throw AssemblyException.entitiesNotReady(body.anchor);
        reservations.add(front);
      }
      if(!formation.plan().stillCurrent(station,player))throw AssemblyException.structureChanged(station.getBlockPos());
      var roots=new ArrayList<ContraptionAssemblyGraph.Root>();
      for(int i=0;i<reservations.size();i++)roots.add(new ContraptionAssemblyGraph.Root(reservations.get(i),formation.bodies().get(i)));
      var transfer=ContraptionAssemblyGraph.assemble(level,roots,complete->{});
      if(!transfer.complete()){
        boolean recovering=recover(reservations);
        return new Report(recovering?Status.RECOVERING:Status.FAILED,null,transfer.failure());
      }
      // Native mount callbacks must still see complete chunk-owned independent body/cargo.
      for(var front:reservations)if(!front.adoptCapturedOriginalRoots())throw AssemblyException.entitiesNotReady(front.blockPosition());
      adopted=new Train(UUID.randomUUID(),player.getUUID(),point.graph(),formation.carriages(),formation.spacing(),formation.doubleEnded(),station.lastDisassembledMapColorIndex());
      var previousName=station.lastDisassembledTrainName();if(previousName!=null)adopted.name=previousName;
      for(int i=0;i<formation.bodies().size();i++)formation.carriages().get(i).setContraption(level,formation.bodies().get(i));
      adopted.setCurrentStation(point);
      var traffic=TrainManager.get(level);
      traffic.add(adopted);published=true;
      adopted.collectInitiallyOccupiedSignalBlocks();
      station.assemblyCompleted(adopted);
      station.award(AllAdvancements.TRAIN);
      if(formation.carriages().size()>=6)station.award(AllAdvancements.LONG_TRAIN);
      if(formation.bodies().stream().anyMatch(CarriageContraption::containsBlockBreakers))
        station.award(AllAdvancements.CONTRAPTION_ACTORS);
      return new Report(Status.ASSEMBLED,adopted,null);
    }catch(AssemblyException refused){
      boolean recovering=recover(reservations);
      return new Report(recovering?Status.RECOVERING:Status.FAILED,null,refused);
    }catch(RuntimeException failure){
      // Once the actual manager owns a Train, it alone retains cargo/body custody even if UI finalization fails.
      if(adopted!=null&&adopted.trafficManager()!=null&&adopted.trafficManager().get(adopted.id)==adopted){published=true;throw failure;}
      recover(reservations);throw failure;
    }finally{
      if(!published)for(var front:reservations)if(front.getContraption()==null&&!front.isRemoved())front.discard();
    }
  }

  /** Returns whether genuine independent native owners still retain pending recovery work. */
  private static boolean recover(List<CarriageContraptionEntity> reservations){
    boolean pending=false;
    for(var front:reservations){
      if(front.getContraption()==null){if(!front.isRemoved())front.discard();continue;}
      if(front.hasOwnedStructure()&&!front.recoverIndependent())pending=true;
    }
    return pending;
  }
  private StationTrainAssembly(){}
}
