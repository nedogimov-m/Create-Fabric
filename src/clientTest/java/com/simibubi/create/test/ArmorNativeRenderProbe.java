package com.simibubi.create.test;
/** Test-only counters advance solely at real native source render submissions, without rendering themselves. */
public final class ArmorNativeRenderProbe {
    private static boolean active;private static String label,expected;private static int world,wearable,foil,box,sleeve,air,blur,cardboard,trim;
    public static void begin(String name,String kind){label=name;expected=kind;world=wearable=foil=box=sleeve=air=blur=cardboard=trim=0;active=true;}
    public static void world(){if(active)world++;}
    public static void wearable(boolean hasFoil){if(active){wearable++;if(hasFoil)foil++;}}
    public static void box(boolean submitted){if(active&&submitted)box++;}
    public static void sleeve(boolean submitted){if(active&&submitted)sleeve++;}
    public static void air(){if(active)air++;}
    public static void blur(){if(active)blur++;}
    public static void cardboard(boolean trimmed){if(active){cardboard++;if(trimmed)trim++;}}
    public static void end(){active=false;System.out.println("CREATE_ARMOR_NATIVE_FRAME {\"scene\":\""+label+"\",\"world\":"+world+",\"wearable\":"+wearable+",\"foil\":"+foil+",\"box\":"+box+",\"sleeve\":"+sleeve+",\"airHudText\":"+air+",\"blur\":"+blur+",\"cardboardArmorLayers\":"+cardboard+",\"nativeTrimmedLayers\":"+trim+"}");boolean valid=switch(expected){case "world"->world>=2;case "wearable"->wearable>0;case "foil"->foil>0;case "box"->box>0;case "sleeve"->sleeve>0;case "air"->air>0;case "blur"->blur>0;case "standing"->cardboard>=4&&trim>0;default->false;};if(!valid)throw new AssertionError("Expected actual native armor submission missing for "+label+": "+expected);}
    private ArmorNativeRenderProbe(){}
}
