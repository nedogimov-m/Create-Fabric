package com.simibubi.create.test;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.girder.GirderModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/** Proposed real baked-face/atlas probe; particle metadata is never substituted for face material. */
public final class NativePartialTextureProbe {
  public record Result(int models, int faces, Set<Identifier> sourceSprites) {
    public Result { sourceSprites = Set.copyOf(sourceSprites); }
  }

  private record Expected(String name, ExtraModelKey<BlockStateModel> key, int faces, List<String> sprites) {}

  private static final List<Expected> WARNING_PARTS = List.of(
      new Expected("bogey_drive", AllPartialModels.BOGEY_DRIVE, 181,
          List.of("block/bogey/frame", "block/bogey/piston", "block/bogey/wheel")),
      new Expected("bogey_drive_wheel", AllPartialModels.LARGE_BOGEY_WHEELS, 352,
          List.of("block/bogey/wheel")),
      new Expected("bogey_frame", AllPartialModels.BOGEY_FRAME, 258,
          List.of("block/bogey/frame", "block/bogey/frame2", "block/bogey/wheel")),
      new Expected("bogey_drive_wheel_pin", AllPartialModels.BOGEY_PIN, 20,
          List.of("block/bogey/piston")),
      new Expected("bogey_drive_piston", AllPartialModels.BOGEY_PISTON, 152,
          List.of("block/bogey/piston")),
      new Expected("bogey_drive_belt", AllPartialModels.BOGEY_DRIVE_BELT, 44,
          List.of("block/bogey/belt")),
      new Expected("bogey_wheel", AllPartialModels.SMALL_BOGEY_WHEELS, 276,
          List.of("block/axis", "block/axis_top", "block/bogey/wheel")),
      new Expected("rope_coil", AllPartialModels.ROPE_COIL, 6,
          List.of("block/rope_pulley_coil")),
      new Expected("girder_segment_bottom", GirderModels.SEGMENT_BOTTOM, 6,
          List.of("block/girder")),
      new Expected("diode_indicator", AllPartialModels.FLEXPEATER_INDICATOR, 5,
          List.of("block/diodes/pulse_repeater/idle")),
      new Expected("train_control_lever", AllPartialModels.TRAIN_CONTROLS_LEVER, 5,
          List.of("block/controls")));

  /** Call only on the actual client thread after its native models/atlas have finished baking. */
  public static Result verify(Minecraft client) {
    if (!client.isSameThread()) throw new AssertionError("Native texture probe requires the actual client thread");
    var all = new ArrayList<>(WARNING_PARTS);
    for (var color : DyeColor.values()) {
      String source = "block/toolbox/" + color.getSerializedName();
      all.add(new Expected("toolbox_lid_" + color, AllPartialModels.TOOLBOX_LIDS.get(color), 20, List.of(source)));
      all.add(new Expected("toolbox_body_" + color, AllPartialModels.TOOLBOX_BODIES.get(color), 6, List.of(source)));
    }
    all.add(new Expected("toolbox_drawer", AllPartialModels.TOOLBOX_DRAWER, 5, List.of("block/toolbox/brown")));
    var seen = new LinkedHashSet<Identifier>();
    int faces = 0;
    for (var expected : all) faces += verify(client, expected, seen);
    var atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
    for (var scroll : List.of("block/bogey/belt_scroll", "block/rope_pulley_coil_scroll")) {
      var id = Create.asResource(scroll);
      var sprite = atlas.getSprite(id);
      if (sprite == atlas.missingSprite() || !sprite.contents().name().equals(id))
        throw new AssertionError("Actual native scrolling source sprite was not stitched: " + id);
      seen.add(id);
    }
    return new Result(all.size(), faces, seen);
  }

  private static int verify(Minecraft client, Expected expected, Set<Identifier> seen) {
    var atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
    var sprites = new ArrayList<TextureAtlasSprite>();
    for (String path : expected.sprites) {
      var id = Create.asResource(path);
      var sprite = atlas.getSprite(id);
      if (sprite == atlas.missingSprite() || !sprite.contents().name().equals(id))
        throw new AssertionError("Actual used source sprite was not stitched for " + expected.name + ": " + id);
      sprites.add(sprite); seen.add(id);
    }
    var mesh = new PartialModelRenderState();
    AllPartialModels.extract(expected.key, mesh);
    if (mesh.mesh == null || mesh.mesh.size() != expected.faces)
      throw new AssertionError("Actual complete source face count differs for " + expected.name
          + ": expected " + expected.faces + ", got " + (mesh.mesh == null ? "null" : mesh.mesh.size()));
    int[] count = {0};
    var used = new LinkedHashSet<Identifier>();
    mesh.mesh.forEach(quad -> {
      count[0]++;
      TextureAtlasSprite found = null;
      for (var sprite : sprites) {
        boolean inside = true;
        for (int vertex = 0; vertex < 4; vertex++) {
          float u = quad.u(vertex), v = quad.v(vertex);
          if (!Float.isFinite(u) || !Float.isFinite(v)
              || u < sprite.getU0() - 1e-6f || u > sprite.getU1() + 1e-6f
              || v < sprite.getV0() - 1e-6f || v > sprite.getV1() + 1e-6f) inside = false;
          if (!Float.isFinite(quad.x(vertex)) || !Float.isFinite(quad.y(vertex)) || !Float.isFinite(quad.z(vertex)))
            throw new AssertionError("Actual used model has a nonfinite source position: " + expected.name);
        }
        if (inside) { found = sprite; break; }
      }
      if (found == null)
        throw new AssertionError("Actual baked face UVs use no genuine source sprite for " + expected.name
            + " at face " + (count[0] - 1));
      used.add(found.contents().name());
    });
    var wanted = new LinkedHashSet<Identifier>();
    expected.sprites.forEach(path -> wanted.add(Create.asResource(path)));
    if (count[0] != expected.faces || !used.equals(wanted))
      throw new AssertionError("Actual model omitted a source material/face for " + expected.name + ": " + used);
    return count[0];
  }

  private NativePartialTextureProbe() {}
}
