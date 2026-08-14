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
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.StructComposition;
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
@PluginDescriptor(name = "BlueLog", description = "Colours a clog section blue when only ignored items are missing", tags = {
		"collection", "log", "clog" })
public class BlueLogPlugin extends Plugin {
	private static final int COMPLETED_PAGE_COLOUR = 0x0dc10d;
	private static final int INCOMPLETE_PAGE_COLOUR = 0xff981f;
	private static final int COLLECTION_LOG_GROUP_ID = InterfaceID.Collection.LIST >>> 16;

	private static final int[] SECTION_LIST_WIDGET_IDS = {
			InterfaceID.Collection.BOSS_TEXT,
			InterfaceID.Collection.RAID_TEXT,
			InterfaceID.Collection.CLUE_TEXT,
			InterfaceID.Collection.MINIGAME_TEXT,
			InterfaceID.Collection.OTHER_TEXT,
	};

	private static final int[] COLLECTION_LOG_TAB_STRUCT_IDS = { 471, 472, 473, 474, 475 };
	private static final int TAB_PAGES_ENUM_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_ENUM_PARAM = 690;

	private static final String PAGE_CACHE_CONFIG_KEY = "pageCache";
	private static final String ALL_PETS_PAGE_NAME = "All Pets";
	private static final String JAR_NAME_PREFIX = "jar of ";

	private static final Type PAGE_CACHE_TYPE = new TypeToken<HashMap<String, PageSnapshot>>() {
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

	private final Map<String, PageSnapshot> snapshotsByPageKey = new HashMap<>();

	private Predicate<String> isIgnoredNormalisedName = name -> false;

	private Set<String> petNamesFromGameCache = Collections.emptySet();

	private void recolourSectionLists() {
		int highlightRGB = config.highlightColour().getRGB();
		int unscannedRGB = config.unscannedColour().getRGB();
		boolean shouldMarkUnscanned = config.highlightUnscanned();

		for (int sectionListWidgetId : SECTION_LIST_WIDGET_IDS) {
			Widget sectionList = client.getWidget(sectionListWidgetId);
			if (sectionList == null) {
				continue;
			}

			for (Widget sectionEntry : sectionEntries(sectionList)) {
				String sectionName = Text.removeTags(Text.sanitize(sectionEntry.getText())).trim();
				if (sectionName.isEmpty()) {
					continue;
				}

				if (sectionEntry.getTextColor() == COMPLETED_PAGE_COLOUR) {
					continue;
				}

				PageSnapshot snapshot = snapshotsByPageKey.get(pageKey(sectionName));
				int colour = INCOMPLETE_PAGE_COLOUR;

				if (snapshot == null) {
					if (shouldMarkUnscanned) {
						colour = unscannedRGB;
					}
				} else if (snapshot.isOnlyMissing(isIgnoredNormalisedName)) {
					colour = highlightRGB;
				}

				sectionEntry.setTextColor(colour);
			}
		}
	}

	private static Widget[] sectionEntries(Widget sectionList) {
		Widget[] children = sectionList.getDynamicChildren();
		if (children != null && children.length > 0) {
			return children;
		}

		return new Widget[] { sectionList };
	}

	boolean isIgnoredItemSlot(Widget slot) {
		return isIgnoredNormalisedName.test(normalisedName(itemName(slot.getItemId())));
	}

	@Override
	protected void startUp() {
		loadPageCache();
		rebuildIgnoredItemTest();
		overlayManager.add(overlay);

		clientThread.invokeLater(() -> {
			if (loadPetNamesIfNeeded()) {
				rebuildIgnoredItemTest();
			}

			recolourSectionLists();
		});
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay);
		snapshotsByPageKey.clear();
		petNamesFromGameCache = Collections.emptySet();
		isIgnoredNormalisedName = name -> false;
	}

