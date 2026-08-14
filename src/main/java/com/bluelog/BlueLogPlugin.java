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
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "BlueLog",
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

	/**
	 * Every boss jar in the game is named "Jar of ...", so the jars preset matches on the prefix
	 * rather than a fixed list that would need updating whenever a new one is released.
	 */
	private static final String JAR_PREFIX = "jar of ";

	/**
	 * On free worlds the game renders members-only items as "Unsired (Members)". The suffix is a
	 * display decoration rather than part of the item name, so it is stripped everywhere: the text
	 * box stays readable, and a list written on a members account still matches on a free one.
	 */
	private static final Pattern MEMBERS_SUFFIX = Pattern.compile("\\s*\\(members\\)$", Pattern.CASE_INSENSITIVE);

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
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BlueLogOverlay overlay;

	/** Everything we know about pages the player has opened, keyed by lowercase page name. */
	private final Map<String, PageSnapshot> pages = new HashMap<>();

	/**
	 * Tests whether an item is ignored, by lowercase name. Combines the config text box with
	 * whichever preset lists are switched on.
	 */
	private Predicate<String> ignoredItem = name -> false;

	@Override
	protected void startUp()
	{
		// Cache first: the presets are derived from it.
		loadCache();
		refreshIgnoredItems();
		overlayManager.add(overlay);
		clientThread.invokeLater(this::recolourList);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		pages.clear();
		ignoredItem = name -> false;
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
				refreshIgnoredItems();
				recolourList();
			}
		});
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		for (MenuEntry entry : event.getMenuEntries())
		{
			Widget slot = entry.getWidget();
			if (slot == null || !isCollectionLogSlot(slot) || slot.getItemId() <= 0)
			{
				continue;
			}

			addIgnoreOption(itemName(slot, slot.getItemId()));
			return;
		}
	}

	/** Dynamic children report their parent as their id, so accept either form. */
	private static boolean isCollectionLogSlot(Widget slot)
	{
		return slot.getId() == InterfaceID.Collection.ITEMS_CONTENTS
			|| slot.getParentId() == InterfaceID.Collection.ITEMS_CONTENTS;
	}

	/**
	 * Adds an entry that toggles the item in the config text box. Presets are deliberately not
	 * consulted: this option edits the hand written list, which is the only part it can change.
	 */
	private void addIgnoreOption(String itemName)
	{
		boolean listed = containsIgnoringCase(configuredItems(), itemName);

		client.getMenu()
			.createMenuEntry(-1)
			.setOption(listed ? "Unignore item" : "Ignore item")
			.setTarget("<col=ff9040>" + itemName + "</col>")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggleConfiguredItem(itemName));
	}

	private void toggleConfiguredItem(String itemName)
	{
		List<String> items = configuredItems();

		// Compare normalised so a legacy entry saved as "Unsired (Members)" is still removed.
		String target = normalisedName(itemName);
		if (!items.removeIf(existing -> normalisedName(existing).equals(target)))
		{
			items.add(itemName);
		}

		// Writing the config fires ConfigChanged, which rebuilds the ignored set and repaints.
		configManager.setConfiguration(BlueLogConfig.GROUP, BlueLogConfig.IGNORED_ITEMS_KEY, String.join(", ", items));

		// RuneLite's config panel rebuilds on PluginChanged, ExternalPluginsChanged and
		// ProfileChanged, but never on ConfigChanged, so an open settings panel would keep showing
		// the old text and write it back over this change when the field next loses focus.
		// ExternalPluginsChanged is the narrowest of the three that forces a rebuild: it is only
		// observed by the config and plugin list panels, whereas ProfileChanged would make a couple
		// of dozen plugins reload their state.
		eventBus.post(new ExternalPluginsChanged());
	}

	/** The text box contents as individual names, with the user's own capitalisation preserved. */
	private List<String> configuredItems()
	{
		List<String> items = new ArrayList<>();
		String raw = config.ignoredItems();
		if (raw == null)
		{
			return items;
		}

		for (String part : raw.split("[,\\r\\n]"))
		{
			String cleaned = Text.removeTags(part).trim();
			if (!cleaned.isEmpty())
			{
				items.add(cleaned);
			}
		}

		return items;
	}

	private static boolean containsIgnoringCase(List<String> items, String name)
	{
		String target = normalisedName(name);
		for (String item : items)
		{
			if (normalisedName(item).equals(target))
			{
				return true;
			}
		}

		return false;
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

		refreshIgnoredItems();
		clientThread.invokeLater(this::recolourList);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Collection log progress is per character, so swap in that character's cache.
		loadCache();
		refreshIgnoredItems();
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
	 * ignored item away from completion.
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
				else if (snapshot.isOnlyMissing(ignoredItem))
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
		String name = stripMembersSuffix(Text.removeTags(Text.sanitize(slot.getName())));
		if (!name.isEmpty())
		{
			return name;
		}

		return itemManager.getItemComposition(itemId).getName().trim();
	}

	/**
	 * Whether a collection log item slot holds an ignored item. Used by
	 * the overlay to mark those slots in the open section.
	 */
	boolean isIgnoredItem(Widget slot)
	{
		return ignoredItem.test(normalisedName(itemName(slot, slot.getItemId())));
	}

	/**
	 * Rebuilds the ignored test from the config text box plus any preset lists that are switched on.
	 * Cheap enough to redo whenever the config or the cache changes.
	 */
	private void refreshIgnoredItems()
	{
		Set<String> names = new LinkedHashSet<>(parseItemList(config.ignoredItems()));

		if (config.ignoreAllPets())
		{
			names.addAll(missingItemsOf(ALL_PETS_ENTRY));
		}

		boolean jars = config.ignoreAllJars();
		ignoredItem = name -> names.contains(name) || (jars && name.startsWith(JAR_PREFIX));
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
			items.add(normalisedName(item));
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
			String cleaned = normalisedName(Text.removeTags(part));
			if (!cleaned.isEmpty())
			{
				items.add(cleaned);
			}
		}

		return items;
	}

	/** An item name reduced to its comparable form: no members suffix, no case, no padding. */
	static String normalisedName(String name)
	{
		return stripMembersSuffix(name).toLowerCase(Locale.ROOT);
	}

	/** An item name as it should be displayed and stored, keeping its original capitalisation. */
	static String stripMembersSuffix(String name)
	{
		return MEMBERS_SUFFIX.matcher(name.trim()).replaceFirst("").trim();
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
			log.warn("Discarding unreadable BlueLog cache", e);
		}
	}

	private void saveCache()
	{
		configManager.setRSProfileConfiguration(BlueLogConfig.GROUP, CACHE_KEY, gson.toJson(pages, CACHE_TYPE));
	}
}
