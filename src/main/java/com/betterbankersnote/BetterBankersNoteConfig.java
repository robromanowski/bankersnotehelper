package com.betterbankersnote;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.Color;

@ConfigGroup("BetterBankersNote")
public interface BetterBankersNoteConfig extends Config {

    @ConfigItem(
            keyName = "outlineColor",
            name = "Outline Color",
            description = "Choose the color for the overlay outline"
    )
    default Color outlineColor() {
        return Color.YELLOW; // Default color
    }
    @ConfigItem(
            keyName = "enableOutline",
            name = "Enable Outline",
            description = "Enable or disable the outline around the overlay icon"
    )
    default boolean enableOutline() {
        return true; // Default is enabled
    }

}