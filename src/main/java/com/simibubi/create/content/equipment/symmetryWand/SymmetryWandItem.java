package com.simibubi.create.content.equipment.symmetryWand;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.symmetryWand.mirror.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Source mirror movement and hotbar semantics; the server owns configuration sessions. */
public class SymmetryWandItem extends Item {
    public SymmetryWandItem(Properties properties){super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;var wand=player.getItemInHand(context.getHand());player.getCooldowns().addCooldown(wand,5);
        if(context.getLevel().isClientSide())return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown()){if(player instanceof ServerPlayer server)SymmetryWandNetworking.open(server,context.getHand());return InteractionResult.SUCCESS;}
        if(context.getHand()!=InteractionHand.MAIN_HAND)return InteractionResult.SUCCESS;
        var position=Vec3.atLowerCornerOf(context.getClickedPos().relative(context.getClickedFace()));var mirror=getMirror(wand);
        if(mirror instanceof EmptyMirror)mirror=new PlaneMirror(position);mirror.setPosition(position);
        if(mirror instanceof PlaneMirror)mirror.setOrientation(player.getDirection().getAxis()==Direction.Axis.Z?PlaneMirror.Align.XY.ordinal():PlaneMirror.Align.YZ.ordinal());
        if(mirror instanceof CrossPlaneMirror){float angle=Math.abs(player.getYHeadRot()%90);mirror.setOrientation(angle>22&&angle<67?CrossPlaneMirror.Align.D.ordinal():CrossPlaneMirror.Align.Y.ordinal());}
        configureSettings(wand,mirror);wand.set(AllDataComponents.SYMMETRY_WAND_ENABLE,true);player.getInventory().setChanged();SymmetryWandNetworking.invalidate(player);return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide())return InteractionResult.SUCCESS;var stack=player.getItemInHand(hand);
        if(player.isShiftKeyDown()){player.getCooldowns().addCooldown(stack,5);if(player instanceof ServerPlayer server)SymmetryWandNetworking.open(server,hand);}
        else{checkComponents(stack);stack.set(AllDataComponents.SYMMETRY_WAND_ENABLE,false);player.getInventory().setChanged();SymmetryWandNetworking.invalidate(player);}return InteractionResult.SUCCESS;
    }
    private static void checkComponents(ItemStack stack){if(!stack.has(AllDataComponents.SYMMETRY_WAND)){stack.set(AllDataComponents.SYMMETRY_WAND,SymmetryWandData.EMPTY);stack.set(AllDataComponents.SYMMETRY_WAND_ENABLE,false);}}
    public static boolean isEnabled(ItemStack stack){return stack.getOrDefault(AllDataComponents.SYMMETRY_WAND_ENABLE,false)&&!stack.getOrDefault(AllDataComponents.SYMMETRY_WAND_SIMULATE,false);}
    /** Never hands the mutable stored component to item logic or render extraction. */
    public static SymmetryMirror getMirror(ItemStack stack){var mirror=stack.get(AllDataComponents.SYMMETRY_WAND);return mirror==null?new EmptyMirror(Vec3.ZERO):mirror.mirror();}
    public static void configureSettings(ItemStack stack,SymmetryMirror mirror){checkComponents(stack);stack.set(AllDataComponents.SYMMETRY_WAND,SymmetryWandData.fromMirror(mirror));}
    public static boolean presentInHotbar(Player player){for(int slot=0;slot<Inventory.getSelectionSize();slot++)if(player.getInventory().getItem(slot).getItem() instanceof SymmetryWandItem)return true;return false;}
    public static void apply(Level level,ItemStack wand,Player player,BlockPos position,BlockState state){if(player instanceof ServerPlayer server&&level==server.level())SymmetryHandler.apply(server,wand,position,state);}
    /** Pre-break compatibility API; the native event path retains the actual pre-break state separately. */
    public static void remove(Level level,ItemStack wand,Player player,BlockPos position){if(player instanceof ServerPlayer server&&level==server.level())SymmetryHandler.remove(server,wand,position,com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,position));}
}
