/*
 * Copyright (c) 2026, Lanielm
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
 * Marks the item slots of the open collection log section that the user has allowed to be missing,
 * so it is obvious which items are the reason a section counts as near complete.
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
			if (slot.isHidden() || slot.getItemId() <= 0 || !plugin.isAllowedItem(slot))
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
