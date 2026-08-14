package com.bluelog;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BlueLogConfig.GROUP)
public interface BlueLogConfig extends Config {
	String GROUP = "bluelog";
	String IGNORED_ITEMS_KEY = "ignoredItems";

	Color DEFAULT_HIGHLIGHT_COLOUR = new Color(0x33, 0x99, 0xFF);
	Color DEFAULT_UNSCANNED_COLOUR = new Color(0x9A, 0x9A, 0x9A);

	@ConfigSection(name = "Items", description = "Items you do not care about", position = 0)
	String itemsSection = "itemsSection";

	@ConfigSection(name = "Colours", description = "Highlighting colour configuration", position = 10, closedByDefault = true)
	String coloursSection = "coloursSection";

	@ConfigSection(name = "Debug", description = "Debugging information", position = 20, closedByDefault = true)
	String debugSection = "debugSection";

	@ConfigItem(keyName = IGNORED_ITEMS_KEY, name = "Ignored items", description = "List of ignored items, separated by commas", position = 1, section = itemsSection)
	default String ignoredItems() {
		return "";
	}

	@ConfigItem(keyName = "ignoreAllPets", name = "Ignore all pets", description = "Ignores pets from all sections", position = 2, section = itemsSection)
	default boolean ignoreAllPets() {
		return false;
	}

	@ConfigItem(keyName = "ignoreAllJars", name = "Ignore all jars", description = "Ignores jars from all sections", position = 3, section = itemsSection)
	default boolean ignoreAllJars() {
		return false;
	}

	@ConfigItem(keyName = "highlightColour", name = "Highlight colour", description = "Colour used for a highlighted section", position = 11, section = coloursSection)
	default Color highlightColour() {
		return DEFAULT_HIGHLIGHT_COLOUR;
	}

	@ConfigItem(keyName = "highlightUnscanned", name = "Mark unscanned sections", description = "Colour sections that have not been scanned yet", position = 21, section = debugSection)
	default boolean highlightUnscanned() {
		return false;
	}

	@ConfigItem(keyName = "unscannedColour", name = "Unscanned colour", description = "Colour used for sections that have not been scanned yet", position = 22, section = debugSection)
	default Color unscannedColour() {
		return DEFAULT_UNSCANNED_COLOUR;
	}
}
