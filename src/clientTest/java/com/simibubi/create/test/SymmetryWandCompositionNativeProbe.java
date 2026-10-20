package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandClient;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

/** Read-only witness of the two actual native marker submits and their independent world matrices. */
public final class SymmetryWandCompositionNativeProbe {
    private static boolean active;private static String label;private static Set<BlockPos> expected=Set.of();private static final Set<BlockPos> observed=new HashSet<>();private static int submits;
    public static void begin(String name,BlockPos a,BlockPos b){label=name;expected=Set.of(a,b);observed.clear();submits=0;active=true;}
    public static void world(PartialModelRenderState state,PoseStack pose){
        if(!active||SymmetryWandClient.latest().markers().stream().noneMatch(marker->marker.model().mesh==state.mesh))return;
        if(state.mesh==null||state.isEmpty()||state.mesh.size()!=38)throw new AssertionError("Actual native composed plane submit lost source38-face geometry");
        var center=pose.last().pose().transformPosition(new Vector3f(.5f,.5f,.5f));var camera=Minecraft.getInstance().gameRenderer.mainCamera().position();
        if(!Float.isFinite(center.x)||!Float.isFinite(center.y)||!Float.isFinite(center.z))throw new AssertionError("Actual native marker matrix has nonfinite coordinates");
        var actual=BlockPos.containing(camera.x+center.x,camera.y+center.y,camera.z+center.z);
        if(!expected.contains(actual))throw new AssertionError("Native marker submitted at unexpected actual world position "+actual+" expected "+expected);
        observed.add(actual);submits++;
    }
    public static void end(){active=false;System.out.println("CREATE_SYMMETRY_COMPOSITION_NATIVE "+label+" actualNativeSubmits="+submits+" actualSubmittedWorldPositions="+observed+" expected="+expected);if(submits<2||!observed.equals(expected))throw new AssertionError("Both independently positioned actual plane meshes were not submitted through native rendering");}
    private SymmetryWandCompositionNativeProbe(){}
}
