package com.simibubi.create.content.trains.schedule;

import net.minecraft.client.gui.screens.MenuScreens;

public final class ScheduleClient {
    private ScheduleClient() {}
    public static void register() { MenuScreens.register(ScheduleMenu.TYPE, ScheduleScreen::new); }
}
