package com.simibubi.create.content.schematics;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllStructureProcessorTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.TagValueInput;

/** Modern native processor interface; entity requirements/filtering are handled by the printer. */
public final class SchematicProcessor implements StructureProcessor {
    public static final SchematicProcessor INSTANCE=new SchematicProcessor();
    public static final MapCodec<SchematicProcessor> CODEC=MapCodec.unit(()->INSTANCE);
    @Override public MapCodec<? extends StructureProcessor> codec(){return AllStructureProcessorTypes.SCHEMATIC;}
    @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader world,BlockPos origin,BlockPos pivot,BlockPos relative,StructureTemplate.StructureBlockInfo info,StructurePlaceSettings settings){
        if(info.nbt()==null||!(world instanceof Level level)||!(info.state().getBlock() instanceof EntityBlock entityBlock))return info;
        var entity=entityBlock.newBlockEntity(info.pos(),info.state());if(entity==null)return new StructureTemplate.StructureBlockInfo(info.pos(),info.state(),null);
        entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),info.nbt()));
        return new StructureTemplate.StructureBlockInfo(info.pos(),info.state(),SchematicNbt.prepare(level,info.state(),entity));
    }
    private SchematicProcessor(){}
}
