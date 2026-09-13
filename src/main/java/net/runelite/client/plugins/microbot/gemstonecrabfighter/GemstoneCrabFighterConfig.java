package net.runelite.client.plugins.microbot.gemstonecrabfighter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("gemstonecrabfighter")
public interface GemstoneCrabFighterConfig extends Config {
    @ConfigItem(keyName = "periodicLooting", name = "Periodic looting",
            description = "Periodically look for loot instead of only reacting to a crab death", position = 0)
    default boolean periodicLooting() { return true; }

    @ConfigItem(keyName = "lootRuneArrows", name = "Loot rune arrows",
            description = "Always retrieve accessible rune-arrow stacks regardless of stack value", position = 1)
    default boolean lootRuneArrows() { return true; }

    @ConfigItem(keyName = "lootValuableItems", name = "Loot valuable items",
            description = "Retrieve accessible items whose total GE stack value meets the threshold", position = 2)
    default boolean lootValuableItems() { return true; }

    @ConfigItem(keyName = "onlyOwnArrows", name = "Only own rune arrows",
            description = "Ignore public rune-arrow stacks; disabling this avoids missing arrows after ownership expires", position = 3)
    default boolean onlyOwnArrows() { return false; }

    @Range(min = 0, max = 10000000)
    @ConfigItem(keyName = "minimumLootValue", name = "Minimum loot value",
            description = "Minimum total GE value of an item stack; rune arrows ignore this threshold", position = 4)
    default int minimumLootValue() { return 200; }

    @Range(min = 1, max = 20)
    @ConfigItem(keyName = "lootRadius", name = "Loot radius",
            description = "Maximum distance from the player for periodic pickup", position = 5)
    default int lootRadius() { return 10; }

    @Range(min = 15, max = 300)
    @ConfigItem(keyName = "minimumLootInterval", name = "Minimum loot interval",
            description = "Shortest randomized delay between loot scans, in seconds", position = 6)
    default int minimumLootInterval() { return 60; }

    @Range(min = 15, max = 300)
    @ConfigItem(keyName = "maximumLootInterval", name = "Maximum loot interval",
            description = "Longest randomized delay between loot scans, in seconds", position = 7)
    default int maximumLootInterval() { return 120; }

    @Range(min = 0, max = 5000)
    @ConfigItem(keyName = "minimumAttentionDelay", name = "Minimum attention delay",
            description = "Minimum non-blocking pause before a scheduled loot action, in milliseconds", position = 8)
    default int minimumAttentionDelay() { return 400; }

    @Range(min = 0, max = 5000)
    @ConfigItem(keyName = "maximumAttentionDelay", name = "Maximum attention delay",
            description = "Maximum non-blocking pause before a scheduled loot action, in milliseconds", position = 9)
    default int maximumAttentionDelay() { return 1800; }

    @Range(min = 5, max = 30)
    @ConfigItem(keyName = "lootCycleTimeout", name = "Loot cycle timeout",
            description = "Maximum seconds spent collecting matching items during one scan", position = 10)
    default int lootCycleTimeout() { return 15; }

    @ConfigItem(keyName = "lootDuringCombat", name = "Loot during combat",
            description = "Allow scheduled loot scans while the crab fight is still active", position = 11)
    default boolean lootDuringCombat() { return true; }

    @ConfigItem(keyName = "finalLootSweep", name = "Final loot sweep",
            description = "Do one no-wait safety sweep before leaving a defeated crab room", position = 12)
    default boolean finalLootSweep() { return true; }

    @Range(min = 5, max = 30)
    @ConfigItem(keyName = "combatTimeoutMinutes", name = "Combat timeout",
            description = "Maximum minutes allowed for one crab fight", position = 13)
    default int combatTimeoutMinutes() { return 15; }

    @Range(min = 5, max = 60)
    @ConfigItem(keyName = "crabWaitTimeout", name = "Crab wait timeout",
            description = "Seconds to wait for a crab before trying another crawl-through", position = 14)
    default int crabWaitTimeout() { return 15; }

    @ConfigItem(keyName = "food", name = "Food",
            description = "Comma-separated exact food names; blank disables eating", position = 20)
    default String food() { return "Lobster"; }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "eatHp", name = "Eat HP %",
            description = "Eat below this health percentage when configured food is available", position = 21)
    default int eatHp() { return 40; }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "emergencyHp", name = "Emergency HP %",
            description = "Stop below this health percentage when no configured food is available", position = 22)
    default int emergencyHp() { return 20; }

    @ConfigItem(keyName = "stopWithoutFood", name = "Stop without food",
            description = "Stop as soon as no configured food remains", position = 23)
    default boolean stopWithoutFood() { return false; }

    @ConfigItem(keyName = "debug", name = "Debug logging",
            description = "Log confirmed attacks, loot and recovery attempts", position = 30)
    default boolean debug() { return false; }
}
