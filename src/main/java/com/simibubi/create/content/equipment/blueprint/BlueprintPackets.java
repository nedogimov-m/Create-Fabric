package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

/** Clients name an actual section or server recipe; all material/output decisions stay on the server. */
public final class BlueprintPackets {
 public record Query(BlueprintMenu.Opening target,int revision,boolean batch) implements CustomPacketPayload{
  public static final Type<Query> TYPE=new Type<>(Create.asResource("blueprint_preview_query"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.composite(BlueprintMenu.Opening.CODEC,Query::target,ByteBufCodecs.VAR_INT,Query::revision,ByteBufCodecs.BOOL,Query::batch,Query::new);
  @Override public Type<Query> type(){return TYPE;}
 }
 public record Preview(BlueprintMenu.Opening target,int revision,boolean batch,boolean empty,boolean craftable,ItemStack result,List<ItemStack> available,List<ItemStack> missing) implements CustomPacketPayload{
  private static final StreamCodec<RegistryFriendlyByteBuf,List<ItemStack>> SAMPLES=ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(9));
  public static final Type<Preview> TYPE=new Type<>(Create.asResource("blueprint_preview"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Preview> CODEC=StreamCodec.of((buf,p)->{BlueprintMenu.Opening.CODEC.encode(buf,p.target);buf.writeVarInt(p.revision);buf.writeBoolean(p.batch);buf.writeBoolean(p.empty);buf.writeBoolean(p.craftable);ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,p.result);SAMPLES.encode(buf,p.available);SAMPLES.encode(buf,p.missing);},buf->new Preview(BlueprintMenu.Opening.CODEC.decode(buf),buf.readVarInt(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),SAMPLES.decode(buf),SAMPLES.decode(buf)));
  @Override public Type<Preview> type(){return TYPE;}
 }
 public record Assign(int menu,BlueprintMenu.Opening target,Identifier recipe) implements CustomPacketPayload{
  public static final Type<Assign> TYPE=new Type<>(Create.asResource("blueprint_assign_complete_recipe"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Assign> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Assign::menu,BlueprintMenu.Opening.CODEC,Assign::target,Identifier.STREAM_CODEC,Assign::recipe,Assign::new);
  @Override public Type<Assign> type(){return TYPE;}
 }
 private static final Map<ServerPlayer,Long> LAST_QUERY=new WeakHashMap<>();
 public static void query(ServerPlayer player,Query query){
  if(query.revision<0)return;var level=player.level();long now=level.getGameTime();var previous=LAST_QUERY.get(player);if(previous!=null&&now-previous<5)return;
  var target=query.target;var entity=level.getEntity(target.entity());if(!(entity instanceof BlueprintEntity blueprint)||!blueprint.getUUID().equals(target.uuid())||target.section()<0||target.section()>=blueprint.size()*blueprint.size())return;
  var section=blueprint.getSection(target.section());if(!section.canPlayerUse(player))return;LAST_QUERY.put(player,now);
  var preview=BlueprintCrafting.preview(player,section,query.batch);if(preview!=null)ServerPlayNetworking.send(player,new Preview(target,query.revision,query.batch,preview.empty(),preview.craftable(),preview.result(),preview.available(),preview.missing()));
 }
 public static void assign(ServerPlayer player,Assign packet){if(!(player.containerMenu instanceof BlueprintMenu menu)||menu.containerId!=packet.menu||!menu.opening().equals(packet.target)||!menu.stillValid(player))return;var recipe=player.level().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,packet.recipe));if(recipe.isPresent()&&recipe.get().value() instanceof CraftingRecipe crafting)menu.assign(player,crafting);}
 public static void register(){PayloadTypeRegistry.serverboundPlay().register(Query.TYPE,Query.CODEC);PayloadTypeRegistry.serverboundPlay().register(Assign.TYPE,Assign.CODEC);PayloadTypeRegistry.clientboundPlay().register(Preview.TYPE,Preview.CODEC);ServerPlayNetworking.registerGlobalReceiver(Query.TYPE,(packet,context)->query(context.player(),packet));ServerPlayNetworking.registerGlobalReceiver(Assign.TYPE,(packet,context)->assign(context.player(),packet));}
 private BlueprintPackets(){}
}
