package net.runelite.client.plugins.microbot.gemstonecrabfighter;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(name = PluginConstants.DEFAULT_PREFIX + "Gemstone Crab Fighter",
        description = "Fights gemstone crabs, retrieves arrows, and moves to the next crab",
        authors = {"leopo"},
        tags = {"combat", "crab", "gemstone", "ranged", "microbot"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL,
        version = GemstoneCrabFighterPlugin.version,
        minClientVersion = "2.6.22")
public class GemstoneCrabFighterPlugin extends Plugin {
    public static final String version = "1.3.1";
    @Inject private GemstoneCrabFighterConfig config;
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private GemstoneCrabFighterOverlay overlay;
    private volatile GemstoneCrabFighterScript script;

    @Provides
    GemstoneCrabFighterConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(GemstoneCrabFighterConfig.class);
    }

    @Override
    protected void startUp() {
        if (script != null && script.isRunning()) return;
        script = new GemstoneCrabFighterScript(config);
        overlayManager.add(overlay);
        script.start();
    }

    @Override
    protected void shutDown() {
        if (script != null) script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        GemstoneCrabFighterScript active = script;
        if (active == null) return;
        if (event.getActor() == client.getLocalPlayer()) active.observePlayerDeath();
        else if (event.getActor() instanceof NPC) active.observeNpcDeath((NPC) event.getActor());
    }

    GemstoneCrabFighterScript getScript() {
        return script;
    }
}
