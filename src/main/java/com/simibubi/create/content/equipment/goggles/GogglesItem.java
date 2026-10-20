package com.simibubi.create.content.equipment.goggles;

import com.simibubi.create.AllItems;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;

/** Minecraft 26.3 uses the Equippable component for use, slots and dispensers. */
public final class GogglesItem extends Item {
    private static final List<Predicate<Player>> IS_WEARING_PREDICATES=new CopyOnWriteArrayList<>();
    static {addIsWearingPredicate(player->player.getItemBySlot(EquipmentSlot.HEAD).is(AllItems.GOGGLES));}
    public GogglesItem(Properties properties){super(properties.stacksTo(1).component(DataComponents.EQUIPPABLE,Equippable.builder(EquipmentSlot.HEAD).setDamageOnHurt(false).build()));}
    public static boolean isWearingGoggles(Player player){for(var predicate:IS_WEARING_PREDICATES)if(predicate.test(player))return true;return false;}
    public static synchronized void addIsWearingPredicate(Predicate<Player> predicate){IS_WEARING_PREDICATES.add(java.util.Objects.requireNonNull(predicate));}
}
