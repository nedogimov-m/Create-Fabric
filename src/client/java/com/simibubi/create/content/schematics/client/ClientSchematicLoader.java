package com.simibubi.create.content.schematics.client;

import com.simibubi.create.Create;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.packet.*;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Source local-file browser and actual ACK-correlated bounded upload, with deterministic stream cleanup. */
public final class ClientSchematicLoader {
    public static final int PACKET_DELAY=10;
    private final List<Component> availableSchematics=new ArrayList<>();
    private InputStream stream;
    private UUID token;
    private String file;
    private int menu,packetBytes,packetCycle,ackTicks;
    private long expected,offset;
    private boolean accepted,finishing;
    private String error="";
    public ClientSchematicLoader(){}
    public List<Component> getAvailableSchematics(){return List.copyOf(availableSchematics);}
    public boolean isUploading(){return token!=null;}
    public String uploadingFile(){return file==null?"":file;}
    public float progress(){return expected==0?0:(float)(offset/(double)expected);}
    public String error(){return error;}
    public void refresh(){availableSchematics.clear();try{Files.createDirectories(SchematicFiles.localRoot());for(var name:SchematicFiles.list(SchematicFiles.localRoot()))availableSchematics.add(Component.literal(name));availableSchematics.sort((a,b)->natural(a.getString(),b.getString()));}catch(IOException failure){error="create.schematics.wrongFormat";Create.LOGGER.error("Failed to refresh actual local schematic files",failure);}}
    private static int natural(String left,String right){int a=0,b=0;while(a<left.length()&&b<right.length()){
        if(Character.isDigit(left.charAt(a))&&Character.isDigit(right.charAt(b))){int ae=a,be=b;while(ae<left.length()&&Character.isDigit(left.charAt(ae)))ae++;while(be<right.length()&&Character.isDigit(right.charAt(be)))be++;var x=left.substring(a,ae);var y=right.substring(b,be);var nx=x.replaceFirst("^0+(?!$)","");var ny=y.replaceFirst("^0+(?!$)","");int comparison=Integer.compare(nx.length(),ny.length());if(comparison==0)comparison=nx.compareTo(ny);if(comparison!=0)return comparison;a=ae;b=be;continue;}int comparison=Character.compare(left.charAt(a++),right.charAt(b++));if(comparison!=0)return comparison;
    }return Integer.compare(left.length(),right.length());}
    public boolean startNewUpload(String requested){
        var client=Minecraft.getInstance();if(client.player==null||client.level==null||!(client.player.containerMenu instanceof SchematicTableMenu table)||isUploading())return false;
        try{var path=SchematicFiles.contained(SchematicFiles.localRoot(),requested);long length=Files.size(path);if(!validateSizeLimitation(length)||!isGZIPEncoded(path.toFile())){fail("create.schematics.wrongFormat");return false;}SchematicFiles.read(client.level,path);stream=Files.newInputStream(path);token=UUID.randomUUID();file=requested;menu=table.containerId;packetBytes=table.opening.packetBytes();expected=length;offset=0;packetCycle=0;ackTicks=0;accepted=false;finishing=false;error="";ClientPlayNetworking.send(SchematicUploadPacket.begin(menu,token,file,expected,table.opening.pos()));return true;}catch(IOException|RuntimeException failure){Create.LOGGER.error("Unable to start actual native schematic upload {}",requested,failure);cancel(false);fail("create.schematics.wrongFormat");return false;}
    }
    public static boolean validateSizeLimitation(long size){var client=Minecraft.getInstance();long maximum=client.player!=null&&client.player.containerMenu instanceof SchematicTableMenu table?table.opening.maximumBytes():SchematicConfig.get().maxTotalSchematicSize()*1000L;if(size>0&&size<=maximum)return true;if(client.player!=null){client.player.sendOverlayMessage(Component.translatable("create.schematics.uploadTooLarge").append(" ("+size/1000+" KB). ").append(Component.translatable("create.schematics.maxAllowedSize")).append(" "+maximum/1000+" KB").withStyle(ChatFormatting.RED));}return false;}
    public static boolean isGZIPEncoded(File file){try(var input=Files.newInputStream(file.toPath())){return input.read()==0x1F&&input.read()==0x8B;}catch(IOException failure){return false;}}
    public void tick(){
        if(!isUploading())return;var client=Minecraft.getInstance();if(client.level==null||client.player==null||!(client.player.containerMenu instanceof SchematicTableMenu table)||table.containerId!=menu){cancel(client.player!=null);return;}
        // Wait for the actual server session to own its original fee before sending any bytes.
        if(!accepted||finishing){if(++ackTicks>SchematicConfig.get().schematicIdleTimeout())cancel(true);return;}if(packetCycle-->0)return;packetCycle=PACKET_DELAY;
        try{int count=(int)Math.min(packetBytes,expected-offset);if(count>0){var bytes=stream.readNBytes(count);if(bytes.length!=count)throw new EOFException("Actual schematic source file changed during upload");ClientPlayNetworking.send(SchematicUploadPacket.write(menu,token,offset,bytes));offset+=bytes.length;}if(offset==expected){if(stream.read()!= -1)throw new IOException("Actual schematic source file grew during upload");stream.close();stream=null;finishing=true;ackTicks=0;ClientPlayNetworking.send(SchematicUploadPacket.finish(menu,token));}}catch(IOException failure){Create.LOGGER.error("Actual native schematic upload stream failed",failure);cancel(true);fail("create.schematicAndQuill.failed");}
    }
    public void status(SchematicUploadStatusPacket status){if(token==null||!token.equals(status.token())||!Objects.equals(file,status.file()))return;switch(status.stage()){case ACCEPTED-> {accepted=true;ackTicks=0;}case COMPLETED->{cancel(false);refresh();}case CANCELLED->{cancel(false);fail("create.schematicAndQuill.failed");}}}
    public void cancel(boolean notifyServer){if(notifyServer&&token!=null&&ClientPlayNetworking.canSend(SchematicUploadPacket.TYPE))ClientPlayNetworking.send(SchematicUploadPacket.cancel(menu,token));if(stream!=null)try{stream.close();}catch(IOException failure){Create.LOGGER.warn("Unable to close native schematic source stream",failure);}stream=null;token=null;file=null;expected=offset=0;accepted=finishing=false;}
    private void fail(String key){error=key;var player=Minecraft.getInstance().player;if(player!=null)player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.RED));}
}
