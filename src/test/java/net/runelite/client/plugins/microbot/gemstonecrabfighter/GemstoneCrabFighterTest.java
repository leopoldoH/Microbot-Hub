package net.runelite.client.plugins.microbot.gemstonecrabfighter;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GemstoneCrabFighterTest {
    @Test
    public void otherPlayersPublicDropsBecomeEligible() {
        assertTrue(GemstoneCrabFighterScript.canLootOwnership(2, false, 100, 100));
        assertTrue(GemstoneCrabFighterScript.canLootOwnership(2, false, 100, 101));
        assertFalse(GemstoneCrabFighterScript.canLootOwnership(2, false, 100, 99));
        assertFalse(GemstoneCrabFighterScript.canLootOwnership(2, true, 100, 101));
        assertTrue(GemstoneCrabFighterScript.canLootOwnership(1, true, 100, 99));
    }
    @Test
    public void recognizesRuneArrowItemNames() {
        assertTrue(GemstoneCrabFighterScript.isRuneArrowName("Rune arrow"));
        assertTrue(GemstoneCrabFighterScript.isRuneArrowName("Rune arrow(p++)"));
        assertFalse(GemstoneCrabFighterScript.isRuneArrowName("Bronze arrow"));
        assertFalse(GemstoneCrabFighterScript.isRuneArrowName("Arrow shaft"));
        assertFalse(GemstoneCrabFighterScript.isRuneArrowName("Dragon bolts"));
    }

    @Test
    public void randomizedDelayStaysInsideConfiguredBounds() {
        for (int i = 0; i < 100; i++) {
            long delay = GemstoneCrabFighterScript.randomDelayMillis(60000, 120000);
            assertTrue(delay >= 60000);
            assertTrue(delay <= 120000);
        }
    }

    @Test
    public void pluginSupportsRepositoryDiscovery() throws Exception {
        assertNotNull(GemstoneCrabFighterPlugin.class.getDeclaredConstructor().newInstance());
    }
}
