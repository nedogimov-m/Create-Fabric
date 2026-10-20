package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.AllItems;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** The three real source base-cart types; construction always uses the actual native vanilla class and UUID. */
public enum NativeCartKind {
    RIDEABLE,CHEST,FURNACE;
    public Item item(){return switch(this){case RIDEABLE->AllItems.MINECART_CONTRAPTION;case CHEST->AllItems.CHEST_MINECART_CONTRAPTION;case FURNACE->AllItems.FURNACE_MINECART_CONTRAPTION;};}
    public AbstractMinecart createEmpty(ServerLevel level,Vec3 position){
        AbstractMinecart cart=switch(this){case RIDEABLE->new Minecart(EntityTypes.MINECART,level);case CHEST->new MinecartChest(EntityTypes.CHEST_MINECART,level);case FURNACE->new MinecartFurnace(EntityTypes.FURNACE_MINECART,level);};
        cart.setInitialPos(position.x,position.y,position.z);
        if(cart.getBehavior() instanceof NewMinecartBehavior behavior){var rail=cart.getCurrentBlockPosOrRailBelow();if(!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,rail))throw new IllegalStateException("Actual native spawn rail must already be loaded");behavior.adjustToRails(rail,com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,rail),true);}
        return cart;
    }
    public static NativeCartKind of(AbstractMinecart cart){if(cart instanceof MinecartFurnace)return FURNACE;if(cart instanceof MinecartChest)return CHEST;if(cart instanceof Minecart)return RIDEABLE;throw new IllegalArgumentException("Only actual source rideable/chest/furnace carts can own a packed cart value");}
    public static NativeCartKind read(String value){return valueOf(value.toUpperCase(Locale.ROOT));}
}
