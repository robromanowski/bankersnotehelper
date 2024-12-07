package com.betterbankersnote;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.InventoryID;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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
        log.info("Config: {}", config != null ? "Loaded" : "Missing");
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
                log.info("Initializing overlay on login.");
                retryOverlayInitialization(5);
            } else {
                log.info("Game state is not LOGGED_IN on start-up.");
            }
        });
    }

    private void retryOverlayInitialization(int retries) {
        if (retries <= 0) {
            log.warn("Overlay initialization failed after retries.");
            overlay.setTargetItemId(-1);
            return;
        }

        clientThread.invokeLater(() -> {
            if (updateOverlayWithCurrentBankersNoteState()) {
                log.info("Overlay successfully initialized.");
            } else {
                log.info("Retrying overlay initialization... Remaining retries: {}", retries - 1);
                retryOverlayInitialization(retries - 1);
            }
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            log.info("Game state changed to LOGGED_IN. Triggering proactive overlay update.");
            retryOverlayInitialization(5);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            log.info("Inventory updated. Triggering overlay update.");
            handleDelayedOverlayUpdate();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        log.info("MenuOptionClicked: Option={}, Target={}, ItemID={}, Action={}",
                event.getMenuOption(), event.getMenuTarget(), event.getItemId(), event.getMenuAction());

        if (event.getMenuAction() == MenuAction.WIDGET_TARGET_ON_WIDGET && event.getItemId() == ItemID.BANKERS_NOTE) {
            log.info("Banker's Note used. Menu option: {}", event.getMenuOption());

            String menuTarget = event.getMenuTarget();
            int extractedItemId = extractItemIdFromTarget(menuTarget);

            if (extractedItemId > 0) {
                log.info("Immediate update: Setting overlay target to {}", extractedItemId);
                overlay.setTargetItemId(extractedItemId);
            } else {
                log.warn("Failed to extract item ID from menu target. Triggering delayed overlay update.");
                handleDelayedOverlayUpdate();
            }
        }
    }

    private void handleDelayedOverlayUpdate() {
        clientThread.invokeLater(() -> {
            log.info("Handling delayed overlay update.");
            if (!updateOverlayWithCurrentBankersNoteState()) {
                log.warn("Failed to update overlay state. Retrying after delay...");
                clientThread.invokeLater(this::updateOverlayWithCurrentBankersNoteState);
            }
        });
    }

    private boolean updateOverlayWithCurrentBankersNoteState() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item != null && item.getId() == ItemID.BANKERS_NOTE) {
                    String widgetTooltip = getTooltipFromBankersNoteWidget();
                    if (widgetTooltip != null) {
                        int itemIdFromTooltip = extractItemIdFromTarget(widgetTooltip);
                        if (itemIdFromTooltip > 0) {
                            log.info("Setting overlay from widget tooltip: {}", itemIdFromTooltip);
                            overlay.setTargetItemId(itemIdFromTooltip);
                            targetItemId = itemIdFromTooltip;
                            return true;
                        }
                    }
                }
            }
            log.warn("Banker's Note not found in inventory or no tooltip available.");
        } else {
            log.warn("Inventory is null.");
        }

        overlay.setTargetItemId(-1); // Reset if Banker's Note is missing
        return false;
    }

    private String getTooltipFromBankersNoteWidget() {
        if (client.getWidget(WidgetInfo.INVENTORY) != null) {
            for (Widget widget : client.getWidget(WidgetInfo.INVENTORY).getDynamicChildren()) {
                if (widget != null && widget.getItemId() == ItemID.BANKERS_NOTE) {
                    String tooltip = widget.getName();
                    log.info("Extracted widget tooltip: {}", tooltip);
                    return tooltip;
                }
            }
        }
        log.warn("No tooltip found for Banker's Note in inventory.");
        return null;
    }

    private int extractItemIdFromTarget(String targetText) {
        if (targetText == null || targetText.isEmpty()) {
            return -1;
        }

        // Strip out HTML color tags and clean the input
        String cleanedTarget = targetText.replaceAll("<[^>]*>", "").trim();
        log.info("Extracted target text: {}", cleanedTarget);

        // Prioritize exact matches by checking against item names
        return itemManager.search(cleanedTarget)
                .stream()
                .filter(item -> item.getName().equalsIgnoreCase(cleanedTarget)) // Match exact name
                .findFirst()
                .map(item -> item.getId())
                .orElse(-1); // Fallback if no match
    }

}
