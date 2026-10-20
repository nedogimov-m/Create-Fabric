package com.simibubi.create.content.contraptions.minecart.capability;

import com.simibubi.create.content.contraptions.minecart.CouplingPhysics;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;

/** Index of actual native loaded cart attachments. Missing endpoints never produce replacement entities. */
public final class CapabilityMinecartController {
    private static final Map<Level,Map<UUID,MinecartController>> LOADED=new WeakHashMap<>();
    private static Map<UUID,MinecartController> index(Level level){return LOADED.computeIfAbsent(level,key->new LinkedHashMap<>());}
    public static void loaded(AbstractMinecart cart){var controller=MinecartController.get(cart);var previous=index(cart.level()).get(cart.getUUID());if(previous!=null&&previous.cart()!=cart&&previous.isPresent())throw new IllegalStateException("Two actual loaded carts claim one native UUID");index(cart.level()).put(cart.getUUID(),controller);}
    public static void unloaded(AbstractMinecart cart){var map=LOADED.get(cart.level());var controller=map==null?null:map.get(cart.getUUID());if(controller!=null&&controller.cart()==cart)map.remove(cart.getUUID());}
    public static MinecartController getIfPresent(Level level,UUID id){if(id==null)return null;var map=LOADED.get(level);var controller=map==null?null:map.get(id);return controller!=null&&controller.isPresent()?controller:null;}
    public static List<MinecartController> loaded(Level level){var map=LOADED.get(level);return map==null?List.of():List.copyOf(map.values());}
    public static void tick(Level level){
        var map=LOADED.get(level);if(map!=null)map.entrySet().removeIf(entry->entry.getValue().cart()==null||entry.getValue().cart().isRemoved());
        if(!level.isClientSide()&&level.getGameTime()%10==0)for(var actual:loaded(level))if(actual.isPresent()&&actual.hasPendingRefunds())com.simibubi.create.content.contraptions.mounted.CartReceiptPublication.returnRefunds(actual,null);
        CouplingPhysics.tick(level);
    }
    public static void clear(Level level){LOADED.remove(level);}
    public static void register(){
        MinecartController.register();
        MinecartControllerUpdatePacket.register();
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof AbstractMinecart cart)loaded(cart);});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof AbstractMinecart cart)unloaded(cart);});
        ServerTickEvents.END_LEVEL_TICK.register(CapabilityMinecartController::tick);
        ServerLevelEvents.UNLOAD.register((server,world)->clear(world));
        EntityTrackingEvents.START_TRACKING.register((entity,player)->{if(entity instanceof AbstractMinecart cart)MinecartControllerUpdatePacket.send(MinecartController.get(cart),player);});
    }
    private CapabilityMinecartController(){}
}
