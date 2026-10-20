package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.blueprint.BlueprintEntity.BlueprintSection;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Eleven genuine synchronized ghost slots and thirty-six actual native player inventory slots. */
public final class BlueprintMenu extends AbstractContainerMenu {
 public record Opening(int entity,UUID uuid,int section){public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Opening::entity,UUIDUtil.STREAM_CODEC,Opening::uuid,ByteBufCodecs.VAR_INT,Opening::section,Opening::new);}
 public static final ExtendedMenuType<BlueprintMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("crafting_blueprint"),new ExtendedMenuType<>(BlueprintMenu::client,Opening.CODEC));
 public final Inventory playerInventory;public final BlueprintSection section;private final Opening opening;private final SimpleContainer ghosts;private final ContainerData flags=new SimpleContainerData(1);
 private static BlueprintMenu client(int id,Inventory inventory,Opening opening){var entity=inventory.player.level().getEntity(opening.entity());var section=entity instanceof BlueprintEntity blueprint&&blueprint.getUUID().equals(opening.uuid())&&opening.section()>=0&&opening.section()<blueprint.size()*blueprint.size()?blueprint.getSection(opening.section()):null;return new BlueprintMenu(id,inventory,section,opening);}
 public BlueprintMenu(int id,Inventory inventory,BlueprintSection section){this(id,inventory,section,new Opening(section.blueprint().getId(),section.blueprint().getUUID(),section.index));}
 private BlueprintMenu(int id,Inventory inventory,BlueprintSection section,Opening opening){super(TYPE,id);this.playerInventory=inventory;this.section=section;this.opening=opening;this.ghosts=section==null?new SimpleContainer(11):section.getItems();flags.set(0,section!=null&&section.inferredIcon()?1:0);addDataSlots(flags);addStandardInventorySlots(inventory,8,131);
  for(int i=0;i<11;i++){int slot=i;addSlot(new Slot(ghosts,i,i<9?29+i%3*18:i==9?123:135,i<9?21+i/3*18:i==9?40:57){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return false;}@Override public boolean isFake(){return true;}});}
 }
 public static void register(){}
 public Opening opening(){return opening;}
 public boolean inferredIcon(){return flags.get(0)!=0;}
 public SimpleContainer ghostItems(){return ghosts;}
 @Override public boolean stillValid(Player player){return player.getInventory()==playerInventory&&section!=null&&section.blueprint().getId()==opening.entity()&&section.blueprint().getUUID().equals(opening.uuid())&&section.index==opening.section()&&section.canPlayerUse(player);}
 private void save(){if(!playerInventory.player.level().isClientSide()&&stillValid(playerInventory.player))section.save(ghosts,inferredIcon());}
 private void changed(int index){if(playerInventory.player.level().isClientSide())return;if(index==9)flags.set(0,0);if(index<9)inferOutput();save();broadcastChanges();}
 private void inferOutput(){
  var player=playerInventory.player;var level=player.level();if(level.isClientSide()||!stillValid(player))return;
  var grid=new ArrayList<ItemStack>(9);for(int i=0;i<9;i++)grid.add(ghosts.getItem(i).copy());var input=CraftingInput.of(3,3,grid);
  var found=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level);
  if(found.isPresent()){ghosts.setItem(9,found.get().value().assemble(input));flags.set(0,1);}else if(inferredIcon()){ghosts.setItem(9,ItemStack.EMPTY);flags.set(0,0);}
 }
 @Override public void clicked(int index,int button,ContainerInput input,Player player){
  if(!player.level().isClientSide()&&!stillValid(player))return;
  if(index>=36&&index<47){if(input==ContainerInput.PICKUP||input==ContainerInput.QUICK_MOVE){ghosts.setItem(index-36,getCarried().copyWithCount(1));changed(index-36);}return;}
  super.clicked(index,button,input,player);
 }
 @Override public ItemStack quickMoveStack(Player player,int index){if(!stillValid(player)||index<0||index>=47)return ItemStack.EMPTY;if(index>=36){ghosts.setItem(index-36,ItemStack.EMPTY);changed(index-36);return ItemStack.EMPTY;}var actual=getSlot(index).getItem();if(actual.isEmpty())return ItemStack.EMPTY;for(int slot=0;slot<11;slot++)if(ghosts.getItem(slot).isEmpty()){ghosts.setItem(slot,actual.copyWithCount(1));changed(slot);break;}return ItemStack.EMPTY;}
 @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.container==playerInventory;}
 @Override public boolean canDragTo(Slot slot){return slot.container==playerInventory;}
 @Override public boolean clickMenuButton(Player player,int button){if(button!=0||!stillValid(player))return false;ghosts.clearContent();flags.set(0,0);save();broadcastChanges();return true;}
 public boolean assign(Player player,CraftingRecipe recipe){if(!stillValid(player)||player.level().isClientSide())return false;var snapshot=section.snapshot();var converted=new SimpleContainer(11);for(int i=0;i<11;i++)converted.setItem(i,ghosts.getItem(i).copy());if(!BlueprintIngredients.assignCompleteRecipe(player.level(),converted,recipe)||!stillValid(player)||!snapshot.equals(section.snapshot()))return false;for(int i=0;i<11;i++)ghosts.setItem(i,converted.getItem(i));flags.set(0,0);save();broadcastChanges();return true;}
 @Override public void removed(Player player){save();super.removed(player);}
}
