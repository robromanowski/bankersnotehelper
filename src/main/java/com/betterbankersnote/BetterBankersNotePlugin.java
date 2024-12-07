package com.betterbankersnote;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;

@Slf4j
@PluginDescriptor(
        name = "Better Banker's Note",
        description = "Displays an icon for the item targeted by the Banker's Note.",
        tags = {"inventory", "note", "overlay"}
)
public class BetterBankersNotePlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BetterBankersNoteConfig config;

    @Inject
    private BetterBankersNoteOverlay overlay;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientThread clientThread;

    @Provides
    BetterBankersNoteConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BetterBankersNoteConfig.class);
    }

    private int targetItemId = -1;

    @Override
    protected void startUp() {
        log.info("Starting Better Banker's Note Plugin");
        overlayManager.add(overlay);
        initializeOverlayOnLogin();
    }

    @Override
    protected void shutDown() {
        log.info("Better Banker's Note plugin stopped.");
        overlayManager.remove(overlay);
        overlay.setTargetItemId(-1); // Clear the overlay
    }

    private void initializeOverlayOnLogin() {
        clientThread.invokeLater(() -> {
            if (client.getGameState() == GameState.LOGGED_IN) {
                retryOverlayInitialization(5);
            }
        });
    }

    private void retryOverlayInitialization(int retries) {
        if (retries <= 0) {
            overlay.setTargetItemId(-1);
            return;
        }

        clientThread.invokeLater(() -> {
            if (updateOverlayWithCurrentBankersNoteState()) {
                log.info("Overlay successfully initialized.");
            } else {
                retryOverlayInitialization(retries - 1);
            }
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            retryOverlayInitialization(5);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            handleDelayedOverlayUpdate();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() == MenuAction.WIDGET_TARGET_ON_WIDGET && event.getItemId() == ItemID.BANKERS_NOTE) {
            String menuTarget = event.getMenuTarget();
            int extractedItemId = extractItemIdFromTarget(menuTarget);

            if (extractedItemId > 0) {
                overlay.setTargetItemId(extractedItemId);
            } else {
                handleDelayedOverlayUpdate();
            }
        }
    }

    private void handleDelayedOverlayUpdate() {
        clientThread.invokeLater(() -> {
            if (!updateOverlayWithCurrentBankersNoteState()) {
                clientThread.invokeLater(this::updateOverlayWithCurrentBankersNoteState);
            }
        });
    }

    private boolean updateOverlayWithCurrentBankersNoteState() {
        Widget inventoryWidget = client.getWidget(InterfaceID.INVENTORY,0);
        if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null) {
            log.warn("Inventory widget not found.");
            overlay.setTargetItemId(-1);
            return false;
        }

        for (Widget item : inventoryWidget.getDynamicChildren()) {
            if (item.getItemId() == ItemID.BANKERS_NOTE) {
                String widgetTooltip = getTooltipFromWidget(item);
                if (widgetTooltip != null) {
                    int itemIdFromTooltip = extractItemIdFromTarget(widgetTooltip);
                    if (itemIdFromTooltip > 0) {
                        overlay.setTargetItemId(itemIdFromTooltip);
                        targetItemId = itemIdFromTooltip;
                        return true;
                    }
                }
            }
        }

        overlay.setTargetItemId(-1); // Reset if no Banker's Note is found
        return false;
    }

    private String getTooltipFromWidget(Widget widget) {
        if (widget != null) {
            return widget.getName();
        }
        return null;
    }

    private int extractItemIdFromTarget(String targetText) {
        if (targetText == null || targetText.isEmpty()) {
            return -1;
        }

        String cleanedTarget = targetText.replaceAll("<[^>]*>", "").trim();
        return itemManager.search(cleanedTarget)
                .stream()
                .filter(item -> item.getName().equalsIgnoreCase(cleanedTarget))
                .findFirst()
                .map(item -> item.getId())
                .orElse(-1);
    }
}
