package com.simibubi.create.content.trains.display;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.minecraft.world.level.storage.*;

/** Actual source flap layout/text/transition kernel; no client renderer or fictional display transport. */
public final class FlapDisplaySection {
    public static final float MONOSPACE=7,WIDE_MONOSPACE=9;
    private static final Map<String,String[]> LOADED_FLAP_CYCLES=new HashMap<>();
    private static Language cycleLanguage;
    final float size;
    final boolean singleFlap,hasGap;
    boolean rightAligned,wideFlaps,sendTransition;
    final String cycle;
    Component component;
    private String serializedText;
    String[] cyclingOptions;
    final boolean[] spinning;
    int spinningTicks;
    String text;
    public FlapDisplaySection(float width,String cycle,boolean singleFlap,boolean hasGap){
        this.size=Float.isFinite(width)?Math.clamp(width,0,8192):0;this.cycle=cycle==null?"alphabet":cycle;this.singleFlap=singleFlap;this.hasGap=hasGap;spinning=new boolean[singleFlap?1:(int)(size/MONOSPACE)];text=" ".repeat(spinning.length);
    }
    public FlapDisplaySection rightAligned(){rightAligned=true;return this;}
    public FlapDisplaySection wideFlaps(){wideFlaps=true;return this;}
    public void setText(Component text){component=text==null?Component.empty():text.copy();serializedText=ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE,component).getOrThrow().toString();sendTransition=true;}
    public void setText(Component text,HolderLookup.Provider registries){component=text==null?Component.empty():text.copy();serializedText=ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),component).getOrThrow().toString();sendTransition=true;}
    public Component getText(){return component;}
    public String getRenderedText(){return text;}
    public float getSize(){return size;}
    public boolean hasGap(){return hasGap;}
    public boolean isWide(){return wideFlaps;}
    public boolean isRightAligned(){return rightAligned;}
    public boolean isSpinning(int index){return index>=0&&index<spinning.length&&spinning[index];}
    public int flapCount(){return spinning.length;}
    public int spinningTicks(){return spinningTicks;}
    public boolean renderCharsIndividually(){return !singleFlap;}
    public void refresh(boolean transition){
        if(component==null)return;var next=component.getString();
        if(!singleFlap){if(rightAligned)next=next.trim();next=next.toUpperCase(Locale.ROOT);next=next.substring(0,Math.min(spinning.length,next.length()));var space=" ".repeat(spinning.length-next.length());next=rightAligned?space+next:next+space;for(int i=0;i<spinning.length;i++)spinning[i]|=transition&&text.charAt(i)!=next.charAt(i);}
        else if(!text.isEmpty())spinning[0]|=transition&&!next.equals(text);
        text=next;spinningTicks=0;cyclingOptions=getFlapCycle(cycle);
    }
    public int tick(boolean instant,RandomSource random){
        cyclingOptions=getFlapCycle(cycle);int max=Math.max(4,(int)(cyclingOptions.length*1.75f));if(spinningTicks>max)return 0;spinningTicks++;if(spinningTicks<2)return 0;int active=0;
        for(int i=0;i<spinning.length;i++){int chance=Math.clamp(8-spinningTicks,1,10);boolean next=!instant&&random.nextInt(chance*max/4)!=0;next&=max>5||spinningTicks<2;spinning[i]&=next;if(i>0&&random.nextInt(3)>0)spinning[i-1]&=next;if(i<spinning.length-1&&random.nextInt(3)>0)spinning[i+1]&=next;if(spinningTicks>max)spinning[i]=false;if(spinning[i])active++;}return active;
    }
    public void write(ValueOutput out){
        out.putFloat("Width",size);out.putString("Cycle",cycle);if(rightAligned)out.putBoolean("RightAligned",true);if(singleFlap)out.putBoolean("SingleFlap",true);if(hasGap)out.putBoolean("Gap",true);if(wideFlaps)out.putBoolean("Wide",true);if(serializedText!=null)out.putString("Text",serializedText);if(sendTransition)out.putBoolean("Transition",true);
    }
    public static FlapDisplaySection load(ValueInput in){var section=new FlapDisplaySection(in.getFloatOr("Width",0),in.getStringOr("Cycle","alphabet"),in.getBooleanOr("SingleFlap",false),in.getBooleanOr("Gap",false));section.rightAligned=in.getBooleanOr("RightAligned",false);section.wideFlaps=in.getBooleanOr("Wide",false);section.update(in);return section;}
    boolean sameStructure(ValueInput in){return size==in.getFloatOr("Width",0)&&cycle.equals(in.getStringOr("Cycle","alphabet"))&&singleFlap==in.getBooleanOr("SingleFlap",false)&&hasGap==in.getBooleanOr("Gap",false)&&rightAligned==in.getBooleanOr("RightAligned",false)&&wideFlaps==in.getBooleanOr("Wide",false);}
    public void update(ValueInput in){
        sendTransition=in.getBooleanOr("Transition",false);var json=in.getStringOr("Text","");boolean changed=!Objects.equals(serializedText,json);if(!json.isEmpty())try{var value=ComponentSerialization.CODEC.parse(in.lookup().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(json)).result();if(value.isPresent()){component=value.get();serializedText=json;}}catch(RuntimeException invalid){component=Component.empty();serializedText="\"\"";}
        if(cyclingOptions==null||changed)refresh(in.getBooleanOr("Transition",false));
    }
    public static synchronized String[] getFlapCycle(String key){
        var language=Language.getInstance();if(cycleLanguage!=language){LOADED_FLAP_CYCLES.clear();cycleLanguage=language;}
        return LOADED_FLAP_CYCLES.computeIfAbsent(key,k->{var translated=language.getOrDefault("create.flap_display.cycles."+k," ; ");var values=translated.split(";",-1);for(int i=0;i<values.length;i++)if(values[i].isEmpty())values[i]=" ";return values.length==0?new String[]{" "}:values;});
    }
}
