package com.bluelog;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BlueLogConfig.GROUP)
public interface BlueLogConfig extends Config {
	String GROUP = "bluelog";
	String IGNORED_ITEMS_KEY = "ignoredItems";

	@ConfigSection(name = "Items", description = "Items you do not care about", position = 0)
	String itemsSection = "itemsSection";

	@ConfigItem(keyName = IGNORED_ITEMS_KEY, name = "Ignored items", description = "List of ignored items, separated by commas", position = 1, section = itemsSection)
	default String ignoredItems() {
		return "";
	}

	@ConfigItem(keyName = "ignoreAllPets", name = "Ignore all pets", description = "Ignores pets from all sections", position = 2, section = itemsSection)
	default boolean ignoreAllPets() {
		return true;
	}

	@ConfigItem(keyName = "ignoreAllJars", name = "Ignore all jars", description = "Ignores jars from all sections", position = 3, section = itemsSection)
	default boolean ignoreAllJars() {
		return true;
	}

	@ConfigItem(keyName = "markerCorner", name = "Marker corner", description = "Corner of the item the ignored marker is drawn in", position = 4, section = itemsSection)
	default MarkerCorner markerCorner() {
		return MarkerCorner.TOP_RIGHT;
	}
}
