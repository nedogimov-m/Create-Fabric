package com.simibubi.create.test;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual addon registration before freeze; no builtin catalyst/tag overrides. */
public final class FanProcessingApiTestFixtures implements ModInitializer {
    public static final String PREFIX="Fan API original finite stock ";
    public static final TestType LOW=new TestType(900,Items.DIAMOND);
    public static final TestType HIGH=new TestType(1000,Items.EMERALD);
    public static final Identifier HIGH_ID=Identifier.fromNamespaceAndPath("create_test","fan_api_high");
    private static final ConcurrentHashMap<String,AtomicInteger> HIGH_CALLS=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,AtomicInteger> LOW_CALLS=new ConcurrentHashMap<>();
    @Override public void onInitialize(){
        Registry.register(CreateBuiltInRegistries.FAN_PROCESSING_TYPE,Identifier.fromNamespaceAndPath("create_test","fan_api_low"),LOW);
        Registry.register(CreateBuiltInRegistries.FAN_PROCESSING_TYPE,HIGH_ID,HIGH);
    }
    public static int calls(String key,boolean high){var value=(high?HIGH_CALLS:LOW_CALLS).get(key);return value==null?0:value.get();}
    public static String marker(ItemStack input){var name=input.get(DataComponents.CUSTOM_NAME);return name==null?"":name.getString();}
    public static final class TestType implements FanProcessingType {
        private final int priority;private final Item output;
        TestType(int priority,Item output){this.priority=priority;this.output=output;}
        @Override public boolean isValidAt(Level level,BlockPos pos){return level.getBlockState(pos).is(Blocks.TORCH);}
        @Override public int getPriority(){return priority;}
        @Override public boolean canProcess(ItemStack stack,Level level){return stack.is(Items.STICK)&&marker(stack).startsWith(PREFIX);}
        @Override public List<ItemStack> process(ItemStack stack,Level level){
            if(!canProcess(stack,level))return null;
            var name=stack.get(DataComponents.CUSTOM_NAME);int count=stack.getCount();
            (this==HIGH?HIGH_CALLS:LOW_CALLS).computeIfAbsent(marker(stack),ignored->new AtomicInteger()).incrementAndGet();
            // Callback input is private: changing it must not debit the inventory before admission.
            stack.setCount(1);
            var result=new ItemStack(output,count);result.set(DataComponents.CUSTOM_NAME,name);return List.of(result);
        }
        @Override public void spawnProcessingParticles(Level level,Vec3 pos){}
        @Override public void morphAirFlow(AirFlowParticleAccess access,RandomSource random){}
        @Override public void affectEntity(Entity entity,Level level){
            if(this==HIGH&&!level.isClientSide()&&entity instanceof LivingEntity living)
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING,30,0,false,false));
        }
    }
}
