# Gemstone Crab Fighter

`Gemstone Crab Fighter` is a standalone community plugin for ranged training
against gemstone crabs. Version 1.3.1 fights crab NPC `14779`, periodically
retrieves rune arrows and valuable ground items, and uses crawl-through object
`57631` to continue with another crab.

## Running it

1. Equip the ranged gear and ammunition you want to use.
2. Start in a gemstone crab room or near the northern entrance at
   `1274,3168,0`.
3. Disable AIO Fighter, Gem Crab Killer, and Auto Fighter. Only one combat loop
   should control the client.
4. Enable `Gemstone Crab Fighter` in the Microbot plugin panel.

For a manual sideload, copy `GemstoneCrabFighterPlugin.jar` into
`C:\Users\leopo\.runelite\microbot-plugins` and restart RuneLite once. Further
core-client recompilation is not required when replacing this external JAR.

Periodic looting is enabled by default. The first scan happens after a short
randomized delay, then scans occur every 60 to 120 seconds. Rune arrows are
always eligible regardless of stack value, while other items require a total GE
stack value of at least 200 gp. These thresholds, the loot radius, combat
looting, and timing ranges are configurable. Each cycle uses a randomized,
non-blocking attention delay and takes one reachable pile at a time. A final
no-wait sweep before changing rooms remains enabled as a safety net.

The combat timeout defaults to 15 minutes because lower-level ranged setups can
take longer than five minutes to defeat a gemstone crab. Loot interaction is
allowed to resolve its own short path inside the instanced room rather than
being rejected by a pre-flight reachability check.

Version 1.3 reads Microbot's tile-item cache and filters by scene-local distance.
This keeps detection and pickup in the active world view inside gemstone-crab
rooms, where template-world distance comparisons can reject visible drops.
Every scheduled scan enters `LOOTING_ITEMS` and logs the detected item names,
values, ownership, and local distance.

Version 1.3.1 permits another player's public drops after their visibility timer
expires. Ownership remains `OTHER` even when a drop becomes public; permanently
private drops and drops whose visibility timer has not expired are excluded.
Pickup counters require an inventory increase, not merely a disappearing pile.

`Only own rune arrows` defaults to off because ownership eventually expires and
the client cannot distinguish your former public stack from another public
stack. Enable it if the account should never take public rune arrows. The
overlay reports the current state, confirmed kills, retrieved arrows, total
items, and time until the next scan.

Food is optional. When a configured food is available, the plugin eats below
the configured threshold. Without food it stops only at the emergency health
threshold unless `Stop without food` is enabled.

## Version 1 limitations

- No banking or inventory-setup restocking.
- No mining of the defeated crab shell.
- The automatic outside route targets the northern entrance only.
- Runtime behavior still requires an in-game validation pass at the encounter.

The implementation is under
`src/main/java/net/runelite/client/plugins/microbot/gemstonecrabfighter/`.
