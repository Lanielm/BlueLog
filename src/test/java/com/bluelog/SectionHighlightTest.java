package com.bluelog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.Test;

public class SectionHighlightTest {
	private static IntPredicate setOf(int... itemIds) {
		Set<Integer> ids = new HashSet<>();
		for (int itemId : itemIds) {
			ids.add(itemId);
		}

		return ids::contains;
	}

	private static final IntPredicate NOTHING = itemId -> false;

	@Test
	public void completedPageIsNotHighlighted() {
		assertFalse(BlueLogPlugin.onlyMissingIgnored(new int[] { 1, 2, 3 }, setOf(1, 2, 3), NOTHING));
	}

	@Test
	public void emptyPageIsNotHighlighted() {
		assertFalse(BlueLogPlugin.onlyMissingIgnored(new int[0], NOTHING, itemId -> true));
	}

	@Test
	public void pageMissingAnUnignoredItemIsNotHighlighted() {
		assertFalse(BlueLogPlugin.onlyMissingIgnored(new int[] { 1, 2, 3 }, setOf(1), setOf(2)));
	}

	@Test
	public void pageMissingOnlyIgnoredItemsIsHighlighted() {
		assertTrue(BlueLogPlugin.onlyMissingIgnored(new int[] { 1, 2, 3 }, setOf(1), setOf(2, 3)));
	}

	@Test
	public void pageWhereEveryItemIsMissingAndIgnoredIsHighlighted() {
		assertTrue(BlueLogPlugin.onlyMissingIgnored(new int[] { 1, 2 }, NOTHING, itemId -> true));
	}

	// The obtained set is keyed by the ids the server transmits, which for items holding data are
	// the replacements of the ids the pages list, so the cache has to hand over the replacements.
	@Test
	public void replacementIdIsWhatCountsAsObtained() {
		int flamtaerBagOnThePage = 12854;
		int flamtaerBagTransmitted = 25630;

		int[] remappedPage = { flamtaerBagTransmitted };
		assertFalse(BlueLogPlugin.onlyMissingIgnored(remappedPage, setOf(flamtaerBagTransmitted), NOTHING));

		int[] unremappedPage = { flamtaerBagOnThePage };
		assertTrue(Arrays.stream(unremappedPage).noneMatch(setOf(flamtaerBagTransmitted)::test));
	}
}
