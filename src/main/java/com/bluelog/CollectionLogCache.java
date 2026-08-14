package com.bluelog;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

@Slf4j
class CollectionLogCache {
	private static final int[] TAB_STRUCT_IDS = { 471, 472, 473, 474, 475 };
	private static final int TAB_PAGES_ENUM_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_ENUM_PARAM = 690;

	private final Client client;

	@Inject
	private CollectionLogCache(Client client) {
		this.client = client;
	}

	int[] pageItemIds(String pageName) {
		try {
			for (int tabStructId : TAB_STRUCT_IDS) {
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

					EnumComposition items = client.getEnum(page.getIntValue(PAGE_ITEMS_ENUM_PARAM));
					if (items == null) {
						return null;
					}

					return items.getIntVals();
				}
			}
		} catch (RuntimeException e) {
			log.warn("Could not read collection log page '{}' from the game cache", pageName, e);
		}

		return null;
	}
}
