package com.simibubi.create.test;

import com.simibubi.create.content.equipment.symmetryWand.*;
import java.util.*;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.util.LightCoordsUtil;

/** Observes actual native item submissions and picture-in-picture rendering, never calls either. */
public final class SymmetryWandNativeProbe {
    private static boolean active;private static String label;private static final Set<Integer> faces=new HashSet<>();private static int draws,previews,worlds,frames;private static java.util.ArrayList<Integer> current;private static int expectedWorldFaces,expectedPortraitFaces;
    public static void begin(String name){begin(name,0,56);}
    public static void begin(String name,int worldFaces,int portraitFaces){expectedWorldFaces=worldFaces;expectedPortraitFaces=portraitFaces;label=name;faces.clear();draws=previews=worlds=frames=0;current=null;active=true;}
    public static void draw(ItemQuads quads,int light){if(!active)return;int count=quads.all().size();if(count!=38&&count!=6&&count!=24)throw new AssertionError("Actual native wand draws lost pinned face geometry: "+count);if(count!=38&&light!=LightCoordsUtil.FULL_BRIGHT)throw new AssertionError("Actual native core/halo/bits lost source full-bright light");faces.add(count);draws++;if(current!=null)current.add(count);}
    public static void frameStart(){if(active)current=new java.util.ArrayList<>();}
    public static void frameEnd(){if(!active)return;if(current==null||!current.equals(java.util.List.of(38,6,6,24)))throw new AssertionError("One actual native wand submit lost a source layer: "+current);frames++;current=null;}
    public static void world(com.simibubi.create.foundation.model.PartialModelRenderState state){if(!active)return;for(var marker:SymmetryWandClient.latest().markers())if(marker.model().mesh==state.mesh){if(state.isEmpty()||expectedWorldFaces>0&&state.mesh.size()!=expectedWorldFaces)throw new AssertionError("Actual native marker submit has wrong concrete source geometry: "+state.mesh.size()+" expected "+expectedWorldFaces);worlds++;break;}}
    public static void portrait(SymmetryWandPortrait.State state){if(!active)return;if(state.model().isEmpty()||state.model().mesh==null||expectedPortraitFaces>0&&state.model().mesh.size()!=expectedPortraitFaces)throw new AssertionError("Actual native screen did not draw its independently expected concrete source mirror mesh");previews++;}
    public static void end(boolean preview){active=false;System.out.println("CREATE_SYMMETRY_NATIVE "+label+" draws="+draws+" sourceFaceCounts="+faces+" nativePictureInPicture="+previews+" worldMarkerSubmits="+worlds+" completeItemFrames="+frames+" expectedWorldFaces="+expectedWorldFaces+" expectedPortraitFaces="+expectedPortraitFaces);if(frames==0||draws==0||!preview&&worlds==0||!faces.containsAll(Set.of(38,6,24))||preview&&previews==0)throw new AssertionError("Native wand frame did not submit all actual source partials/preview: "+label);}
    private SymmetryWandNativeProbe(){}
}
