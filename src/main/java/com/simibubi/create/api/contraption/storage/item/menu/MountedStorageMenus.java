package com.simibubi.create.api.contraption.storage.item.menu;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import java.util.*;
import java.util.function.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;

public final class MountedStorageMenus {
    public static final List<MenuType<?>> GENERIC_CHEST_MENUS = List.of(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);
    public static @Nullable MenuProvider createGeneric(Component name, List<MountedItemStorage> storages, Predicate<Player> stillValid, Consumer<Player> onClose) {
        var container = new StorageInteractionWrapper(storages, stillValid, onClose); int slots = container.getContainerSize(), rows = slots / 9;
        if (rows < 1 || rows > 6 || slots % 9 != 0) return null;
        return new SimpleMenuProvider((id, inventory, player) -> new ChestMenu(GENERIC_CHEST_MENUS.get(rows - 1), id, inventory, container, rows), name);
    }
    public static @Nullable MenuProvider createGeneric9x9(Component name, List<MountedItemStorage> storages, Predicate<Player> stillValid, Consumer<Player> onClose) {
        var container = new StorageInteractionWrapper(storages, stillValid, onClose);
        return container.getContainerSize() == 9 ? new SimpleMenuProvider((id, inventory, player) -> new DispenserMenu(id, inventory, container), name) : null;
    }
    private MountedStorageMenus() {}
}
