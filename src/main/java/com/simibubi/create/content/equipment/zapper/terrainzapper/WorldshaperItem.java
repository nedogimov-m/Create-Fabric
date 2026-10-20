package com.simibubi.create.content.equipment.zapper.terrainzapper;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.zapper.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WorldshaperItem extends ZapperItem {
    public WorldshaperItem(Properties properties){super(properties);}
    @Override protected void openHandgunGUI(ItemStack stack,InteractionHand hand){if(OPEN_SCREEN==null)throw new IllegalStateException("Native Worldshaper client screen registration missing");OPEN_SCREEN.accept(stack,hand);}
    @Override protected int getZappingRange(ItemStack stack){return 128;}
    @Override protected int getCooldownDelay(ItemStack stack){return 2;}
    @Override public Component validateUsage(ItemStack stack){var params=stack.get(AllDataComponents.SHAPER_BRUSH_PARAMS);if(params==null)return Component.translatable("create.terrainzapper.shiftRightClickToSet");if(!validSettings(stack.getOrDefault(AllDataComponents.SHAPER_BRUSH,TerrainBrushes.Cuboid),params,stack.getOrDefault(AllDataComponents.SHAPER_TOOL,TerrainTools.Fill)))return Component.translatable("create.terrainzapper.shiftRightClickToSet");return super.validateUsage(stack);}
    @Override protected boolean canActivateWithoutSelectedBlock(ItemStack stack){return !stack.getOrDefault(AllDataComponents.SHAPER_TOOL,TerrainTools.Fill).requiresSelectedBlock();}
    @Override protected boolean activate(Level level,Player player,ItemStack stack,BlockState state,BlockHitResult hit,CompoundTag data){
        var brush=stack.getOrDefault(AllDataComponents.SHAPER_BRUSH,TerrainBrushes.Cuboid).get();var params=stack.get(AllDataComponents.SHAPER_BRUSH_PARAMS);var placement=stack.getOrDefault(AllDataComponents.SHAPER_PLACEMENT_OPTIONS,PlacementOptions.Merged);var tool=stack.getOrDefault(AllDataComponents.SHAPER_TOOL,TerrainTools.Fill);if(params==null||validateUsage(stack)!=null)return false;
        brush.set(params.getX(),params.getY(),params.getZ());var target=hit.getBlockPos().offset(brush.getOffset(player.getLookAngle(),hit.getDirection(),placement));var affected=new ArrayList<BlockPos>();brush.addToGlobalPositions(level,target,hit.getDirection(),affected,tool);affected.removeIf(pos->!ZapperWorldAccess.canEdit(level,player,pos));PlacementPatterns.applyPattern(affected,stack,level.getRandom());brush.redirectTool(tool).run(level,affected,hit.getDirection(),state,data,player);return true;
    }
    public static boolean validSettings(TerrainBrushes kind,BlockPos params,TerrainTools tool){if(kind==null||params==null||tool==null)return false;var brush=kind.get();if(Arrays.stream(brush.getSupportedTools()).noneMatch(value->value==tool))return false;for(int i=0;i<3;i++){int value=i==0?params.getX():i==1?params.getY():params.getZ();if(brush.hasConnectivityOptions()&&i>0){if(value<0||value>1)return false;}else if(value<brush.getMin(i)||value>brush.getMax(i))return false;}return true;}
    public static void configureSettings(ItemStack stack,PlacementPatterns pattern,TerrainBrushes brush,int x,int y,int z,TerrainTools tool,PlacementOptions placement){var params=new BlockPos(x,y,z);if(pattern==null||placement==null||!validSettings(brush,params,tool))throw new IllegalArgumentException("Worldshaper settings outside pinned source GUI bounds/tools");stack.set(AllDataComponents.PLACEMENT_PATTERN,pattern);stack.set(AllDataComponents.SHAPER_BRUSH,brush);stack.set(AllDataComponents.SHAPER_BRUSH_PARAMS,params);stack.set(AllDataComponents.SHAPER_TOOL,tool);stack.set(AllDataComponents.SHAPER_PLACEMENT_OPTIONS,placement);}
}
