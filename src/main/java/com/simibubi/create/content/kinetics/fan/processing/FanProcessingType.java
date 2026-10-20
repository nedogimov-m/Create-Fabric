package com.simibubi.create.content.kinetics.fan.processing;

import java.util.List;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Source extension contract; a registered type is the actual processing strategy. */
public interface FanProcessingType {
    boolean isValidAt(Level level,BlockPos pos);
    int getPriority();
    boolean canProcess(ItemStack stack,Level level);
    @Nullable List<ItemStack> process(ItemStack stack,Level level);
    void spawnProcessingParticles(Level level,Vec3 pos);
    void morphAirFlow(AirFlowParticleAccess particleAccess,RandomSource random);
    void affectEntity(Entity entity,Level level);

    static @Nullable FanProcessingType parse(String name){
        var id=Identifier.tryParse(name);
        return id==null?null:CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getValue(id);
    }
    static @Nullable FanProcessingType getAt(Level level,BlockPos pos){
        FanProcessingTypeRegistry.ensureInitialized();
        for(var type:FanProcessingTypeRegistry.SORTED_TYPES_VIEW)if(type.isValidAt(level,pos))return type;
        return null;
    }
    interface AirFlowParticleAccess {
        void setColor(int color);
        void setAlpha(float alpha);
        void spawnExtraParticle(ParticleOptions options,float speedMultiplier);
    }
}
