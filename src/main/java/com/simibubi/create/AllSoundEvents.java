package com.simibubi.create;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class AllSoundEvents {
  public static final SoundEvent ITEM_HATCH=sound("item_hatch"),ITEM_HATCH_COMPOUNDED_1=sound("item_hatch_compounded_1");
  public static final SoundEvent CARDBOARD_SWORD=sound("cardboard_bonk");
  public static final SoundEvent COPPER_ARMOR_EQUIP=sound("copper_armor_equip");
    public static final net.minecraft.sounds.SoundEvent WORLDSHAPER_PLACE=sound("worldshaper_place");
  public static final SoundEvent SCHEMATICANNON_LAUNCH_BLOCK=sound("schematicannon_launch_block"),SCHEMATICANNON_FINISH=sound("schematicannon_finish");
  public static final SoundEvent POTATO_HIT=sound("potato_hit"),FWOOMP=sound("fwoomp");
  public static final SoundEvent TRAIN2 = sound("train2");
  public static final SoundEvent TRAIN = sound("train"), TRAIN3 = sound("train3");
  public static final SoundEvent WHISTLE_TRAIN = sound("whistle_train"),
      WHISTLE_TRAIN_LOW = sound("whistle_train_low");
  public static final SoundEvent WHISTLE_TRAIN_MANUAL = sound("whistle_train_manual"),
      WHISTLE_TRAIN_MANUAL_END = sound("whistle_train_manual_end"),
      WHISTLE_TRAIN_MANUAL_LOW = sound("whistle_train_manual_low"),
      WHISTLE_TRAIN_MANUAL_LOW_END = sound("whistle_train_manual_low_end");
  public static final SoundEvent DEPOT_SLIDE = sound("depot_slide"), DEPOT_PLOP = sound("depot_plop");
  public static final SoundEvent SCROLL_VALUE = sound("scroll_value");
  public static final SoundEvent CONTROLLER_PUT = sound("controller_put"),
      CONTROLLER_TAKE = sound("controller_take");
  public static final SoundEvent CONTROLLER_CLICK = sound("controller_click");
  public static final SoundEvent PECULIAR_BELL_USE = sound("peculiar_bell_use"),
      HAUNTED_BELL_USE = sound("haunted_bell_use"),
      HAUNTED_BELL_CONVERT = sound("haunted_bell_convert"),
      DESK_BELL_USE = sound("desk_bell");
  public static final SoundEvent CONTRAPTION_ASSEMBLE = sound("contraption_assemble"),
      CONTRAPTION_DISASSEMBLE = sound("contraption_disassemble");
  public static final SoundEvent SLIME_ADDED = sound("slime_added");
  public static final SoundEvent FROGPORT_OPEN = sound("frogport_open"),
      FROGPORT_CLOSE = sound("frogport_close"),
      FROGPORT_CATCH = sound("frogport_catch"),
      FROGPORT_DEPOSIT = sound("frogport_deposit");
  public static final SoundEvent STOCK_TICKER_REQUEST = sound("stock_ticker_request"),
      STOCK_TICKER_TRADE = sound("stock_ticker_trade");
  public static final SoundEvent CONFIRM = sound("confirm"),
      CONFIRM_2 = sound("confirm_2"),
      DENY = sound("deny");
  public static final net.minecraft.sounds.SoundEvent STOCK_LINK = sound("stock_link");
  public static final SoundEvent CRANKING = sound("cranking");
  public static final SoundEvent CRANKING_BUTTON = sound("cranking_compounded_1");
  public static final SoundEvent PRESS = sound("mechanical_press_activation");
  public static final SoundEvent MIXING = sound("mixing");
  public static final SoundEvent FUNNEL_FLAP = sound("funnel_flap");
  public static final SoundEvent PACKAGE_POP = sound("package_pop");
  public static final SoundEvent PACKAGER = sound("packager");
  public static final SoundEvent SPOUTING = sound("spout");
  public static final SoundEvent STEAM = sound("steam");
  public static final SoundEvent WHISTLE_HIGH = sound("whistle_high"),
      WHISTLE_MEDIUM = sound("whistle"),
      WHISTLE_LOW = sound("whistle_low"),
      WHISTLE_CHIFF = sound("chiff");
  public static final SoundEvent CRUSHING = sound("crushing_1");
  public static final SoundEvent CRUSHING_1 = CRUSHING, CRUSHING_2 = sound("crushing_2"),
      CRUSHING_3 = sound("crushing_3"), COGS = sound("cogs");
  public static final SoundEvent CRAFTER_CLICK = sound("crafter_click");
  public static final SoundEvent CRAFTER_CRAFT = sound("crafter_craft");
  public static final SoundEvent SAW_ACTIVATE_WOOD = sound("saw_activate_wood");
  public static final SoundEvent SAW_ACTIVATE_STONE = sound("saw_activate_stone");
  public static final SoundEvent PRESS_BREAK = sound("mechanical_press_activation_compounded_1");
  public static final SoundEvent PRESS_BELT = sound("mechanical_press_activation_belt");
  public static final SoundEvent PRESS_BELT_BREAK =
      sound("mechanical_press_activation_belt_compounded_1");
  public static final SoundEvent SANDING_SHORT = sound("sanding_short");
  public static final SoundEvent SANDING_LONG = sound("sanding_long");
  public static final SoundEvent WRENCH_ROTATE = sound("wrench_rotate");
  public static final SoundEvent WRENCH_REMOVE = sound("wrench_remove");
  public static final SoundEvent WRENCH_REMOVE_HIT = sound("wrench_remove_compounded_1");

  public static final SoundEvent CLIPBOARD_CHECKMARK = sound("clipboard_check"),
      CLIPBOARD_ERASE = sound("clipboard_erase");

  private static SoundEvent sound(String name) {
    var id = Create.asResource(name);
    return Registry.register(
        BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
  }

  public static void register() {}

  private AllSoundEvents() {}
}
