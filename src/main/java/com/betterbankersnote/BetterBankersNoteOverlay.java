package com.betterbankersnote;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
public class BetterBankersNoteOverlay extends Overlay {

    private final Client client;
    private final ItemManager itemManager;
    private BetterBankersNoteConfig config; // Injected config
    private int targetItemId = -1; // Default: No target item

    @Inject
    public BetterBankersNoteOverlay(Client client, ItemManager itemManager, BetterBankersNoteConfig config) {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config; // Store configuration for outline color
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

    }

    public void setConfig(BetterBankersNoteConfig config) {
        this.config = config;
    }


    public void setTargetItemId(int itemId) {
        log.info("Overlay target item ID set to: {}", itemId);
        this.targetItemId = itemId;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (targetItemId <= 0) {
            log.debug("No target item set. Skipping overlay rendering.");
            return null; // Skip if no target item
        }

        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        if (inventoryWidget == null || inventoryWidget.isHidden()) {
            log.debug("Inventory widget is not visible. Skipping overlay rendering.");
            return null; // Skip if inventory is not visible
        }

        // Locate Banker's Note in the inventory
        for (Widget item : inventoryWidget.getDynamicChildren()) {
            if (item.getItemId() == ItemID.BANKERS_NOTE) { // Banker's Note ID
                Rectangle bounds = item.getBounds();

                // Fetch the target item image
                BufferedImage targetImage = itemManager.getImage(targetItemId);
                if (targetImage != null) {
                    int overlaySize = 20; // Desired icon size
                    Image scaledImage = targetImage.getScaledInstance(overlaySize, overlaySize, Image.SCALE_SMOOTH);

                    // Calculate position to place the icon in the bottom-right corner
                    int x = bounds.x + bounds.width - overlaySize - 2; // Slight padding (2px) from the edge
                    int y = bounds.y + bounds.height - overlaySize - 2; // Slight padding (2px) from the edge

                    // Draw the scaled icon
                    graphics.drawImage(scaledImage, x, y, null);

                    // Check if the outline is enabled
                    if (config.enableOutline()) {
                        // Draw an outline around the image using the config color
                        drawImageOutline(graphics, targetImage, x, y, overlaySize, overlaySize, config.outlineColor());
                    }
                } else {
                    log.warn("No image found for target item ID: {}", targetItemId);
                }
                break;
            }
        }

        return null;
    }



    /**
     * Draws an outline around the non-transparent parts of an image.
     */
    private void drawImageOutline(Graphics2D graphics, BufferedImage image, int x, int y, int targetWidth, int targetHeight, Color outlineColor) {
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        graphics.setColor(outlineColor); // Use config-defined outline color
        for (int i = 0; i < scaledImage.getWidth(); i++) {
            for (int j = 0; j < scaledImage.getHeight(); j++) {
                int pixel = scaledImage.getRGB(i, j);
                if ((pixel >> 24) != 0x00 && isEdgePixel(scaledImage, i, j)) { // Check for edge
                    graphics.fillRect(x + i, y + j, 1, 1); // Draw 1x1 pixel outline
                }
            }
        }
    }

    /**
     * Determines if a pixel is on the edge by checking neighboring pixels for transparency.
     */
    private boolean isEdgePixel(BufferedImage image, int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (int d = 0; d < dx.length; d++) {
            int nx = x + dx[d];
            int ny = y + dy[d];
            if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                return true; // Out-of-bounds is considered transparent
            }
            int neighborPixel = image.getRGB(nx, ny);
            if ((neighborPixel >> 24) == 0x00) {
                return true; // Transparent neighbor means edge
            }
        }
        return false;
    }
}