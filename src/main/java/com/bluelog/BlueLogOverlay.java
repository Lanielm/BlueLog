package com.bluelog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Marks the item slots of the open collection log section holding ignored items, so it is obvious
 * which items are the reason a section counts as near complete.
 */
class BlueLogOverlay extends Overlay
{
	/** Diameter of the dot, including its outline. Item slots are 36x32. */
	private static final int MARKER_SIZE = 7;

	private final Client client;
	private final BlueLogPlugin plugin;
	private final BlueLogConfig config;

	@Inject
	private BlueLogOverlay(Client client, BlueLogPlugin plugin, BlueLogConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget itemsContents = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (itemsContents == null || itemsContents.isHidden())
		{
			return null;
		}

		Widget[] slots = itemsContents.getDynamicChildren();
		if (slots == null || slots.length == 0)
		{
			return null;
		}

		Color markerColour = config.nearCompleteColor();
		Shape originalClip = graphics.getClip();

		// Slots scroll within the container, so clip to it rather than painting over the frame.
		graphics.setClip(itemsContents.getBounds());
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		for (Widget slot : slots)
		{
			// Opacity 0 means the game drew the item solid, i.e. it has been obtained.
			if (slot.isHidden() || slot.getItemId() <= 0 || slot.getOpacity() == 0)
			{
				continue;
			}

			if (!plugin.isIgnoredItem(slot))
			{
				continue;
			}

			Rectangle bounds = slot.getBounds();
			int x = bounds.x;
			int y = bounds.y + bounds.height - MARKER_SIZE;

			// Dark disc first so the mark stays visible against a pale item sprite.
			graphics.setColor(Color.BLACK);
			graphics.fillOval(x, y, MARKER_SIZE, MARKER_SIZE);
			graphics.setColor(markerColour);
			graphics.fillOval(x + 1, y + 1, MARKER_SIZE - 2, MARKER_SIZE - 2);
		}

		graphics.setClip(originalClip);
		return null;
	}
}
