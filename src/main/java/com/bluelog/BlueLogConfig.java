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
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BlueLogConfig.GROUP)
public interface BlueLogConfig extends Config
{
	String GROUP = "bluelog";

	@ConfigSection(
		name = "Items",
		description = "The items that are allowed to be missing",
		position = 0
	)
	String itemsSection = "itemsSection";

	@ConfigSection(
		name = "Colours",
		description = "How sections are highlighted",
		position = 10
	)
	String coloursSection = "coloursSection";

	@ConfigItem(
		keyName = "allowedItems",
		name = "Allowed missing items",
		description = "One item name per line (commas also work). A section turns blue when every item you are still"
			+ " missing from it appears in this list. Names are matched exactly, ignoring case.",
		position = 1,
		section = itemsSection
	)
	default String allowedItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "nearCompleteColor",
		name = "Near-complete colour",
		description = "Colour used for a section whose only missing items are in your list",
		position = 11,
		section = coloursSection
	)
	default Color nearCompleteColor()
	{
		return new Color(0x33, 0x99, 0xFF);
	}

	@ConfigItem(
		keyName = "highlightUnscanned",
		name = "Mark unscanned sections",
		description = "Colour sections that have never been opened on this account. The game only sends item data for"
			+ " the page you are looking at, so a section cannot be judged until you have opened it at least once.",
		position = 12,
		section = coloursSection
	)
	default boolean highlightUnscanned()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unscannedColor",
		name = "Unscanned colour",
		description = "Colour used for sections that have never been opened on this account",
		position = 13,
		section = coloursSection
	)
	default Color unscannedColor()
	{
		return new Color(0x9A, 0x9A, 0x9A);
	}
}
