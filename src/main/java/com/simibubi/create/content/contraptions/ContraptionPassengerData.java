package com.simibubi.create.content.contraptions;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Source passenger history belongs to the actual passenger, including its native saved data. */
public final class ContraptionPassengerData {
  private static final AttachmentType<Vec3> MOUNT_LOCATION = AttachmentRegistry.<Vec3>builder()
      .persistent(Vec3.CODEC).buildAndRegister(Create.asResource("contraption_mount_location"));
  private static final AttachmentType<Vec3> IMPORTED_DISMOUNT_LOCATION = AttachmentRegistry.<Vec3>builder()
      .persistent(Vec3.CODEC).buildAndRegister(Create.asResource("historical_contraption_dismount_location"));
  private static final AttachmentType<Boolean> GROUNDED = AttachmentRegistry.<Boolean>builder()
      .buildAndRegister(Create.asResource("contraption_grounded"));

  public static void register() {}
  public static void mounted(Entity passenger) {
    ((AttachmentTarget) passenger).setAttached(MOUNT_LOCATION, passenger.position());
  }
  public static Vec3 takeMountLocation(Entity passenger) {
    return ((AttachmentTarget) passenger).removeAttached(MOUNT_LOCATION);
  }
  public static Vec3 takeImportedDismountLocation(Entity passenger) {
    return ((AttachmentTarget) passenger).removeAttached(IMPORTED_DISMOUNT_LOCATION);
  }
  public static void clearImportedDismountLocation(Entity passenger) {
    ((AttachmentTarget) passenger).removeAttached(IMPORTED_DISMOUNT_LOCATION);
  }
  public static void markGrounded(Entity passenger) {
    ((AttachmentTarget) passenger).setAttached(GROUNDED, true);
  }
  /** Current native flag without consuming its one actual tick transition. */
  public static boolean isGrounded(Entity passenger) { return ((AttachmentTarget) passenger).hasAttached(GROUNDED); }
  public static boolean takeGrounded(Entity passenger) {
    var target = (AttachmentTarget) passenger;
    boolean present = target.hasAttached(GROUNDED);
    target.removeAttached(GROUNDED);
    return present;
  }
  private ContraptionPassengerData() {}
}
