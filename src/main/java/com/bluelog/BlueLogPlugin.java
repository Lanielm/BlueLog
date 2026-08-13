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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Blue Log",
	description = "Colours a collection log section blue when the only items you are still missing are ones you listed",
	tags = {"collection", "log", "clog", "blue", "highlight"}
)
public class BlueLogPlugin extends Plugin
{
	/**
	 * Text colour the game itself uses for a fully completed section. We never repaint these,
	 * so a finished section stays green.
	 */
	private static final int COMPLETED_PAGE_COLOR = 0x0dc10d;

	/** Text colour the game itself uses for a section that is not yet complete. */
	private static final int INCOMPLETE_PAGE_COLOR = 0xff981f;

	/** Interface group of the collection log, derived from a component so it cannot drift. */
	private static final int COLLECTION_LOG_GROUP_ID = InterfaceID.Collection.LIST >>> 16;

	/**
	 * Section names are not held in one list. Each of the five tabs has its own text layer whose
	 * dynamic children are that tab's section names, so all five are walked.
	 */
	private static final int[] SECTION_NAME_LISTS = {
		InterfaceID.Collection.BOSS_TEXT,
		InterfaceID.Collection.RAID_TEXT,
		InterfaceID.Collection.CLUE_TEXT,
		InterfaceID.Collection.MINIGAME_TEXT,
		InterfaceID.Collection.OTHER_TEXT,
	};

	private static final String CACHE_KEY = "pageCache";

	/**
	 * Entry the "ignore all pets" preset is sourced from. Its cached missing items are exactly the
	 * pets the player has yet to obtain, which beats hardcoding a list that would go stale.
	 */
	private static final String ALL_PETS_ENTRY = "All Pets";

	private static final Type CACHE_TYPE = new TypeToken<HashMap<String, PageSnapshot>>()
	{
	}.getType();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BlueLogConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BlueLogOverlay overlay;

	/** Everything we know about pages the player has opened, keyed by lowercase page name. */
	private final Map<String, PageSnapshot> pages = new HashMap<>();

	/**
	 * Item names the user is happy to still be missing, lowercase. Combines the config text box
	 * with whichever preset lists are switched on.
	 */
	private Set<String> allowedItems = Collections.emptySet();

	@Override
	protected void startUp()
	{
		// Cache first: the presets are derived from it.
		loadCache();
		refreshAllowedItems();
		overlayManager.add(overlay);
		clientThread.invokeLater(this::recolourList);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		pages.clear();
		allowedItems = Collections.emptySet();
	}