	@Provides
	BlueLogConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(BlueLogConfig.class);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST) {
			return;
		}

		recolourSectionLists();

		clientThread.invokeLater(() -> {
			// Second chance at the pet list: startUp can run before the game cache is
			// reachable, but the log being open means it certainly is now.
			boolean petsLoaded = loadPetNamesIfNeeded();
			boolean pageChanged = snapshotOpenPageIfChanged();

			if (petsLoaded || pageChanged) {
				// The pets preset falls back to the All Pets snapshot, so the page that just
				// changed may have changed the ignore test with it.
				rebuildIgnoredItemTest();
				recolourSectionLists();
			}
		});
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		for (MenuEntry entry : event.getMenuEntries()) {
			Widget slot = entry.getWidget();
			if (slot == null || !isCollectionLogSlot(slot) || slot.getItemId() <= 0) {
				continue;
			}

			addIgnoreMenuEntry(itemName(slot.getItemId()));
			return;
		}
	}

	/** Dynamic children report their parent as their id, so accept either form. */
	private static boolean isCollectionLogSlot(Widget slot) {
		return slot.getId() == InterfaceID.Collection.ITEMS_CONTENTS
				|| slot.getParentId() == InterfaceID.Collection.ITEMS_CONTENTS;
	}

	// Presets are deliberately not consulted: this entry edits the hand written
	// list, which is the only part it can change.
	private void addIgnoreMenuEntry(String itemName) {
		boolean alreadyIgnored = containsNormalisedName(ignoredItemsAsTyped(), itemName);

		client.getMenu()
				.createMenuEntry(-1)
				.setOption(alreadyIgnored ? "Unignore item" : "Ignore item")
				.setTarget("<col=ff9040>" + itemName + "</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> toggleIgnoredItemInConfig(itemName));
	}

	private void toggleIgnoredItemInConfig(String itemName) {
		List<String> ignoredItems = ignoredItemsAsTyped();

		String normalisedTarget = normalisedName(itemName);
		if (!ignoredItems.removeIf(existing -> normalisedName(existing).equals(normalisedTarget))) {
			ignoredItems.add(itemName);
		}

		// Writing the config fires ConfigChanged, which rebuilds the ignored test and
		// repaints.
		configManager.setConfiguration(BlueLogConfig.GROUP, BlueLogConfig.IGNORED_ITEMS_KEY, Text.toCSV(ignoredItems));

		// RuneLite's config panel rebuilds on PluginChanged, ExternalPluginsChanged and
		// ProfileChanged, but never on ConfigChanged, so an open settings panel would
		// keep showing the old text and write it back over this change when the field
		// next loses focus. ExternalPluginsChanged is the narrowest of the three that
		// forces a rebuild: it is only observed by the config and plugin list panels,
		// whereas ProfileChanged would make a couple of dozen plugins reload their
		// state.
		eventBus.post(new ExternalPluginsChanged());
	}

	private List<String> ignoredItemsAsTyped() {
		String configuredText = config.ignoredItems();
		if (configuredText == null) {
			return Collections.emptyList();
		}

		List<String> ignoredItems = new ArrayList<>();
		for (String part : Text.fromCSV(configuredText)) {
			String cleaned = Text.removeTags(part).trim();
			if (!cleaned.isEmpty()) {
				ignoredItems.add(cleaned);
			}
		}

		return ignoredItems;
	}

	private static boolean containsNormalisedName(List<String> items, String name) {
		String normalisedTarget = normalisedName(name);
		for (String item : items) {
			if (normalisedName(item).equals(normalisedTarget)) {
				return true;
			}
		}

		return false;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == COLLECTION_LOG_GROUP_ID) {
			clientThread.invokeLater(this::recolourSectionLists);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!BlueLogConfig.GROUP.equals(event.getGroup())) {
			return;
		}

		rebuildIgnoredItemTest();
		clientThread.invokeLater(this::recolourSectionLists);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
		// Collection log progress is per character, so swap in that character's cache.
		loadPageCache();
		rebuildIgnoredItemTest();
		clientThread.invokeLater(this::recolourSectionLists);
	}

	private boolean snapshotOpenPageIfChanged() {
		String pageName = openPageName();
		if (pageName == null || pageName.isEmpty()) {
			return false;
		}

		// Slots are created in ITEMS_CONTENTS. ITEMS is only the outer container that
		// gets resized.
		Widget itemsContainer = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (itemsContainer == null) {
			return false;
		}

		Widget[] slots = itemsContainer.getDynamicChildren();
		if (slots == null || slots.length == 0) {
			// Page has not rendered its items yet; keep whatever we already knew.
			return false;
		}

		List<String> missingItems = new ArrayList<>();
		int itemSlotCount = 0;

		for (Widget slot : slots) {
			int itemId = slot.getItemId();
			if (itemId <= 0) {
				continue;
			}

			itemSlotCount++;

			// Obtained items are drawn fully opaque; missing ones are faded out by the
			// game.
			if (slot.getOpacity() == 0) {
				continue;
			}

			String itemName = itemName(itemId);
			if (!itemName.isEmpty()) {
				missingItems.add(itemName);
			}
		}

		if (itemSlotCount == 0) {
			return false;
		}

		PageSnapshot snapshot = new PageSnapshot(pageName, missingItems, itemSlotCount, System.currentTimeMillis());
		PageSnapshot previousSnapshot = snapshotsByPageKey.get(pageKey(pageName));
		if (snapshot.sameContentAs(previousSnapshot)) {
			return false;
		}

		snapshotsByPageKey.put(pageKey(pageName), snapshot);
		savePageCache();
		return true;
	}

	private String openPageName() {
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		if (header == null) {
			header = client.getWidget(InterfaceID.Collection.HEADER);
		}

		if (header == null) {
			return null;
		}

		String headerText = firstNonEmptyText(header);
		return headerText == null ? null : Text.removeTags(headerText).trim();
	}

	// The header is sometimes the text widget itself and sometimes a container
	// whose first line is the section name, so check the widget and then its
	// children.
	private static String firstNonEmptyText(Widget header) {
		String ownText = header.getText();
		if (ownText != null && !ownText.isEmpty()) {
			return ownText;
		}

		for (Widget[] children : Arrays.asList(header.getDynamicChildren(), header.getStaticChildren())) {
			if (children == null) {
				continue;
			}

			for (Widget child : children) {
				String childText = child.getText();
				if (childText != null && !childText.isEmpty()) {
					return childText;
				}
			}
		}

		return null;
	}

	// Taken from the item definition rather than the slot widget, whose name
	// carries
	// the log's own decorations: colour tags and a "(Members)" suffix on free
	// worlds.
	private String itemName(int itemId) {
		return itemManager.getItemComposition(itemId).getName().trim();
	}

	private void rebuildIgnoredItemTest() {
		Set<String> ignoredNames = new LinkedHashSet<>(parseNormalisedItemNames(config.ignoredItems()));

		if (config.ignoreAllPets()) {
			// The snapshot only knows the pets missing at the player's last visit, so it is
			// the fallback for when the game cache lookup found nothing.
			ignoredNames.addAll(petNamesFromGameCache.isEmpty()
					? normalisedMissingItemsOf(ALL_PETS_PAGE_NAME)
					: petNamesFromGameCache);
		}

		boolean ignoreAllJars = config.ignoreAllJars();
		isIgnoredNormalisedName = name -> ignoredNames.contains(name)
				|| (ignoreAllJars && name.startsWith(JAR_NAME_PREFIX));
	}

	/** @return true when the pet list was read for the first time. */
	private boolean loadPetNamesIfNeeded() {
		if (!petNamesFromGameCache.isEmpty()) {
			return false;
		}

		Set<String> petNames = readPageItemNames(ALL_PETS_PAGE_NAME);
		if (petNames.isEmpty()) {
			return false;
		}

		petNamesFromGameCache = petNames;
		return true;
	}

	/**
	 * Every item on a collection log page, read from the game cache rather than
	 * from the widgets, so the page does not have to have been opened. Must run on
	 * the client thread.
	 */
	private Set<String> readPageItemNames(String pageName) {
		try {
			for (int tabStructId : COLLECTION_LOG_TAB_STRUCT_IDS) {
				StructComposition tab = client.getStructComposition(tabStructId);
				if (tab == null) {
					continue;
				}

				EnumComposition pages = client.getEnum(tab.getIntValue(TAB_PAGES_ENUM_PARAM));
				if (pages == null) {
					continue;
				}

				for (int pageStructId : pages.getIntVals()) {
					StructComposition page = client.getStructComposition(pageStructId);
					if (page == null || !pageName.equalsIgnoreCase(page.getStringValue(PAGE_NAME_PARAM))) {
						continue;
					}

					return itemNamesOf(page.getIntValue(PAGE_ITEMS_ENUM_PARAM));
				}
			}
		} catch (RuntimeException e) {
			// A cache id that no longer resolves must not take the plugin down with it.
			log.warn("Could not read collection log page '{}' from the game cache", pageName, e);
		}

		return Collections.emptySet();
	}

	private Set<String> itemNamesOf(int itemsEnumId) {
		EnumComposition items = client.getEnum(itemsEnumId);
		if (items == null) {
			return Collections.emptySet();
		}

		Set<String> names = new LinkedHashSet<>();
		for (int itemId : items.getIntVals()) {
			String name = itemName(itemId);
			if (!name.isEmpty()) {
				names.add(normalisedName(name));
			}
		}

		return names;
	}

	private Set<String> normalisedMissingItemsOf(String pageName) {
		PageSnapshot snapshot = snapshotsByPageKey.get(pageKey(pageName));
		if (snapshot == null || snapshot.missing == null) {
			return Collections.emptySet();
		}

		Set<String> normalisedItems = new LinkedHashSet<>();
		for (String item : snapshot.missing) {
			normalisedItems.add(normalisedName(item));
		}

		return normalisedItems;
	}

	static Set<String> parseNormalisedItemNames(String configuredText) {
		if (configuredText == null || configuredText.trim().isEmpty()) {
			return Collections.emptySet();
		}

		Set<String> normalisedItems = new LinkedHashSet<>();
		for (String part : Text.fromCSV(configuredText)) {
			String normalised = normalisedName(Text.removeTags(part));
			if (!normalised.isEmpty()) {
				normalisedItems.add(normalised);
			}
		}

		return normalisedItems;
	}

	static String normalisedName(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	private static String pageKey(String pageName) {
		return pageName.toLowerCase(Locale.ROOT);
	}

	private void loadPageCache() {
		snapshotsByPageKey.clear();

		String json = configManager.getRSProfileConfiguration(BlueLogConfig.GROUP, PAGE_CACHE_CONFIG_KEY);
		if (json == null || json.isEmpty()) {
			return;
		}

		try {
			Map<String, PageSnapshot> storedSnapshots = gson.fromJson(json, PAGE_CACHE_TYPE);
			if (storedSnapshots != null) {
				snapshotsByPageKey.putAll(storedSnapshots);
			}
		} catch (JsonSyntaxException e) {
			log.warn("Discarding unreadable BlueLog cache", e);
		}
	}

	private void savePageCache() {
		configManager.setRSProfileConfiguration(BlueLogConfig.GROUP, PAGE_CACHE_CONFIG_KEY,
				gson.toJson(snapshotsByPageKey, PAGE_CACHE_TYPE));
	}
}
