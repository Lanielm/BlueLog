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

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
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

	@Test
	public void completePageIsNeverHighlighted()
	{
		assertTrue(page().isComplete());
		assertFalse(page().isOnlyMissing(BlueLogPlugin.parseItemList("Twisted bow")));
	}

	@Test
	public void singleAllowedMissingItemIsHighlighted()
	{
		assertTrue(page("Twisted bow").isOnlyMissing(BlueLogPlugin.parseItemList("Twisted bow")));
	}

	@Test
	public void matchingIgnoresCaseAndSurroundingSpace()
	{
		assertTrue(page("Twisted bow").isOnlyMissing(BlueLogPlugin.parseItemList("  TWISTED BOW  ")));
	}

	@Test
	public void unlistedMissingItemBlocksHighlight()
	{
		assertFalse(page("Twisted bow", "Elder maul").isOnlyMissing(BlueLogPlugin.parseItemList("Twisted bow")));
	}

	@Test
	public void everyMissingItemListedIsHighlighted()
	{
		Set<String> allowed = BlueLogPlugin.parseItemList("Twisted bow\nElder maul");
		assertTrue(page("Twisted bow", "Elder maul").isOnlyMissing(allowed));
	}

	@Test
	public void emptyListHighlightsNothing()
	{
		assertFalse(page("Twisted bow").isOnlyMissing(Collections.emptySet()));
	}

	@Test
	public void parsesNewlinesAndCommas()
	{
		assertEquals(
			Arrays.asList("twisted bow", "elder maul", "kodai insignia"),
			new java.util.ArrayList<>(BlueLogPlugin.parseItemList("Twisted bow\nElder maul, Kodai insignia")));
	}

	@Test
	public void blankInputParsesToEmptySet()
	{
		assertTrue(BlueLogPlugin.parseItemList("   ").isEmpty());
		assertTrue(BlueLogPlugin.parseItemList(null).isEmpty());
	}
}
