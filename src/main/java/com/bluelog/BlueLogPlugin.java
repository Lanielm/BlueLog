package com.bluelog;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
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
@PluginDescriptor(name = "BlueLog", description = "Colours a collection log section blue when the only items you are still missing are ones you have ignored, and marks those ignored items in the log", tags = {
		"collection", "log", "clog" })
public class BlueLogPlugin extends Plugin {
	private static final String ALL_PETS_PAGE_NAME = "All Pets";
	private static final String JAR_NAME_PREFIX = "jar of ";

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

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private CollectionLogCache collectionLogCache;

	@Inject
	private PageSnapshots pageSnapshots;

	@Inject
	private BlueLogConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BlueLogOverlay overlay;

	private Predicate<String> isIgnored = name -> false;

	private Set<String> petNames;

	boolean didPetNamesLoad() {
		return petNames != null && !petNames.isEmpty();
	}

	@Provides
	BlueLogConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(BlueLogConfig.class);
	}

	private void rebuildIgnoredItemTest() {
		Set<String> ignoredNames = new LinkedHashSet<>(BLUtils.textToItemNamesSet(config.ignoredItems()));

		if (config.ignoreAllPets() && didPetNamesLoad()) {
			ignoredNames.addAll(petNames);
		}

		boolean ignoreAllJars = config.ignoreAllJars();
		isIgnored = name -> ignoredNames.contains(name)
				|| (ignoreAllJars && name.startsWith(JAR_NAME_PREFIX));
	}

	private void loadPetNames() {
		int[] petItemIds = collectionLogCache.pageItemIds(ALL_PETS_PAGE_NAME);

		if (petItemIds == null) {
			log.warn("Could not load pet names from the game cache");
			return;
		}

		Set<String> petNamesLocal = new LinkedHashSet<>();
		for (int petItemId : petItemIds) {
			String petName = getItemIdName(petItemId);
			if (petName != null && !petName.isEmpty()) {
				petNamesLocal.add(BLUtils.normalizeString(petName));
			} else {
				log.warn("Could not load pet name for item ID {}", petItemId);
			}
		}

		if (!petNamesLocal.isEmpty()) {
			petNames = petNamesLocal;
		} else {
			log.warn("No pet names were loaded from the game cache");
		}
	}

	private static Widget[] sectionEntries(Widget sectionList) {
		Widget[] children = sectionList.getDynamicChildren();
		if (children != null && children.length > 0) {
			return children;
		}

		return new Widget[] { sectionList };
	}

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

				PageSnapshot snapshot = pageSnapshots.get(sectionName);
				int colour = INCOMPLETE_PAGE_COLOUR;

				if (snapshot == null) {
					if (shouldMarkUnscanned) {
						colour = unscannedRGB;
					}
				} else if (snapshot.isOnlyMissing(isIgnored)) {
					colour = highlightRGB;
				}

				sectionEntry.setTextColor(colour);
			}
		}
	}

	private static boolean isCollectionLogSlot(Widget slot) {
		return slot.getId() == InterfaceID.Collection.ITEMS_CONTENTS
				|| slot.getParentId() == InterfaceID.Collection.ITEMS_CONTENTS;
	}

	private void addIgnoreMenuEntry(String itemName) {
		boolean alreadyIgnored = BLUtils.textToItemNamesSet(config.ignoredItems())
				.contains(BLUtils.normalizeString(itemName));

		client.getMenu()
				.createMenuEntry(-1)
				.setOption(alreadyIgnored ? "Unignore item" : "Ignore item")
				.setTarget("<col=ff9040>" + itemName + "</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> toggleIgnoredItemInConfig(itemName));
	}

	private void toggleIgnoredItemInConfig(String itemName) {
		List<String> ignoredItems = new ArrayList<>(BLUtils.textToItemNames(config.ignoredItems()));

		String normalisedTarget = BLUtils.normalizeString(itemName);
		if (!ignoredItems.removeIf(existing -> BLUtils.normalizeString(existing).equals(normalisedTarget))) {
			ignoredItems.add(itemName);
		}

		configManager.setConfiguration(BlueLogConfig.GROUP, BlueLogConfig.IGNORED_ITEMS_KEY, Text.toCSV(ignoredItems));
		eventBus.post(new ExternalPluginsChanged());
	}

	private boolean isItemIgnored(String itemName) {
		return isIgnored.test(BLUtils.normalizeString(itemName));
	}

	private String getItemIdName(int itemId) {
		return BLUtils.stripMembersSuffix(itemManager.getItemComposition(itemId).getName());
	}

	boolean isItemIdIgnored(int itemId) {
		return isItemIgnored(getItemIdName(itemId));
	}

	@Override
	protected void startUp() {
		overlayManager.add(overlay);
		pageSnapshots.load();
		clientThread.invokeLater(this::rebuildIgnoredItemTest);
	}

	@Override
	protected void shutDown() {
		pageSnapshots.flush();
		overlayManager.remove(overlay);
		isIgnored = name -> false;
		petNames = null;
		pageSnapshots.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!BlueLogConfig.GROUP.equals(event.getGroup())) {
			return;
		}

		clientThread.invokeLater(() -> {
			rebuildIgnoredItemTest();
			recolourSectionLists();
		});
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		for (MenuEntry entry : event.getMenuEntries()) {
			Widget slot = entry.getWidget();
			if (slot == null || !isCollectionLogSlot(slot) || slot.getItemId() <= 0) {
				continue;
			}

			addIgnoreMenuEntry(getItemIdName(slot.getItemId()));
			return;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST) {
			return;
		}

		if (!didPetNamesLoad()) {
			loadPetNames();
			rebuildIgnoredItemTest();
		}

		recolourSectionLists();

		clientThread.invokeLater(() -> {
			if (pageSnapshots.captureOpenPage()) {
				recolourSectionLists();
			}
		});
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
		pageSnapshots.load();
		clientThread.invokeLater(this::recolourSectionLists);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		if (event.getGroupId() == COLLECTION_LOG_GROUP_ID) {
			pageSnapshots.flush();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == COLLECTION_LOG_GROUP_ID) {
			clientThread.invokeLater(this::recolourSectionLists);
		}
	}
}
