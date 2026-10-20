package com.simibubi.create.content.equipment.armor;

/** Native per-entity transient movement state; no second PlayerNBT or persistent equipment cache. */
public interface ArmorLivingAccess {
    boolean create$isArmorJumping();
    boolean create$wasLavaGrounded();
    void create$setLavaGrounded(boolean value);
}
