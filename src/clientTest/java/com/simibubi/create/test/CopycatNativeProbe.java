package com.simibubi.create.test;

import com.simibubi.create.content.decoration.copycat.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Observes only actual native Copycat model emissions; never requests geometry or changes a quad. */
public final class CopycatNativeProbe {
    public record Expected(BlockState material,int faces,AABB bounds,Set<String> textures,Map<String,Integer> layers,boolean tinted){
        public Expected{textures=Set.copyOf(textures);layers=Map.copyOf(layers);}
        public Expected(BlockState material,int faces,AABB bounds,Set<String> textures,String layer,boolean tinted){this(material,faces,bounds,textures,Map.of(layer,faces),tinted);}
    }
    public static final class Scope {
        final long generation;final BlockPos pos;final BlockState material;final Expected expected;
        int faces;double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        final Set<String> textures=new HashSet<>();final Map<String,Integer> layers=new HashMap<>();final Set<Integer> colors=new HashSet<>();
        Scope(long generation,BlockPos pos,BlockState material,Expected expected){this.generation=generation;this.pos=pos.immutable();this.material=material;this.expected=expected;}
    }
    private record Observation(BlockState material,int faces,AABB bounds,Set<String> textures,Map<String,Integer> layers,Set<Integer> colors){}
    private static volatile long generation;private static volatile Map<BlockPos,Expected> expected=Map.of();private static volatile String label;
    private static final Map<BlockPos,Observation> observed=new ConcurrentHashMap<>();
    public static void begin(String name,Map<BlockPos,Expected> positions){expected=Map.of();generation++;label=name;observed.clear();expected=Map.copyOf(positions);}
    public static Scope submission(BlockAndTintGetter level,BlockPos pos){var rule=expected.get(pos);if(rule==null)return null;var material=CopycatModel.getMaterial(level,pos);return material==rule.material?new Scope(generation,pos,material,rule):null;}
    public static void quad(Scope scope,QuadView quad,SpriteFinder finder){
        var sprite=finder.find(quad);if(sprite==null||sprite.contents().name().getPath().contains("missing"))throw new AssertionError("Actual Copycat source material sprite is missing");
        scope.textures.add(sprite.contents().name().toString());scope.layers.merge(quad.chunkLayer().toString().toLowerCase(Locale.ROOT),1,Integer::sum);scope.faces++;
        for(int vertex=0;vertex<4;vertex++){double x=quad.x(vertex),y=quad.y(vertex),z=quad.z(vertex);if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(quad.u(vertex))||!Float.isFinite(quad.v(vertex)))throw new AssertionError("Actual native Copycat geometry/UV is nonfinite");scope.minX=Math.min(scope.minX,x);scope.minY=Math.min(scope.minY,y);scope.minZ=Math.min(scope.minZ,z);scope.maxX=Math.max(scope.maxX,x);scope.maxY=Math.max(scope.maxY,y);scope.maxZ=Math.max(scope.maxZ,z);scope.colors.add(quad.color(vertex));
            if(quad.u(vertex)<sprite.getU0()-1e-5||quad.u(vertex)>sprite.getU1()+1e-5||quad.v(vertex)<sprite.getV0()-1e-5||quad.v(vertex)>sprite.getV1()+1e-5)throw new AssertionError("Native cropped Copycat UV escaped the actual source sprite");
        }
    }
    public static void finish(Scope scope){if(scope.generation!=generation)return;var bounds=new AABB(scope.minX,scope.minY,scope.minZ,scope.maxX,scope.maxY,scope.maxZ);observed.put(scope.pos,new Observation(scope.material,scope.faces,bounds,Set.copyOf(scope.textures),Map.copyOf(scope.layers),Set.copyOf(scope.colors)));}
    public static boolean complete(){return observed.keySet().containsAll(expected.keySet())&&!expected.isEmpty();}
    public static void verify(){
        if(!complete())throw new AssertionError("Actual native chunk emissions did not reach every expected Copycat position: "+observed.keySet()+" expected "+expected.keySet());
        for(var entry:expected.entrySet()){var actual=observed.get(entry.getKey());var rule=entry.getValue();if(actual.faces!=rule.faces||!same(actual.bounds,rule.bounds)||!rule.textures.containsAll(actual.textures)||actual.textures.isEmpty()||!actual.layers.equals(rule.layers)||rule.tinted&&actual.colors.stream().noneMatch(color->(color&0xFFFFFF)!=0xFFFFFF))throw new AssertionError("Actual native Copycat source geometry/material/culling/tint receipt failed at "+entry.getKey()+": "+actual+" expected "+rule);}
        System.out.println("CREATE_COPYCAT_NATIVE "+label+" actualNativeChunkGeometry="+observed);
    }
    private static boolean same(AABB a,AABB b){return Math.abs(a.minX-b.minX)<1e-5&&Math.abs(a.minY-b.minY)<1e-5&&Math.abs(a.minZ-b.minZ)<1e-5&&Math.abs(a.maxX-b.maxX)<1e-5&&Math.abs(a.maxY-b.maxY)<1e-5&&Math.abs(a.maxZ-b.maxZ)<1e-5;}
    public static void end(){verify();expected=Map.of();}
    private CopycatNativeProbe(){}
}
