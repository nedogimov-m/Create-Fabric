package com.simibubi.create.foundation.utility;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.Create;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Actual source raw/parsed text, using native registry-aware component codecs and resolution context. */
public final class DynamicComponent {
    private JsonElement rawCustomText;
    private Component parsedCustomText;
    private String serializedCustomText;
    public boolean sameAs(String json){return isValid()&&rawCustomText.equals(getJsonFromString(json));}
    public boolean isValid(){return rawCustomText!=null&&parsedCustomText!=null;}
    public String resolve(){return parsedCustomText==null?"":parsedCustomText.getString();}
    public MutableComponent get(){return parsedCustomText==null?Component.empty():parsedCustomText.copy();}
    public void displayCustomText(Level level,BlockPos pos,String json){rawCustomText=getJsonFromString(json);parsedCustomText=parseCustomText(level,pos,rawCustomText);serializedCustomText=parsedCustomText==null?null:ComponentSerialization.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),parsedCustomText).getOrThrow().toString();}
    public void read(BlockPos pos,ValueInput input){rawCustomText=getJsonFromString(input.getStringOr("RawCustomText",""));serializedCustomText=input.getStringOr("CustomText","");try{parsedCustomText=ComponentSerialization.CODEC.parse(input.lookup().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(serializedCustomText)).result().orElse(null);}catch(RuntimeException invalid){parsedCustomText=null;}}
    public void write(ValueOutput output){if(isValid()){output.putString("RawCustomText",rawCustomText.toString());output.putString("CustomText",serializedCustomText);}}
    public DynamicComponent copy(){var copy=new DynamicComponent();copy.rawCustomText=rawCustomText==null?null:rawCustomText.deepCopy();copy.parsedCustomText=parsedCustomText==null?null:parsedCustomText.copy();copy.serializedCustomText=serializedCustomText;return copy;}
    public static JsonElement getJsonFromString(String json){if(json==null)return null;try{return JsonParser.parseString(json);}catch(JsonParseException invalid){return null;}}
    public static Component parseCustomText(Level level,BlockPos pos,JsonElement json){if(!(level instanceof ServerLevel)||json==null)return null;try{var parsed=ComponentSerialization.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();return parseCustomText(level,pos,parsed);}catch(RuntimeException invalid){return null;}}
    public static Component parseCustomText(Level level,BlockPos pos,Component component){if(!(level instanceof ServerLevel server))return null;try{return ComponentUtils.resolve(ResolutionContext.create(getCommandSource(server,pos)),component);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){return null;}}
    public static CommandSourceStack getCommandSource(ServerLevel level,BlockPos pos){return new CommandSourceStack(CommandSource.NULL,Vec3.atCenterOf(pos),Vec2.ZERO,level,LevelBasedPermissionSet.GAMEMASTER,Component.literal(Create.ID),level.getServer());}
}
