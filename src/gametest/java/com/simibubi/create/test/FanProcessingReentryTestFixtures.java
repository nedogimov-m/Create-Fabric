package com.simibubi.create.test;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real registered addon callback; all input movement is performed by public native owners. */
public final class FanProcessingReentryTestFixtures implements ModInitializer {
    public static final String PREFIX="Fan reentry original ";
    private static final ConcurrentHashMap<String,Consumer<Level>> CALLBACKS=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,AtomicInteger> CALLS=new ConcurrentHashMap<>();
    public static final FanProcessingType TYPE=new FanProcessingType(){
        @Override public boolean isValidAt(Level level,BlockPos pos){return level.getBlockState(pos).is(Blocks.SOUL_TORCH);}
        @Override public int getPriority(){return 1100;}
        @Override public boolean canProcess(ItemStack input,Level level){return input.is(Items.STICK)&&marker(input).startsWith(PREFIX);}
        @Override public List<ItemStack> process(ItemStack input,Level level){
            if(!canProcess(input,level))return null;
            String key=marker(input);CALLS.computeIfAbsent(key,ignored->new AtomicInteger()).incrementAndGet();
            var callback=CALLBACKS.remove(key);if(callback!=null)callback.accept(level);
            var output=new ItemStack(Items.EMERALD,input.getCount());output.set(DataComponents.CUSTOM_NAME,input.get(DataComponents.CUSTOM_NAME));
            if(key.contains("entity-admission")){var extra=new ItemStack(Items.DIAMOND);extra.set(DataComponents.CUSTOM_NAME,input.get(DataComponents.CUSTOM_NAME));return List.of(output,extra);}
            return List.of(output);
        }
        @Override public void spawnProcessingParticles(Level level,Vec3 pos){}
        @Override public void morphAirFlow(AirFlowParticleAccess access,RandomSource random){}
        @Override public void affectEntity(Entity entity,Level level){}
    };
    @Override public void onInitialize(){Registry.register(CreateBuiltInRegistries.FAN_PROCESSING_TYPE,Identifier.fromNamespaceAndPath("create_test","fan_reentry"),TYPE);}
    public static String marker(ItemStack input){var name=input.get(DataComponents.CUSTOM_NAME);return name==null?"":name.getString();}
    public static void arm(ItemStack input,Consumer<Level> callback){if(CALLBACKS.putIfAbsent(marker(input),callback)!=null)throw new IllegalStateException("Duplicate active native reentry fixture");}
    public static void disarm(ItemStack input){CALLBACKS.remove(marker(input));}
    public static int calls(ItemStack input){var count=CALLS.get(marker(input));return count==null?0:count.get();}
}
