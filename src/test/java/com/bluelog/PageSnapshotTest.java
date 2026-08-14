package com.bluelog;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PageSnapshotTest
{
	private static PageSnapshot page(String... missing)
	{
		return new PageSnapshot("Test Page", Arrays.asList(missing), 10, 0L);
	}

	/** Mirrors how the plugin builds its ignored test from the config text box alone. */
	private static Predicate<String> ignoring(String configBox)
	{
		return BlueLogPlugin.parseNormalisedItemNames(configBox)::contains;
	}

	@Test
	public void completePageIsNeverHighlighted()
	{
		assertTrue(page().isComplete());
		assertFalse(page().isOnlyMissing(ignoring("Twisted bow")));
	}

	@Test
	public void singleIgnoredMissingItemIsHighlighted()
	{
		assertTrue(page("Twisted bow").isOnlyMissing(ignoring("Twisted bow")));
	}

	@Test
	public void matchingIgnoresCaseAndSurroundingSpace()
	{
		assertTrue(page("Twisted bow").isOnlyMissing(ignoring("  TWISTED BOW  ")));
	}

	@Test
	public void unlistedMissingItemBlocksHighlight()
	{
		assertFalse(page("Twisted bow", "Elder maul").isOnlyMissing(ignoring("Twisted bow")));
	}

	@Test
	public void everyMissingItemListedIsHighlighted()
	{
		Set<String> ignored = BlueLogPlugin.parseNormalisedItemNames("Twisted bow, Elder maul");
		assertTrue(page("Twisted bow", "Elder maul").isOnlyMissing(ignored::contains));
	}

	@Test
	public void emptyListHighlightsNothing()
	{
		assertFalse(page("Twisted bow").isOnlyMissing(Collections.<String>emptySet()::contains));
	}

	@Test
	public void jarPresetMatchesEveryJarByPrefix()
	{
		Predicate<String> jars = name -> name.startsWith("jar of ");

		assertTrue(page("Jar of dirt").isOnlyMissing(jars));
		assertTrue(page("Jar of swamp", "Jar of congealed blood").isOnlyMissing(jars));
		assertFalse(page("Jar of dirt", "Twisted bow").isOnlyMissing(jars));
		assertFalse(page("Jar generator").isOnlyMissing(jars));
	}

	@Test
	public void parsesCommaSeparatedNames()
	{
		assertEquals(
			Arrays.asList("twisted bow", "elder maul", "kodai insignia"),
			new java.util.ArrayList<>(BlueLogPlugin.parseNormalisedItemNames("Twisted bow,Elder maul,  Kodai insignia ")));
	}

	@Test
	public void skipsEmptyEntries()
	{
		assertEquals(
			Arrays.asList("twisted bow", "elder maul"),
			new java.util.ArrayList<>(BlueLogPlugin.parseNormalisedItemNames("Twisted bow,, Elder maul,")));
	}

	@Test
	public void bracketedNamesAreKeptVerbatim()
	{
		assertEquals(
			Collections.singletonList("unsired (members)"),
			new java.util.ArrayList<>(BlueLogPlugin.parseNormalisedItemNames("Unsired (Members)")));
	}

	@Test
	public void blankInputParsesToEmptySet()
	{
		assertTrue(BlueLogPlugin.parseNormalisedItemNames("   ").isEmpty());
		assertTrue(BlueLogPlugin.parseNormalisedItemNames(null).isEmpty());
	}
}
