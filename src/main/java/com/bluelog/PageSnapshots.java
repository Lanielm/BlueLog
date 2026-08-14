package com.bluelog;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

@Slf4j
class PageSnapshots {
	private static final String PAGE_CACHE_CONFIG_KEY = "pageCache";

	private static final Type PAGE_CACHE_TYPE = new TypeToken<HashMap<String, PageSnapshot>>() {
	}.getType();

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final Gson gson;

	private final Map<String, PageSnapshot> byPageKey = new HashMap<>();

	@Inject
	private PageSnapshots(Client client, ItemManager itemManager, ConfigManager configManager, Gson gson) {
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.gson = gson;
	}

	PageSnapshot get(String pageName) {
		return byPageKey.get(BLUtils.normalizeString(pageName));
	}

	void clear() {
		byPageKey.clear();
	}

	void load() {
		byPageKey.clear();

		String json = configManager.getRSProfileConfiguration(BlueLogConfig.GROUP, PAGE_CACHE_CONFIG_KEY);
		if (json == null || json.isEmpty()) {
			return;
		}

		try {
			Map<String, PageSnapshot> storedSnapshots = gson.fromJson(json, PAGE_CACHE_TYPE);
			if (storedSnapshots != null) {
				byPageKey.putAll(storedSnapshots);
			}
		} catch (JsonSyntaxException e) {
			log.warn("Discarding unreadable BlueLog page cache", e);
		}
	}

	private void save() {
		configManager.setRSProfileConfiguration(BlueLogConfig.GROUP, PAGE_CACHE_CONFIG_KEY,
				gson.toJson(byPageKey, PAGE_CACHE_TYPE));
	}

	private static boolean isSlotObtained(Widget slot) {
		return slot.getOpacity() == 0;
	}

	boolean captureOpenPage() {
		String pageName = openPageName();
		if (pageName == null || pageName.isEmpty()) {
			return false;
		}

		Widget itemsContents = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (itemsContents == null) {
			return false;
		}

		Widget[] slots = itemsContents.getDynamicChildren();
		if (slots == null || slots.length == 0) {
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

			if (isSlotObtained(slot)) {
				continue;
			}

			String itemName = BLUtils.stripMembersSuffix(itemManager.getItemComposition(itemId).getName());
			if (!itemName.isEmpty()) {
				missingItems.add(itemName);
			}
		}

		if (itemSlotCount == 0) {
			return false;
		}

		PageSnapshot snapshot = new PageSnapshot(pageName, missingItems, itemSlotCount, System.currentTimeMillis());
		if (snapshot.sameContentAs(get(pageName))) {
			return false;
		}

		byPageKey.put(BLUtils.normalizeString(pageName), snapshot);
		save();
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
}
