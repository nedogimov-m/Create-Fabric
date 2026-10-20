package com.simibubi.create.content.schematics;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.table.*;
import com.simibubi.create.content.schematics.packet.SchematicUploadStatusPacket;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Actual live table/menu uploads; incomplete files never replace complete schematics or original fees. */
public final class ServerSchematicLoader {
    private static final Map<MinecraftServer,ServerSchematicLoader> SERVERS=new WeakHashMap<>();
    private static final class Upload {
        final UUID token,player;
        final int menu,packetBytes;
        final String file,owner;
        final long expected;
        final SchematicTableBlockEntity table;
        final Path temporary,target;
        final OutputStream stream;
        long received,lastActivity;
        Upload(UUID token,ServerPlayer player,int menu,String file,String owner,long expected,SchematicTableBlockEntity table,Path temporary,Path target,OutputStream stream,int packetBytes,long tick){this.token=token;this.player=player.getUUID();this.menu=menu;this.file=file;this.owner=owner;this.expected=expected;this.table=table;this.temporary=temporary;this.target=target;this.stream=stream;this.packetBytes=packetBytes;lastActivity=tick;}
    }
    private final MinecraftServer server;
    private final Path root;
    private final Map<UUID,Upload> uploads=new LinkedHashMap<>();
    private ServerSchematicLoader(MinecraftServer server,Path root){this.server=Objects.requireNonNull(server);this.root=root.toAbsolutePath().normalize();}
    public static ServerSchematicLoader get(MinecraftServer server){return SERVERS.computeIfAbsent(server,s->new ServerSchematicLoader(s,SchematicFiles.uploadedRoot()));}
    public MinecraftServer server(){return server;}
    public int activeUploads(){return uploads.size();}
    public boolean handleInstantSchematic(ServerPlayer player,String schematic,BlockPos origin,BlockPos bounds){
        if(player.level().getServer()!=server||server.getPlayerList().getPlayer(player.getUUID())!=player||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().is(AllItems.SCHEMATIC_AND_QUILL)||bounds.getX()<1||bounds.getY()<1||bounds.getZ()<1)return false;
        var held=player.getMainHandItem();
        try{
            var file=SchematicFiles.fileName(schematic);var owner=SchematicFiles.ownerName(player.getName().getString());var last=new BlockPos(Math.addExact(origin.getX(),bounds.getX()-1),Math.addExact(origin.getY(),bounds.getY()-1),Math.addExact(origin.getZ(),bounds.getZ()-1));
            if(!player.level().mayInteract(player,origin)||!player.level().mayInteract(player,last))return false;
            var tag=SchematicExport.capture(player.level(),origin,last);var path=SchematicFiles.writeAtomic(root.resolve(owner),file,tag,true,Long.MAX_VALUE);
            if(player.getMainHandItem()!=held)return false;var blueprint=SchematicItem.create(player.level(),file,owner);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,blueprint);trimCompletedFiles(path.getParent(),file);return true;
        }catch(IOException|IllegalArgumentException|ArithmeticException failure){Create.LOGGER.warn("Unable to complete actual native instant quill export {}",schematic,failure);player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.schematicAndQuill.instant_failed").withStyle(net.minecraft.ChatFormatting.RED));return false;}
    }
    public boolean isActive(SchematicTableBlockEntity table,UUID token){return uploads.values().stream().anyMatch(upload->upload.table==table&&upload.token.equals(token));}
    private long now(){return server.overworld().getGameTime();}
    private boolean ownsMenu(ServerPlayer player,int id,SchematicTableBlockEntity table){return player.level().getServer()==server&&server.getPlayerList().getPlayer(player.getUUID())==player&&player.containerMenu instanceof SchematicTableMenu menu&&menu.containerId==id&&menu.owner==table&&menu.stillValid(player);}
    private boolean current(Upload upload){var player=server.getPlayerList().getPlayer(upload.player);return player!=null&&ownsMenu(player,upload.menu,upload.table)&&upload.table.ownsUpload(this,upload.token);}
    public boolean handleNewUpload(ServerPlayer player,int menu,UUID token,String schematic,long size,BlockPos pos){
        if(uploads.containsKey(player.getUUID())||size<=0||size>SchematicConfig.get().maxTotalSchematicSize()*1000L||!(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(player.level(),pos) instanceof SchematicTableBlockEntity table)||!ownsMenu(player,menu,table))return false;
        Path temporary=null;OutputStream stream=null;
        try{
            String file=SchematicFiles.fileName(schematic),owner=SchematicFiles.ownerName(player.getName().getString());var target=SchematicFiles.contained(root.resolve(owner),file);Files.createDirectories(target.getParent());temporary=Files.createTempFile(target.getParent(),".create-upload-",".pending");stream=Files.newOutputStream(temporary,StandardOpenOption.WRITE);
            if(!table.beginUpload(this,token,file)){stream.close();Files.deleteIfExists(temporary);return false;}
            var entry=new Upload(token,player,menu,file,owner,size,table,temporary,target,stream,SchematicConfig.get().maxSchematicPacketSize(),now());uploads.put(player.getUUID(),entry);status(entry,SchematicUploadStatusPacket.Stage.ACCEPTED);return true;
        }catch(IOException|IllegalArgumentException failure){
            if(stream!=null)try{stream.close();}catch(IOException close){failure.addSuppressed(close);}if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException cleanup){failure.addSuppressed(cleanup);}Create.LOGGER.warn("Unable to start native schematic upload {}",schematic,failure);return false;
        }
    }
    public boolean handleWriteRequest(ServerPlayer player,UUID token,long offset,byte[] bytes){
        var upload=uploads.get(player.getUUID());if(upload==null||!upload.token.equals(token))return false;
        if(!current(upload)||bytes.length==0||bytes.length>upload.packetBytes||offset!=upload.received||bytes.length>upload.expected-upload.received){cancel(upload);return false;}
        try{upload.stream.write(bytes);upload.received+=bytes.length;upload.lastActivity=now();upload.table.uploadProgress(this,token,upload.received,upload.expected);return true;}catch(IOException failure){Create.LOGGER.warn("Native schematic upload write failed",failure);cancel(upload);return false;}
    }
    public boolean handleFinishedUpload(ServerPlayer player,UUID token){
        var upload=uploads.get(player.getUUID());if(upload==null||!upload.token.equals(token))return false;
        if(!current(upload)||upload.received!=upload.expected){cancel(upload);return false;}
        try{
            upload.stream.close();var tag=SchematicFiles.readTag(upload.temporary);var template=SchematicFiles.decode(player.level(),tag);
            if(!current(upload)){cancel(upload);return false;}
            SchematicFiles.moveCompleted(upload.temporary,upload.target,true);
            // Use metadata from the actual validated completed native structure.
            var result=new ItemStack(AllItems.SCHEMATIC);result.set(AllDataComponents.SCHEMATIC_DEPLOYED,false);result.set(AllDataComponents.SCHEMATIC_OWNER,upload.owner);result.set(AllDataComponents.SCHEMATIC_FILE,upload.file);result.set(AllDataComponents.SCHEMATIC_ANCHOR,BlockPos.ZERO);result.set(AllDataComponents.SCHEMATIC_ROTATION,net.minecraft.world.level.block.Rotation.NONE);result.set(AllDataComponents.SCHEMATIC_MIRROR,net.minecraft.world.level.block.Mirror.NONE);result.set(AllDataComponents.SCHEMATIC_BOUNDS,template.getSize());
            if(!upload.table.completeUpload(this,token,result)){cancel(upload);return false;}
            uploads.remove(upload.player);status(upload,SchematicUploadStatusPacket.Stage.COMPLETED);trimCompletedFiles(upload.target.getParent(),upload.file);return true;
        }catch(IOException|IllegalArgumentException failure){Create.LOGGER.warn("Unable to complete actual native schematic upload",failure);cancel(upload);return false;}
    }
    private void trimCompletedFiles(Path directory,String keep){
        try{var files=new ArrayList<>(SchematicFiles.list(directory));files.remove(keep);files.sort(Comparator.comparingLong(name->{try{return Files.getLastModifiedTime(directory.resolve(name),LinkOption.NOFOLLOW_LINKS).toMillis();}catch(IOException failure){throw new UncheckedIOException(failure);}}));int remove=Math.max(0,files.size()+1-SchematicConfig.get().maxSchematics());for(int i=0;i<remove;i++)Files.delete(directory.resolve(files.get(i)));}catch(IOException|UncheckedIOException failure){Create.LOGGER.warn("Unable to prune an old completed schematic after successful native output",failure);}
    }
    public void tick(){for(var upload:List.copyOf(uploads.values()))if(!current(upload)||now()-upload.lastActivity>SchematicConfig.get().schematicIdleTimeout())cancel(upload);}
    public void cancel(SchematicTableBlockEntity table,UUID token){for(var upload:List.copyOf(uploads.values()))if(upload.table==table&&upload.token.equals(token))cancel(upload);}
    public void cancelForMenu(ServerPlayer player,int menu){var upload=uploads.get(player.getUUID());if(upload!=null&&upload.menu==menu)cancel(upload);}
    public boolean cancelForMenu(ServerPlayer player,int menu,UUID token){var upload=uploads.get(player.getUUID());if(upload==null||upload.menu!=menu||!upload.token.equals(token))return false;cancel(upload);return true;}
    private void status(Upload upload,SchematicUploadStatusPacket.Stage stage){var player=server.getPlayerList().getPlayer(upload.player);if(player!=null&&ServerPlayNetworking.canSend(player,SchematicUploadStatusPacket.TYPE))ServerPlayNetworking.send(player,new SchematicUploadStatusPacket(upload.token,stage,upload.file));}
    private void cancel(Upload upload){
        if(!uploads.remove(upload.player,upload))return;
        try{upload.stream.close();}catch(IOException failure){Create.LOGGER.warn("Unable to close interrupted native schematic upload",failure);}
        try{Files.deleteIfExists(upload.temporary);}catch(IOException failure){Create.LOGGER.warn("Unable to remove incomplete native schematic upload",failure);}
        // Removed/unloaded BE objects never refund into the world. The saved native owner keeps
        // its original private receipt and recovers it when that actual table loads again.
        if(!upload.table.isRemoved()&&upload.table.getLevel()!=null&&com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(upload.table.getLevel(),upload.table.getBlockPos())==upload.table)upload.table.cancelUpload(this,upload.token);
        status(upload,SchematicUploadStatusPacket.Stage.CANCELLED);
    }
    public void shutdown(){for(var upload:List.copyOf(uploads.values()))cancel(upload);}
    public static void register(){ServerTickEvents.END_SERVER_TICK.register(server->get(server).tick());ServerLifecycleEvents.SERVER_STOPPING.register(server->{var loader=SERVERS.remove(server);if(loader!=null)loader.shutdown();});}
}
