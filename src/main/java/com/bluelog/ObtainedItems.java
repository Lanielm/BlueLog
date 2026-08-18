package com.bluelog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

// The core idea used below comes from WikiSync and RuneProfile: trigger a collection log search 
// operation to make the server send the whole log, reading the item id and quantity from the 
// arguments of script 4100, and treating the stream as finished once a couple of ticks pass.

// This plugin waits a few ticks before creating its own search op so as to avoid sending a 
// duplicate request if another plugin has already done so. 
@Slf4j
@Singleton
class ObtainedItems {
	private static final int COLLECTION_DELAYED_TRANSMIT = 4100;
	private static final int COLLECTION_LOG_SETUP = 7797;
	private static final int COLLECTION_INIT = 2240;

	private static final int TICKS_TO_LET_OTHERS_REQUEST = 3;
	private static final int TICKS_AFTER_LAST_TRANSMIT = 2;
	private static final int TICKS_BEFORE_GIVING_UP = 10;

	private static final String OBTAINED_ITEMS_KEY = "obtainedItems";

	private final Client client;
	private final EventBus eventBus;
	private final ConfigManager configManager;

	private final Set<Integer> obtained = new HashSet<>();
	private final Set<Integer> arriving = new HashSet<>();

	private Runnable onUpdated = () -> {
	};

	private boolean loaded;
	private int openTick = -1;
	private int requestTick = -1;
	private int lastTransmitTick = -1;

	@Inject
	private ObtainedItems(Client client, EventBus eventBus, ConfigManager configManager) {
		this.client = client;
		this.eventBus = eventBus;
		this.configManager = configManager;
	}

	void startUp(Runnable onUpdated) {
		this.onUpdated = onUpdated;
		eventBus.register(this);
		load();
	}

	void shutDown() {
		eventBus.unregister(this);
		clear();
	}

	void clear() {
		obtained.clear();
		loaded = false;
		resetCollection();
	}

	boolean isLoaded() {
		return loaded;
	}

	boolean contains(int itemId) {
		return obtained.contains(itemId);
	}

	private void resetCollection() {
		arriving.clear();
		openTick = -1;
		requestTick = -1;
		lastTransmitTick = -1;
	}

	private boolean isOpenedFromAdventureLog() {
		return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
	}

	private int obtainedSlotCount() {
		return client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		switch (event.getGameState()) {
			case HOPPING:
			case LOGGING_IN:
			case CONNECTION_LOST:
				resetCollection();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() != COLLECTION_LOG_SETUP) {
			return;
		}

		if (isOpenedFromAdventureLog()) {
			resetCollection();
			return;
		}

		if (openTick != -1) {
			return;
		}

		resetCollection();
		openTick = client.getTickCount();
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		if (event.getScriptId() != COLLECTION_DELAYED_TRANSMIT || isOpenedFromAdventureLog()) {
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		int itemId = (int) args[1];
		int quantity = (int) args[2];

		lastTransmitTick = client.getTickCount();

		if (quantity > 0) {
			arriving.add(itemId);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		int tick = client.getTickCount();

		if (lastTransmitTick != -1) {
			if (tick > lastTransmitTick + TICKS_AFTER_LAST_TRANSMIT) {
				onTransmitsSettled();
			}
			return;
		}

		if (requestTick != -1) {
			if (tick > requestTick + TICKS_BEFORE_GIVING_UP) {
				log.debug("Nothing was transmitted within {} ticks of asking for the collection log",
						TICKS_BEFORE_GIVING_UP);
				resetCollection();
			}
			return;
		}

		if (openTick != -1 && tick > openTick + TICKS_TO_LET_OTHERS_REQUEST) {
			request();
		}
	}

	private void request() {
		requestTick = client.getTickCount();

		if (openTick == -1) {
			openTick = requestTick;
		}
		client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
		client.runScript(COLLECTION_INIT);
	}

	private void onTransmitsSettled() {
		int expected = obtainedSlotCount();

		if (arriving.size() < expected && requestTick == -1) {
			log.debug("Only {} of {} slots arrived, so that was a page load; asking for the whole log",
					arriving.size(), expected);
			arriving.clear();
			lastTransmitTick = -1;
			request();
			return;
		}

		obtained.clear();
		obtained.addAll(arriving);
		loaded = true;
		log.debug("Collected {} obtained collection log items", obtained.size());

		store();
		resetCollection();
		onUpdated.run();
	}

	// Kept per character so the log is already coloured the moment it opens, rather
	// than several seconds later when the transmits land. A fresh collection
	// overwrites it every time.
	void load() {
		obtained.clear();
		loaded = false;
		resetCollection();

		String stored = configManager.getRSProfileConfiguration(BlueLogConfig.GROUP, OBTAINED_ITEMS_KEY);
		if (stored == null || stored.isEmpty()) {
			return;
		}

		for (String itemId : stored.split(",")) {
			try {
				obtained.add(Integer.parseInt(itemId.trim()));
			} catch (NumberFormatException e) {
				log.warn("Discarding an unreadable stored collection log");
				obtained.clear();
				return;
			}
		}

		loaded = !obtained.isEmpty();
		log.debug("Loaded {} obtained collection log items for this character", obtained.size());
	}

	private void store() {
		List<Integer> sorted = new ArrayList<>(obtained);
		sorted.sort(null);

		StringBuilder sb = new StringBuilder();
		for (Integer itemId : sorted) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(itemId);
		}

		String csv = sb.toString();
		if (!csv.equals(configManager.getRSProfileConfiguration(BlueLogConfig.GROUP, OBTAINED_ITEMS_KEY))) {
			configManager.setRSProfileConfiguration(BlueLogConfig.GROUP, OBTAINED_ITEMS_KEY, csv);
		}
	}
}
