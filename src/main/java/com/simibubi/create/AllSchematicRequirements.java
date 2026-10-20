package com.simibubi.create;

import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.decoration.placard.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Source special requirements for native blocks whose public base classes are already ported. */
public final class AllSchematicRequirements {
    public static void registerDefaults(){SchematicRequirementRegistries.BLOCKS.registerProvider(AllSchematicRequirements::block);}
    private static SchematicRequirementRegistries.BlockRequirement block(Block block){
        if(block instanceof com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<?>)return (state,be)->consume(new ItemStack(AllBlocks.RAILWAY_CASING));
        if(block instanceof com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock)return (state,be)->new ItemRequirement(ItemUseType.CONSUME,List.of(new ItemStack(AllBlocks.CART_ASSEMBLER),new ItemStack(state.getValue(com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock.RAIL_TYPE).getItem())));
        if(block instanceof TrackBlock track)return (state,be)->trackRequirement(track,state,be);
        if(block instanceof FactoryPanelBlock)return (state,be)->ItemRequirement.NONE;
        if(block instanceof BeltFunnelBlock funnel)return (state,be)->consume(new ItemStack(funnel.parent()));
        if(block instanceof com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock||block instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock)return (state,be)->ItemRequirement.of(AllBlocks.FLUID_PIPE.defaultBlockState(),be);
        if(block instanceof com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock)return (state,be)->consume(new ItemStack(AllBlocks.ORANGE_NIXIE_TUBE));
        if(block instanceof com.simibubi.create.content.redstone.link.controller.LecternControllerBlock)return (state,be)->new ItemRequirement(ItemUseType.CONSUME,List.of(new ItemStack(Blocks.LECTERN),new ItemStack(AllItems.LINKED_CONTROLLER)));
        if(block instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock)return (state,be)->ItemRequirement.of(AllBlocks.REDSTONE_CONTACT.defaultBlockState(),be);
        if(block instanceof BlazeBurnerBlock)return (state,be)->consume(new ItemStack(state.getValue(BlazeBurnerBlock.HEAT_LEVEL)==BlazeBurnerBlock.HeatLevel.NONE?AllItems.EMPTY_BLAZE_BURNER:AllItems.BLAZE_BURNER));
        if(block instanceof BeltBlock)return (state,be)->{var required=new ArrayList<ItemStack>();if(state.getValue(BeltBlock.PART)!=BeltPart.MIDDLE)required.add(new ItemStack(AllBlocks.SHAFT));if(state.getValue(BeltBlock.PART)==BeltPart.START)required.add(new ItemStack(AllItems.BELT_CONNECTOR));return required.isEmpty()?ItemRequirement.NONE:new ItemRequirement(ItemUseType.CONSUME,required);};
        if(block instanceof PlacardBlock)return (state,be)->{var base=consume(new ItemStack(AllBlocks.PLACARD));if(be instanceof PlacardBlockEntity placard&&!placard.getHeldItem().isEmpty())return base.union(new ItemRequirement(new ItemRequirement.StrictNbtStackRequirement(placard.getHeldItem().copy(),ItemUseType.CONSUME)));return base;};
        if(block instanceof com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock)return (state,be)->ItemRequirement.of(AllBlocks.SHAFT.defaultBlockState(),be).union(ItemRequirement.of(AllBlocks.METAL_GIRDER.defaultBlockState(),be));
        return null;
    }
    private static ItemRequirement consume(ItemStack stack){return new ItemRequirement(ItemUseType.CONSUME,stack);}
    private static ItemRequirement trackRequirement(TrackBlock block,BlockState state,BlockEntity be){
        var amounts=new LinkedHashMap<TrackMaterial,Integer>();int own=1,girders=0;
        if(be instanceof TrackBlockEntity track)for(var curve:track.getConnections().values())if(curve.isPrimary()){
            int cost=TrackCurveCosts.getTrackItemCost(curve);if(curve.getMaterial()==block.getMaterial())own=Math.addExact(own,cost);else amounts.merge(curve.getMaterial(),cost,Math::addExact);girders=Math.addExact(girders,TrackCurveCosts.getGirderItemCost(curve));
        }
        var stacks=new ArrayList<ItemStack>();split(stacks,state.getBlock().asItem(),own);for(var entry:amounts.entrySet())split(stacks,entry.getKey().asStack().getItem(),entry.getValue());split(stacks,AllBlocks.METAL_GIRDER.asItem(),girders);return new ItemRequirement(ItemUseType.CONSUME,stacks);
    }
    private static void split(List<ItemStack> stacks,Item item,int amount){while(amount>0){int count=Math.min(amount,item.getDefaultMaxStackSize());stacks.add(new ItemStack(item,count));amount-=count;}}
    public static ItemRequirement nativeBlockEntity(BlockEntity be,BlockState state){
        ItemRequirement requirement=ItemRequirement.NONE;
        if(be instanceof FactoryPanelBlockEntity gauge){for(var panel:gauge.panels.values())if(panel.active){requirement=requirement.union(consume(new ItemStack(AllBlocks.FACTORY_GAUGE)));requirement=requirement.union(panel.getRequiredItems());}return requirement;}
        var filtering=BlockEntityBehaviour.get(be,FilteringBehaviour.TYPE);if(filtering!=null)requirement=requirement.union(filtering.getRequiredItems());
        var bracket=BlockEntityBehaviour.get(be,com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour.TYPE);
        if(bracket!=null)requirement=requirement.union(bracket.getRequiredItems());
        return requirement;
    }
    private AllSchematicRequirements(){}
}
