package net.runelite.client.plugins.microbot.gemstonecrabfighter;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class GemstoneCrabFighterOverlay extends OverlayPanel {
    private final GemstoneCrabFighterPlugin plugin;

    @Inject
    public GemstoneCrabFighterOverlay(GemstoneCrabFighterPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(230, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        GemstoneCrabFighterScript script = plugin.getScript();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Gemstone Crab Fighter").build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State").right(script == null ? "Stopped" : script.stateName()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Kills").right(String.valueOf(script == null ? 0 : script.getKills())).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Arrows looted").right(String.valueOf(script == null ? 0 : script.getArrowsLooted())).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Items looted").right(String.valueOf(script == null ? 0 : script.getItemsLooted())).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Next loot scan").right(script == null ? "-" : script.nextLootScan()).build());
        return super.render(graphics);
    }
}
