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
	String IGNORED_ITEMS_KEY = "ignoredItems";

	@ConfigSection(
		name = "Items",
		description = "The items you are happy to be missing",
		position = 0
	)
	String itemsSection = "itemsSection";

	@ConfigSection(
		name = "Colours",
		description = "How sections are highlighted",
		position = 10
	)
	String coloursSection = "coloursSection";

	@ConfigSection(
		name = "Debug",
		description = "Diagnosing which sections the plugin has data for",
		position = 20,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@ConfigItem(
		keyName = IGNORED_ITEMS_KEY,
		name = "Ignored items",
		description = "Comma separated item names (newlines also work). A section turns blue when every item you are"
			+ " still missing from it appears in this list. Names are matched exactly, ignoring case. You can also"
			+ " right click an item in the collection log to add or remove it here.",
		position = 1,
		section = itemsSection
	)
	default String ignoredItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "ignoreAllPets",
		name = "Ignore all pets",
		description = "Adds every pet you are still missing to the list above. The pets are read from your own"
			+ " All Pets entry, so open that page in the collection log once to let this take effect.",
		position = 2,
		section = itemsSection
	)
	default boolean ignoreAllPets()
	{
		return false;
	}

	@ConfigItem(
		keyName = "ignoreAllJars",
		name = "Ignore all jars",
		description = "Treats every boss jar as ignored. Matches any item named \"Jar of ...\", so jars"
			+ " added to the game in future are covered automatically.",
		position = 3,
		section = itemsSection
	)
	default boolean ignoreAllJars()
	{
		return false;
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
		position = 21,
		section = debugSection
	)
	default boolean highlightUnscanned()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unscannedColor",
		name = "Unscanned colour",
		description = "Colour used for sections that have never been opened on this account",
		position = 22,
		section = debugSection
	)
	default Color unscannedColor()
	{
		return new Color(0x9A, 0x9A, 0x9A);
	}
}
