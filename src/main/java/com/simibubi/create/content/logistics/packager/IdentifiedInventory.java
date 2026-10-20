package com.simibubi.create.content.logistics.packager;

import com.simibubi.create.api.packager.InventoryIdentifier;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/** A Fabric storage with its optional shared world inventory identity. */
public record IdentifiedInventory(InventoryIdentifier identifier,Storage<ItemVariant> handler) {}
