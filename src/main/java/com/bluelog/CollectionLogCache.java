package com.bluelog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

@Slf4j
@Singleton
class CollectionLogCache {
	private static final int TOP_LEVEL_TABS_ENUM = 2102;
	private static final int TAB_PAGES_ENUM_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_ENUM_PARAM = 690;

	// Items that store data (satchels, bags, the Unsired) were given a second id to
	// fix a duping issue. The pages still list the original id while the server
	// transmits the replacement, so page ids have to be translated before they can
	// be compared against what was obtained.
	private static final int ITEM_REPLACEMENTS_ENUM = 3721;

	private final Client client;

	private Map<String, int[]> pagesByKey;

	@Inject
	private CollectionLogCache(Client client) {
		this.client = client;
	}

	void clear() {
		pagesByKey = null;
	}

	boolean isLoaded() {
		return pagesByKey != null && !pagesByKey.isEmpty();
	}

	Map<String, int[]> pages() {
		if (pagesByKey == null) {
			pagesByKey = readPages();
		}

		return pagesByKey;
	}

	int[] pageItemIds(String pageName) {
		return pages().get(BLUtils.normalizeString(pageName));
	}

	private Map<String, int[]> readPages() {
		Map<Integer, Integer> replacements = readReplacements();
		Map<String, int[]> pages = new LinkedHashMap<>();

		try {
			for (int tabStructId : client.getEnum(TOP_LEVEL_TABS_ENUM).getIntVals()) {
				int pagesEnumId = client.getStructComposition(tabStructId).getIntValue(TAB_PAGES_ENUM_PARAM);

				for (int pageStructId : client.getEnum(pagesEnumId).getIntVals()) {
					StructComposition page = client.getStructComposition(pageStructId);
					String pageName = page.getStringValue(PAGE_NAME_PARAM);
					if (pageName == null || pageName.isEmpty()) {
						continue;
					}

					int[] itemIds = client.getEnum(page.getIntValue(PAGE_ITEMS_ENUM_PARAM)).getIntVals().clone();
					for (int i = 0; i < itemIds.length; i++) {
						itemIds[i] = replacements.getOrDefault(itemIds[i], itemIds[i]);
					}

					pages.put(BLUtils.normalizeString(pageName), itemIds);
				}
			}
		} catch (RuntimeException e) {
			log.warn("Could not read the collection log pages from the game cache", e);
			return Collections.emptyMap();
		}

		log.debug("Read {} collection log pages from the game cache", pages.size());
		return pages;
	}

	private Map<Integer, Integer> readReplacements() {
		Map<Integer, Integer> replacements = new LinkedHashMap<>();

		try {
			EnumComposition enumComposition = client.getEnum(ITEM_REPLACEMENTS_ENUM);
			int[] keys = enumComposition.getKeys();
			int[] values = enumComposition.getIntVals();

			for (int i = 0; i < keys.length && i < values.length; i++) {
				replacements.put(keys[i], values[i]);
			}
		} catch (RuntimeException e) {
			log.warn("Could not read the collection log item replacements from the game cache", e);
		}

		return replacements;
	}
}