	@Provides
	BlueLogConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlueLogConfig.class);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
		{
			return;
		}

		// The list widgets already carry their final text and colour by the time this script
		// returns, so recolour immediately to avoid a frame of orange.
		recolourList();

		// Item slots for the newly selected page are not populated until the client has
		// finished the tick, so read them once things have settled and repaint again.
		clientThread.invokeLater(() ->
		{
			if (snapshotOpenPage())
			{
				// Opening the All Pets page is what fills the pets preset, so rebuild before painting.
				refreshAllowedItems();
				recolourList();
			}
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == COLLECTION_LOG_GROUP_ID)
		{
			clientThread.invokeLater(this::recolourList);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BlueLogConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		refreshAllowedItems();
		clientThread.invokeLater(this::recolourList);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Collection log progress is per character, so swap in that character's cache.
		loadCache();
		refreshAllowedItems();
		clientThread.invokeLater(this::recolourList);
	}

	/**
	 * Reads the section currently displayed on the right hand side of the log and records
	 * which of its items are still missing.
	 *
	 * @return true when the stored data for that section changed
	 */
	private boolean snapshotOpenPage()
	{
		String pageName = openPageName();
		if (pageName == null || pageName.isEmpty())
		{
			return false;
		}

		// Slots are created in ITEMS_CONTENTS. ITEMS is only the outer container that gets resized.
		Widget itemsList = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (itemsList == null)
		{
			return false;
		}

		Widget[] slots = itemsList.getDynamicChildren();
		if (slots == null || slots.length == 0)
		{
			// Page has not rendered its items yet; keep whatever we already knew.
			return false;
		}

		List<String> missing = new ArrayList<>();
		int total = 0;

		for (Widget slot : slots)
		{
			int itemId = slot.getItemId();
			if (itemId <= 0)
			{
				continue;
			}

			total++;

			// Obtained items are drawn fully opaque; missing ones are faded out by the game.
			if (slot.getOpacity() == 0)
			{
				continue;
			}

			String itemName = itemName(slot, itemId);
			if (!itemName.isEmpty())
			{
				missing.add(itemName);
			}
		}

		if (total == 0)
		{
			return false;
		}

		PageSnapshot snapshot = new PageSnapshot(pageName, missing, total, System.currentTimeMillis());
		PageSnapshot previous = pages.get(key(pageName));
		if (snapshot.sameContentAs(previous))
		{
			return false;
		}

		pages.put(key(pageName), snapshot);
		saveCache();
		return true;
	}

	/**
	 * Walks the section list down the left hand side and paints the sections that are one
	 * allowed item away from completion.
	 */
	private void recolourList()
	{
		int nearCompleteColor = config.nearCompleteColor().getRGB();
		int unscannedColor = config.unscannedColor().getRGB();
		boolean markUnscanned = config.highlightUnscanned();

		for (int listId : SECTION_NAME_LISTS)
		{
			Widget list = client.getWidget(listId);
			if (list == null)
			{
				continue;
			}

			for (Widget entry : sectionEntries(list))
			{
				String name = Text.removeTags(Text.sanitize(entry.getText())).trim();
				if (name.isEmpty())
				{
					continue;
				}

				// A finished section is already green and should stay that way.
				if (entry.getTextColor() == COMPLETED_PAGE_COLOR)
				{
					continue;
				}

				// Always set a colour rather than only painting the interesting cases. Nothing else
				// repaints these widgets until the game redraws the list, so leaving an entry alone
				// would strand whatever colour was applied last time.
				PageSnapshot snapshot = pages.get(key(name));
				int colour = INCOMPLETE_PAGE_COLOR;

				if (snapshot == null)
				{
					if (markUnscanned)
					{
						colour = unscannedColor;
					}
				}
				else if (snapshot.isOnlyMissing(allowedItems))
				{
					colour = nearCompleteColor;
				}

				entry.setTextColor(colour);
			}
		}
	}

	/**
	 * The per-tab text layer holds one dynamic child per section. Fall back to the layer itself in
	 * case a tab is laid out as a single text widget.
	 */
	private static Widget[] sectionEntries(Widget list)
	{
		Widget[] children = list.getDynamicChildren();
		if (children != null && children.length > 0)
		{
			return children;
		}

		return new Widget[]{list};
	}

	/** The name of the section currently open, read from the panel header. */
	private String openPageName()
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		if (header == null)
		{
			header = client.getWidget(InterfaceID.Collection.HEADER);
		}

		if (header == null)
		{
			return null;
		}

		String text = firstNonEmptyText(header);
		return text == null ? null : Text.removeTags(text).trim();
	}

	/**
	 * The header is sometimes the text widget itself and sometimes a container whose first
	 * line is the section name, so check the widget and then its children.
	 */
	private static String firstNonEmptyText(Widget header)
	{
		String own = header.getText();
		if (own != null && !own.isEmpty())
		{
			return own;
		}

		for (Widget[] children : Arrays.asList(header.getDynamicChildren(), header.getStaticChildren()))
		{
			if (children == null)
			{
				continue;
			}

			for (Widget child : children)
			{
				String text = child.getText();
				if (text != null && !text.isEmpty())
				{
					return text;
				}
			}
		}

		return null;
	}

	private String itemName(Widget slot, int itemId)
	{
		String name = Text.removeTags(Text.sanitize(slot.getName())).trim();
		if (!name.isEmpty())
		{
			return name;
		}

		return itemManager.getItemComposition(itemId).getName().trim();
	}

	/**
	 * Whether a collection log item slot holds an item the user has allowed to be missing. Used by
	 * the overlay to mark those slots in the open section.
	 */
	boolean isAllowedItem(Widget slot)
	{
		if (allowedItems.isEmpty())
		{
			return false;
		}

		return allowedItems.contains(itemName(slot, slot.getItemId()).toLowerCase(Locale.ROOT));
	}

	/**
	 * Rebuilds the allowed set from the config text box plus any preset lists that are switched on.
	 * Cheap enough to redo whenever the config or the cache changes.
	 */
	private void refreshAllowedItems()
	{
		Set<String> allowed = new LinkedHashSet<>(parseItemList(config.allowedItems()));

		if (config.ignoreAllPets())
		{
			allowed.addAll(missingItemsOf(ALL_PETS_ENTRY));
		}

		allowedItems = allowed;
	}

	/**
	 * The items still missing from a cached entry, lowercase. Empty when that entry has never been
	 * opened, which is what makes a preset a no-op until its source page has been visited.
	 */
	private Set<String> missingItemsOf(String entryName)
	{
		PageSnapshot snapshot = pages.get(key(entryName));
		if (snapshot == null || snapshot.missing == null)
		{
			return Collections.emptySet();
		}

		Set<String> items = new LinkedHashSet<>();
		for (String item : snapshot.missing)
		{
			items.add(item.toLowerCase(Locale.ROOT));
		}

		return items;
	}

	/** Splits the config box on newlines and commas. */
	static Set<String> parseItemList(String raw)
	{
		if (raw == null || raw.trim().isEmpty())
		{
			return Collections.emptySet();
		}

		Set<String> items = new LinkedHashSet<>();
		for (String part : raw.split("[,\\r\\n]"))
		{
			String cleaned = Text.removeTags(part).trim().toLowerCase(Locale.ROOT);
			if (!cleaned.isEmpty())
			{
				items.add(cleaned);
			}
		}

		return items;
	}

	private static String key(String pageName)
	{
		return pageName.toLowerCase(Locale.ROOT);
	}

	private void loadCache()
	{
		pages.clear();

		String json = configManager.getRSProfileConfiguration(BlueLogConfig.GROUP, CACHE_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}

		try
		{
			Map<String, PageSnapshot> stored = gson.fromJson(json, CACHE_TYPE);
			if (stored != null)
			{
				pages.putAll(stored);
			}
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Discarding unreadable Blue Log cache", e);
		}
	}

	private void saveCache()
	{
		configManager.setRSProfileConfiguration(BlueLogConfig.GROUP, CACHE_KEY, gson.toJson(pages, CACHE_TYPE));
	}
}
