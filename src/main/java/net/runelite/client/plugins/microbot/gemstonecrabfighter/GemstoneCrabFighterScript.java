package net.runelite.client.plugins.microbot.gemstonecrabfighter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.automation.food.FoodService;
import net.runelite.client.plugins.microbot.util.automation.inventory.InventoryService;
import net.runelite.client.plugins.microbot.util.automation.runtime.NameFilter;
import net.runelite.client.plugins.microbot.util.automation.runtime.PlayerSnapshot;
import net.runelite.client.plugins.microbot.util.automation.safety.SafetyService;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class GemstoneCrabFighterScript extends StateMachineScript<GemstoneCrabState> {
    static final int CRAB_ID = NpcID.GEMSTONE_CRAB;
    static final int CRAB_REMAINS_ID = NpcID.GEMSTONE_CRAB_REMAINS;
    static final int CRAWL_THROUGH_ID = ObjectID1.CAVE_ROCK02_ENTRANCE01_GEMSTONE;
    static final WorldPoint OUTSIDE_CAVE = new WorldPoint(1274, 3168, 0);

    private final GemstoneCrabFighterConfig config;
    private final SafetyService safety = new SafetyService();
    private final InventoryService inventory = new InventoryService();
    private final FoodService food = new FoodService(inventory);
    private final AtomicInteger kills = new AtomicInteger();
    private final AtomicLong arrowsLooted = new AtomicLong();
    private final AtomicLong itemsLooted = new AtomicLong();
    private final AtomicInteger attackedIndex = new AtomicInteger(-1);

    private volatile boolean playerDeathObserved;
    private volatile boolean crabDeathObserved;
    private boolean finalSweepCompleted;
    private volatile PlayerSnapshot player = PlayerSnapshot.unavailable();
    private NameFilter foods;
    private GemstoneCrabState requested = GemstoneCrabState.INITIALIZING;
    private String reason = "Starting";
    private long offlineSince;
    private long attackDeadline;
    private long combatDeadline;
    private long nextLootCheckAt;
    private long lootAttentionAt;
    private long lootCycleDeadline;
    private long crabWaitDeadline;
    private int interactionFailures;

    public GemstoneCrabFighterScript(GemstoneCrabFighterConfig config) {
        this.config = config;
    }

    public boolean start() {
        if (isRunning()) return true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::tick,
                0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick() {
        try {
            if (Thread.currentThread().isInterrupted() || getCurrentState() == GemstoneCrabState.STOPPED) return;
            if (playerDeathObserved) { stop("Player death observed"); return; }
            player = safety.snapshot();
            if (!player.isLoggedIn()) {
                if (offlineSince == 0) offlineSince = System.nanoTime();
                if (System.nanoTime() - offlineSince > TimeUnit.SECONDS.toNanos(60)) {
                    stop("Login unavailable for 60 seconds");
                }
                return;
            }
            offlineSince = 0;
            validate(config);
            if (!applySafety()) return;
            if (requested != GemstoneCrabState.HEALING) maybeBeginPeriodicLoot();
            step();
        } catch (Exception e) {
            log.error("[GemstoneCrabFighter] Scheduled loop failed", e);
            stop("Unexpected error; inspect logs before restarting");
        }
    }

    private boolean applySafety() {
        if (player.isDead() || player.getHp() <= 0) {
            stop("Player death detected");
            return false;
        }
        if (foods == null) return true;
        boolean hasFood = food.available(foods);
        if (hasFood && player.below(config.eatHp())) {
            next(GemstoneCrabState.HEALING, "Low health");
        } else if (!hasFood && player.below(config.emergencyHp())) {
            stop("Emergency health reached without configured food");
            return false;
        } else if (!hasFood && config.stopWithoutFood()) {
            stop("Configured food exhausted");
            return false;
        }
        return true;
    }

    @Override
    protected GemstoneCrabState initialState() {
        return GemstoneCrabState.INITIALIZING;
    }

    @Override
    protected List<Transition<GemstoneCrabState>> defineTransitions() {
        List<Transition<GemstoneCrabState>> transitions = new ArrayList<>();
        for (GemstoneCrabState from : GemstoneCrabState.values()) {
            for (GemstoneCrabState to : GemstoneCrabState.values()) {
                if (from == to) continue;
                transitions.add(Transition.from(from)
                        .when(() -> requested == to, "requested == " + to)
                        .because("Encounter decision: " + to).goTo(to));
            }
        }
        return transitions;
    }

    @Override
    protected void onTransition(GemstoneCrabState from, GemstoneCrabState to, String ignored) {
        log.info("[GemstoneCrabFighter] State: {} -> {} ({})", from, to, reason);
        Microbot.status = "Gemstone Crab Fighter: " + to + " - " + reason;
    }

    private void next(GemstoneCrabState state, String message) {
        requested = state;
        reason = message;
    }

    @Override
    protected void onState(GemstoneCrabState state) {
        switch (state) {
            case INITIALIZING:
                foods = new NameFilter(config.food(), "");
                scheduleInitialLootCheck();
                routeToEncounter();
                break;
            case WALKING_TO_CAVE:
                walkToCave();
                break;
            case ENTERING_CAVE:
                enterCave();
                break;
            case FINDING_CRAB:
                attackCrab();
                break;
            case ATTACKING:
                awaitCombat();
                break;
            case IN_COMBAT:
                awaitKill();
                break;
            case HEALING:
                heal();
                break;
            case LOOTING_ITEMS:
                lootItems();
                break;
            case SWITCHING_CRAB:
                switchCrab();
                break;
            case WAITING_FOR_CRAB:
                waitForCrab();
                break;
            case STOPPED:
                stop(reason);
                break;
        }
    }

    private void routeToEncounter() {
        if (crabDeathObserved || findRemains() != null) {
            handleCrabDefeated();
        } else if (player.isFighting()) {
            resetCombatDeadline();
            next(GemstoneCrabState.IN_COMBAT, "Combat already active");
        } else if (findCrab() != null) {
            next(GemstoneCrabState.FINDING_CRAB, "Gemstone crab available");
        } else if (findEntrance() != null) {
            next(GemstoneCrabState.ENTERING_CAVE, "Crawl-through available");
        } else {
            next(GemstoneCrabState.WALKING_TO_CAVE, "Travel to the northern crab entrance");
        }
    }

    private void walkToCave() {
        if (findCrab() != null || findEntrance() != null) {
            routeToEncounter();
            return;
        }
        Rs2Walker.walkTo(OUTSIDE_CAVE);
        routeToEncounter();
    }

    private void enterCave() {
        if (findCrab() != null) {
            next(GemstoneCrabState.FINDING_CRAB, "Crab found before crawling");
            return;
        }
        Rs2TileObjectModel entrance = findEntrance();
        if (entrance == null) {
            next(GemstoneCrabState.WALKING_TO_CAVE, "Crawl-through not visible");
            return;
        }
        if (!entrance.click("Crawl-through")) {
            if (++interactionFailures >= 3) stop("Could not use the crab cave crawl-through");
            return;
        }
        interactionFailures = 0;
        crabWaitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.crabWaitTimeout());
        next(GemstoneCrabState.WAITING_FOR_CRAB, "Entered a crab room");
    }

    private void attackCrab() {
        if (player.isFighting()) {
            resetCombatDeadline();
            next(GemstoneCrabState.IN_COMBAT, "Combat active");
            return;
        }
        Rs2NpcModel crab = findCrab();
        if (crab == null) {
            routeToEncounter();
            return;
        }
        attackedIndex.set(crab.getIndex());
        crabDeathObserved = false;
        finalSweepCompleted = false;
        boolean dispatched = crab.click("Attack");
        attackDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        next(GemstoneCrabState.ATTACKING,
                dispatched ? "Waiting for combat confirmation" : "Attack interaction failed");
    }

    private void awaitCombat() {
        if (crabDeathObserved || findRemains() != null) {
            handleCrabDefeated();
        } else if (player.isFighting()) {
            interactionFailures = 0;
            resetCombatDeadline();
            next(GemstoneCrabState.IN_COMBAT, "Combat confirmed");
        } else if (System.nanoTime() >= attackDeadline) {
            attackedIndex.set(-1);
            if (++interactionFailures >= 3) stop("Attack failed three times");
            else next(GemstoneCrabState.FINDING_CRAB, "Attack timed out; retrying");
        }
    }

    private void awaitKill() {
        if (crabDeathObserved || findRemains() != null) {
            handleCrabDefeated();
        } else if (System.nanoTime() >= combatDeadline) {
            stop("Crab combat exceeded " + config.combatTimeoutMinutes() + " minutes");
        } else if (!player.isFighting()) {
            next(GemstoneCrabState.FINDING_CRAB, "Combat ended without a confirmed kill");
        }
    }

    private void maybeBeginPeriodicLoot() {
        GemstoneCrabState state = getCurrentState();
        if (!config.periodicLooting() || state == null || state == GemstoneCrabState.INITIALIZING
                || state == GemstoneCrabState.HEALING || state == GemstoneCrabState.LOOTING_ITEMS
                || state == GemstoneCrabState.STOPPED || System.nanoTime() < nextLootCheckAt) return;
        if (!config.lootDuringCombat() && player.isFighting()) {
            nextLootCheckAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(randomDelayMillis(5_000, 15_000));
            return;
        }
        beginLootCycle("Scheduled loot scan");
    }

    private void handleCrabDefeated() {
        attackedIndex.set(-1);
        if (config.finalLootSweep() && !finalSweepCompleted) {
            finalSweepCompleted = true;
            beginLootCycle("Final loot sweep before changing rooms");
        } else {
            next(GemstoneCrabState.SWITCHING_CRAB, "Crab defeated; moving to the next room");
        }
    }

    private void beginLootCycle(String message) {
        long now = System.nanoTime();
        lootAttentionAt = now + TimeUnit.MILLISECONDS.toNanos(randomDelayMillis(
                config.minimumAttentionDelay(), config.maximumAttentionDelay()));
        lootCycleDeadline = now + TimeUnit.SECONDS.toNanos(config.lootCycleTimeout());
        next(GemstoneCrabState.LOOTING_ITEMS, message);
    }

    private void lootItems() {
        long now = System.nanoTime();
        if (now < lootAttentionAt) return;
        if (now >= lootCycleDeadline || (!config.lootDuringCombat() && player.isFighting())) {
            finishLootCycle("Scheduled loot window complete");
            return;
        }
        long before = itemsLooted.get();
        boolean cleared = runLootPass();
        long picked = itemsLooted.get() - before;
        finishLootCycle(picked > 0
                ? "Retrieved " + picked + " ground items"
                : cleared ? "Loot scan complete; no matching items remain"
                : "Loot scan could not clear matching items");
    }

    private void finishLootCycle(String message) {
        scheduleNextLootCheck();
        routeToEncounter();
        reason = message;
    }

    private void scheduleInitialLootCheck() {
        long maximum = Math.min(TimeUnit.SECONDS.toMillis(config.minimumLootInterval()), 20_000L);
        nextLootCheckAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(randomDelayMillis(5_000L, maximum));
    }

    private void scheduleNextLootCheck() {
        long minimum = TimeUnit.SECONDS.toMillis(config.minimumLootInterval());
        long maximum = TimeUnit.SECONDS.toMillis(config.maximumLootInterval());
        nextLootCheckAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(randomDelayMillis(minimum, maximum));
    }

    private void switchCrab() {
        Rs2TileObjectModel entrance = findEntrance();
        if (entrance == null) {
            if (++interactionFailures >= 3) stop("No crawl-through available after the kill");
            return;
        }
        if (!entrance.click("Crawl-through")) {
            if (++interactionFailures >= 3) stop("Could not move to the next crab");
            return;
        }
        interactionFailures = 0;
        crabDeathObserved = false;
        finalSweepCompleted = false;
        crabWaitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.crabWaitTimeout());
        next(GemstoneCrabState.WAITING_FOR_CRAB, "Moved to the next crab room");
    }

    private void waitForCrab() {
        if (findCrab() != null) {
            interactionFailures = 0;
            next(GemstoneCrabState.FINDING_CRAB, "Next gemstone crab available");
        } else if (System.nanoTime() >= crabWaitDeadline) {
            next(findEntrance() == null ? GemstoneCrabState.WALKING_TO_CAVE : GemstoneCrabState.ENTERING_CAVE,
                    "No crab appeared before the wait timeout");
        }
    }

    private void heal() {
        if (food.eat(foods, this::cancelled)) {
            routeToEncounter();
        } else if (!cancelled()) {
            stop("Unable to eat configured food");
        }
    }

    private Rs2NpcModel findCrab() {
        return Microbot.getRs2NpcCache().query().withId(CRAB_ID)
                .where(npc -> !npc.isDead() && npc.getHealthRatio() != 0)
                .nearestOnClientThread();
    }

    private Rs2NpcModel findRemains() {
        return Microbot.getRs2NpcCache().query().withId(CRAB_REMAINS_ID).nearestOnClientThread();
    }

    private Rs2TileObjectModel findEntrance() {
        return Microbot.getRs2TileObjectCache().query().withId(CRAWL_THROUGH_ID).nearestOnClientThread();
    }

    private void resetCombatDeadline() {
        combatDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(config.combatTimeoutMinutes());
    }

    private boolean runLootPass() {
        List<Rs2TileItemModel> tracked = Microbot.getRs2TileItemCache().query().toListOnClientThread();
        List<LootCandidate> observed = tracked.stream()
                .map(this::snapshotLootCandidate)
                .filter(candidate -> candidate != null)
                .collect(Collectors.toList());
        List<LootCandidate> eligible = observed.stream()
                .filter(this::isConfiguredLoot)
                .filter(candidate -> candidate.distance <= config.lootRadius())
                .sorted(Comparator.comparingInt(candidate -> candidate.distance))
                .collect(Collectors.toList());

        String details = observed.stream().limit(10).map(LootCandidate::describe)
                .collect(Collectors.joining("; "));
        log.info("[GemstoneCrabFighter] Loot scan: tracked={}, eligible={}, radius={}, minValue={}, items=[{}]",
                observed.size(), eligible.size(), config.lootRadius(), config.minimumLootValue(), details);

        boolean allRetrieved = true;
        for (LootCandidate candidate : eligible) {
            if (cancelled() || System.nanoTime() >= lootCycleDeadline) return false;
            if (!takeTileItem(candidate)) allRetrieved = false;
        }
        log.info("[GemstoneCrabFighter] Periodic loot pass completed; eligible={}, cleared={}",
                eligible.size(), allRetrieved);
        return allRetrieved;
    }

    private LootCandidate snapshotLootCandidate(Rs2TileItemModel item) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) return null;
            LocalPoint playerLocal = Microbot.getClient().getLocalPlayer().getLocalLocation();
            LocalPoint itemLocal = item.getLocalLocation();
            if (playerLocal == null || itemLocal == null
                    || playerLocal.getWorldView() != itemLocal.getWorldView()
                    || item.getWorldLocation().getPlane() != Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane()) return null;
            int dx = Math.abs(playerLocal.getSceneX() - itemLocal.getSceneX());
            int dy = Math.abs(playerLocal.getSceneY() - itemLocal.getSceneY());
            String name = item.getName();
            return new LootCandidate(item, name, item.getQuantity(), item.getTotalValue(),
                    item.getOwnership(), Math.max(dx, dy),
                    canLootOwnership(item.getOwnership(), item.isPrivate(), item.getVisibleTime(),
                            Microbot.getClient().getTickCount()));
        }).orElse(null);
    }

    private boolean isConfiguredLoot(LootCandidate candidate) {
        if (!candidate.accessible) return false;
        boolean runeArrow = isRuneArrowName(candidate.name);
        if (runeArrow && config.onlyOwnArrows() && candidate.ownership != TileItem.OWNERSHIP_SELF) {
            return false;
        }
        return (config.lootRuneArrows() && runeArrow)
                || (config.lootValuableItems() && config.minimumLootValue() > 0
                && candidate.value >= config.minimumLootValue());
    }

    private boolean takeTileItem(LootCandidate candidate) {
        if (Rs2Inventory.emptySlotCount() == 0
                && (!candidate.item.isStackable() || !Rs2Inventory.hasItem(candidate.item.getId()))) {
            log.info("[GemstoneCrabFighter] Cannot retrieve {}: inventory has no usable slot", candidate.name);
            return false;
        }
        int before = Rs2Inventory.count(candidate.item.getId());
        if (!candidate.item.pickup()) {
            log.info("[GemstoneCrabFighter] Pickup interaction was not dispatched for {}", candidate.name);
            return false;
        }
        Global.sleepUntil(() -> cancelled()
                || Rs2Inventory.count(candidate.item.getId()) > before
                || !isStillTracked(candidate.item), 4_000);
        int received = Math.max(0, Rs2Inventory.count(candidate.item.getId()) - before);
        boolean removed = !isStillTracked(candidate.item);
        if (received == 0 && !removed) {
            log.info("[GemstoneCrabFighter] Pickup was dispatched but {} remained on the ground", candidate.name);
            return false;
        }
        if (received == 0) {
            log.info("[GemstoneCrabFighter] {} disappeared without an inventory increase; pickup unconfirmed", candidate.name);
            return false;
        }
        int counted = received;
        itemsLooted.addAndGet(counted);
        if (isRuneArrowName(candidate.name)) arrowsLooted.addAndGet(counted);
        log.info("[GemstoneCrabFighter] Retrieved {} x {} ({} gp)",
                counted, candidate.name, candidate.value);
        return true;
    }

    private boolean isStillTracked(Rs2TileItemModel target) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileItemCache().getStream()
                        .anyMatch(item -> item.getTileItem() == target.getTileItem())).orElse(true);
    }

    static boolean canLootOwnership(int ownership, boolean privateItem, int visibleTick, int currentTick) {
        return ownership != TileItem.OWNERSHIP_OTHER || (!privateItem && visibleTick <= currentTick);
    }

    private static final class LootCandidate {
        private final Rs2TileItemModel item;
        private final String name;
        private final int quantity;
        private final int value;
        private final int ownership;
        private final int distance;
        private final boolean accessible;

        private LootCandidate(Rs2TileItemModel item, String name, int quantity, int value,
                              int ownership, int distance, boolean accessible) {
            this.item = item;
            this.name = name;
            this.quantity = quantity;
            this.value = value;
            this.ownership = ownership;
            this.distance = distance;
            this.accessible = accessible;
        }

        private String describe() {
            return name + " x" + quantity + " " + value + "gp owner=" + ownership
                    + " accessible=" + accessible + " dist=" + distance;
        }
    }

    static boolean isRuneArrowName(String name) {
        if (name == null) return false;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        int suffix = normalized.indexOf('(');
        if (suffix >= 0) normalized = normalized.substring(0, suffix).trim();
        return normalized.equals("rune arrow") || normalized.equals("rune arrows");
    }

    static long randomDelayMillis(long minimum, long maximum) {
        if (maximum <= minimum) return minimum;
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1L);
    }

    static void validate(GemstoneCrabFighterConfig config) {
        if (config.lootRadius() < 1 || config.lootRadius() > 20
                || config.minimumLootValue() < 0
                || config.minimumLootInterval() < 15
                || config.maximumLootInterval() < config.minimumLootInterval()
                || config.maximumLootInterval() > 300
                || config.minimumAttentionDelay() < 0
                || config.maximumAttentionDelay() < config.minimumAttentionDelay()
                || config.maximumAttentionDelay() > 5000
                || config.lootCycleTimeout() < 5 || config.lootCycleTimeout() > 30
                || config.combatTimeoutMinutes() < 5 || config.combatTimeoutMinutes() > 30
                || config.crabWaitTimeout() < 5 || config.crabWaitTimeout() > 60
                || config.emergencyHp() < 1 || config.emergencyHp() > config.eatHp()
                || config.eatHp() > 100) {
            throw new IllegalArgumentException("Invalid loot, wait or health configuration");
        }
    }

    public void observeNpcDeath(NPC npc) {
        if (npc.getId() != CRAB_ID) return;
        int index = attackedIndex.get();
        if (index >= 0 && npc.getIndex() == index) {
            crabDeathObserved = true;
            if (attackedIndex.compareAndSet(index, -1)) kills.incrementAndGet();
        }
    }

    public void observePlayerDeath() {
        playerDeathObserved = true;
    }

    private boolean cancelled() {
        return Thread.currentThread().isInterrupted() || playerDeathObserved
                || getCurrentState() == GemstoneCrabState.STOPPED;
    }

    public int getKills() { return kills.get(); }
    public long getArrowsLooted() { return arrowsLooted.get(); }
    public long getItemsLooted() { return itemsLooted.get(); }

    public String nextLootScan() {
        if (!config.periodicLooting()) return "Off";
        long remaining = Math.max(0L, nextLootCheckAt - System.nanoTime());
        return Math.max(0L, TimeUnit.NANOSECONDS.toSeconds(remaining) + (remaining == 0 ? 0 : 1)) + "s";
    }

    public String stateName() {
        return getSnapshot() == null ? "Starting" : getSnapshot().currentState().name();
    }

    @Override
    protected GemstoneCrabState onError(GemstoneCrabState state, Exception e) {
        log.error("[GemstoneCrabFighter] Failure in {}", state, e);
        stop("Action failed; inspect logs before restarting");
        return GemstoneCrabState.STOPPED;
    }

    private void stop(String message) {
        reason = message;
        requested = GemstoneCrabState.STOPPED;
        Microbot.status = "Gemstone Crab Fighter stopped: " + message;
        log.warn("[GemstoneCrabFighter] Stopped: {}", message);
        shutdown();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        scheduledExecutorService.shutdownNow();
    }
}
