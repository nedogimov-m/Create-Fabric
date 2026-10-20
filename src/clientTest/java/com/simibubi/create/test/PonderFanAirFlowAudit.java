package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.AirFlowParticle;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import java.lang.reflect.Field;
import java.util.*;
import net.createmod.catnip.levelWrappers.WrappedClientLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderWorldParticles;
import net.createmod.ponder.mixin.client.PonderParticleAccessor;
import net.createmod.ponder.test.mixin.PonderWorldParticlesAuditAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

/** Read-only observation of natural source ticks, actual local queues and native quad extraction. */
public final class PonderFanAirFlowAudit {
    private static Session active;
    private static final ThreadLocal<ArrayDeque<ExtraCall>> EXTRAS=ThreadLocal.withInitial(ArrayDeque::new);
    public static final class Session {
        public final PonderLevel world;
        public final Map<String,Integer> morphed=new LinkedHashMap<>(),extraCalls=new LinkedHashMap<>(),extraCreated=new LinkedHashMap<>();
        public final Set<IAirCurrentSource> sources=Collections.newSetFromMap(new IdentityHashMap<>());
        public final Set<AirFlowParticle> flows=Collections.newSetFromMap(new IdentityHashMap<>());
        public final List<Map<String,Object>> extraReceipts=new ArrayList<>();
        public int actualFlowQuads,actualEngineQuadVertices,hostChecks;
        public Session(PonderLevel world){this.world=world;}
    }
    private record Group(Object key,Object owner,Object queue,List<?> entries){}
    public static final class EngineSnapshot {
        final ParticleEngine engine;final Object groupMap,pendingQueue,emittersQueue;
        final List<Group> groups;final List<?> pending,emitters;final Map<?,?> tracked;
        EngineSnapshot(ParticleEngine engine){
            this.engine=engine;groupMap=read(engine,ParticleEngine.class,"particles");
            pendingQueue=read(engine,ParticleEngine.class,"particlesToAdd");emittersQueue=read(engine,ParticleEngine.class,"trackingEmitters");
            groups=new ArrayList<>();
            for(var entry:((Map<?,?>)groupMap).entrySet()){
                var queue=read(entry.getValue(),ParticleGroup.class,"particles");
                groups.add(new Group(entry.getKey(),entry.getValue(),queue,List.copyOf((Collection<?>)queue)));
            }
            pending=List.copyOf((Collection<?>)pendingQueue);emitters=List.copyOf((Collection<?>)emittersQueue);
            tracked=new HashMap<>((Map<?,?>)read(engine,ParticleEngine.class,"trackedParticleCounts"));
        }
        public void assertUnchanged(String stage){
            var now=new EngineSnapshot(engine);
            require(groupMap==now.groupMap&&pendingQueue==now.pendingQueue&&emittersQueue==now.emittersQueue,stage+": host engine queue/map owners changed");
            require(groups.size()==now.groups.size(),stage+": host particle groups changed");
            for(var old:groups){var current=now.groups.stream().filter(g->g.key==old.key).findFirst().orElseThrow(()->new AssertionError(stage+": host group key identity lost"));require(old.owner==current.owner&&old.queue==current.queue&&same(old.entries,current.entries),stage+": host group/particle identities changed");}
            require(same(pending,now.pending)&&same(emitters,now.emitters)&&tracked.equals(now.tracked),stage+": host pending/emitter identities or tracked counts changed");
        }
        public List<Particle> particles(){var out=new ArrayList<Particle>();for(var group:groups)for(var p:group.entries)out.add((Particle)p);for(var p:pending)out.add((Particle)p);return out;}
    }
    private record ExtraCall(Object access,AirFlowParticle particle,ParticleOptions options,float multiplier,String type,
                             Session session,EngineSnapshot before,EngineSnapshot host){}
    public static void begin(Session session){require(active==null,"Local fan observer already active");active=session;EXTRAS.remove();}
    public static void clear(){active=null;EXTRAS.remove();}
    public static EngineSnapshot snapshot(ParticleEngine engine){return new EngineSnapshot(engine);}
    public static PonderWorldParticles service(PonderLevel world){return ((PonderWorldParticlesAuditAccessor)world).create$actualLocalParticles();}
    public static ParticleEngine engine(PonderLevel world){return (ParticleEngine)read(service(world),PonderWorldParticles.class,"engine");}
    public static IAirCurrentSource source(AirFlowParticle particle){return (IAirCurrentSource)read(particle,AirFlowParticle.class,"source");}
    public static double number(Particle particle,String name){return ((Number)read(particle,Particle.class,name)).doubleValue();}
    public static List<Particle> particles(PonderLevel world){var engine=engine(world);return engine==null?List.of():snapshot(engine).particles();}
    public static void assertLocal(Particle particle,PonderLevel world,String stage){
        var owner=((PonderParticleAccessor)particle).ponder$world();
        require(owner instanceof WrappedClientLevel&&((WrappedClientLevel)owner).getWrappedLevel()==world,stage+": particle belongs to actual local WrappedClientLevel/Ponder owner");
        require(owner!=Minecraft.getInstance().level,stage+": particle cannot belong to host level");
    }
    public static String type(FanProcessingType value){return value==null?"none":CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(value).toString();}
    private static FanProcessingType currentType(AirFlowParticle particle,IAirCurrentSource source){
        var pos=source.getAirCurrentPos();var axis=source.getAirflowOriginSide().getUnitVec3();
        double dx=number(particle,"x")-(pos.getX()+.5),dy=number(particle,"y")-(pos.getY()+.5),dz=number(particle,"z")-(pos.getZ()+.5);
        double distance=new Vec3(dx,dy,dz).multiply(axis).length()-.5;
        return source.getAirCurrent().getTypeAt((float)distance);
    }
    public static void observeNatural(Session session,Camera camera){
        var local=engine(session.world);if(local==null)return;
        require(local!=Minecraft.getInstance().particleEngine,"Actual local particle engine must differ from host engine");
        for(var particle:particles(session.world)){
            assertLocal(particle,session.world,"natural tick");
            if(!(particle instanceof AirFlowParticle flow)||!flow.isAlive())continue;
            var source=source(flow);
            require(source instanceof EncasedFanBlockEntity,"Natural airflow source must be a genuine EncasedFanBlockEntity");
            var fan=(EncasedFanBlockEntity)source;
            if(fan.isRemoved()){require(fan.getLevel()==session.world&&session.world.getBlockEntity(fan.getBlockPos())!=fan,"Retired natural source remains local while native cleanup awaits its next tick");continue;}
            require(fan.isVirtual()&&fan.getLevel()==session.world&&!fan.isRemoved()
                &&session.world.getBlockEntity(fan.getBlockPos())==fan&&fan.getSpeed()!=0&&fan.getAirCurrent().maxDistance>0,
                "Natural airflow source must be the current nonzero actual scene fan");
            require(Minecraft.getInstance().level.getBlockEntity(fan.getBlockPos())!=fan,"Natural airflow factory cannot resolve a coincident host owner");
            session.sources.add(source);session.flows.add(flow);
            if(number(flow,"age")<=0)continue;
            var state=new QuadParticleRenderState();flow.extract(state,camera,.5f);var vertices=new Vertices();state.buildLayer(SingleQuadParticle.Layer.TRANSLUCENT,vertices);
            require(vertices.positions.size()==4&&vertices.colors.size()==4,"Natural actual airflow extracts exactly one native translucent quad");
            finite(vertices);session.actualFlowQuads++;
            var kind=currentType(flow,source);String id=type(kind);
            if(kind==AllFanProcessingTypes.BLASTING||kind==AllFanProcessingTypes.SMOKING||kind==AllFanProcessingTypes.SPLASHING){
                int color=vertices.colors.getFirst(),alpha=color>>>24;int r=color>>16&255,g=color>>8&255,b=color&255;
                boolean morphology=kind==AllFanProcessingTypes.BLASTING?Math.abs(alpha-127)<=1&&r==255&&g>=68&&g<=136&&b<=85:
                    kind==AllFanProcessingTypes.SMOKING?alpha==255&&r==g&&g==b&&r<=85:
                    alpha==255&&r>=34&&r<=68&&g>=119&&g<=153&&b==255;
                // A boundary crossing can retain the preceding segment color for one tick.
                if(morphology)session.morphed.merge(id,1,Integer::sum);
            }
        }
    }
    /** Test mixin HEAD. Observes genuine Access invocation; never calls or replaces a callback. */
    public static void extraHead(Object access,ParticleOptions options,float multiplier){
        var session=active;if(session==null)return;
        var particle=(AirFlowParticle)read(access,access.getClass(),"this$0");var owner=((PonderParticleAccessor)particle).ponder$world();
        if(!(owner instanceof WrappedClientLevel wrapped)||wrapped.getWrappedLevel()!=session.world)return;
        var source=source(particle);require(source!=null&&source.getAirCurrentWorld()==session.world,"Extra callback retains actual local source");
        assertLocal(particle,session.world,"extra HEAD");
        var id=type(currentType(particle,source));
        EXTRAS.get().push(new ExtraCall(access,particle,options,multiplier,id,session,snapshot(engine(session.world)),snapshot(Minecraft.getInstance().particleEngine)));
    }
    /** Test mixin RETURN. Checks immediate local append plus exact untouched host queues. */
    public static void extraReturn(Object access,ParticleOptions options,float multiplier){
        var calls=EXTRAS.get();if(calls.isEmpty()||calls.peek().access!=access)return;var call=calls.pop();
        require(call.options==options&&Float.floatToIntBits(call.multiplier)==Float.floatToIntBits(multiplier),"Observer retains original native extra arguments");
        var session=call.session;var before=call.before;var after=snapshot(engine(session.world));
        require(before.engine==after.engine&&before.groupMap==after.groupMap&&before.pendingQueue==after.pendingQueue,"Extra emission preserves actual local engine/queue owners");
        require(before.groups.size()==after.groups.size(),"Extra emission cannot replace existing native local groups");
        for(var old:before.groups){var now=after.groups.stream().filter(g->g.key==old.key).findFirst().orElseThrow();require(old.owner==now.owner&&old.queue==now.queue&&same(old.entries,now.entries),"Extra emission leaves grouped native particles intact");}
        require(after.pending.size()>=before.pending.size()&&after.pending.size()<=before.pending.size()+1,"One native extra call adds at most one actual local particle");
        require(same(before.pending,after.pending.subList(0,before.pending.size())),"Extra emission preserves exact original pending particle sequence");
        int added=after.pending.size()-before.pending.size();
        if(added==1){assertLocal((Particle)after.pending.getLast(),session.world,"extra RETURN");session.extraCreated.merge(call.type,1,Integer::sum);}
        call.host.assertUnchanged("extra RETURN");session.hostChecks++;session.extraCalls.merge(call.type,1,Integer::sum);
        if(session.extraReceipts.size()<60)session.extraReceipts.add(Map.of("type",call.type,"option",BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString(),"added",added,"pendingBefore",before.pending.size(),"pendingAfter",after.pending.size(),"sourceOwnerIdentity",System.identityHashCode(source(call.particle)),"hostQueuesUnchanged",true));
    }
    public static int capturedQuadVertices(ParticlesRenderState state){
        int count=0;
        for(var group:(List<?>)read(state,ParticlesRenderState.class,"particles"))if(group instanceof QuadParticleRenderState quad){
            for(var layer:quad.layers()){var vertices=new Vertices();quad.buildLayer(layer,vertices);finite(vertices);count+=vertices.positions.size();}
        }
        return count;
    }
    public static void finite(Vertices vertices){for(var v:vertices.positions)require(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z),"Native extracted particle vertices must be finite");}
    public static Object read(Object value,Class<?> owner,String name){
        try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(value);}
        catch(ReflectiveOperationException failure){throw new AssertionError("Actual native field unavailable: "+owner.getName()+"."+name,failure);}
    }
    private static boolean same(List<?> first,List<?> second){if(first.size()!=second.size())return false;for(int i=0;i<first.size();i++)if(first.get(i)!=second.get(i))return false;return true;}
    public static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static final class Vertices implements VertexConsumer {
        public final List<Vec3> positions=new ArrayList<>();public final List<Integer> colors=new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z){positions.add(new Vec3(x,y,z));return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){colors.add(a<<24|r<<16|g<<8|b);return this;}
        @Override public VertexConsumer setColor(int color){colors.add(color);return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setUv3(float u,float v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        @Override public VertexConsumer setLineWidth(float width){return this;}
    }
    private PonderFanAirFlowAudit(){}
}
