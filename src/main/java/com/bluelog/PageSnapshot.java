package com.bluelog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * What we learned about a single collection log section the last time it was open.
 * <p>
 * The client is only sent item data for the page currently being viewed, so this is
 * the plugin's memory of pages the player has already looked at.
 */
class PageSnapshot
{
	/** Section name as displayed, e.g. "Chambers of Xeric". */
	String name;

	/** Display names of the items in this section the player has not obtained. */
	List<String> missing = new ArrayList<>();

	/** Total number of item slots in the section, used only for diagnostics. */
	int total;

	/** Epoch millis of the last time this page was read. */
	long updatedAt;

	PageSnapshot()
	{
		// for Gson
	}

	PageSnapshot(String name, List<String> missing, int total, long updatedAt)
	{
		this.name = name;
		this.missing = missing;
		this.total = total;
		this.updatedAt = updatedAt;
	}

	boolean isComplete()
	{
		return missing == null || missing.isEmpty();
	}

	/**
	 * True when the player is only missing ignored items, and is genuinely still missing at least
	 * one of them. The predicate is given lowercase item names.
	 */
	boolean isOnlyMissing(Predicate<String> ignored)
	{
		if (isComplete())
		{
			return false;
		}

		for (String item : missing)
		{
			// Normalised, so a page cached before the members suffix was stripped still matches.
			if (!ignored.test(BlueLogPlugin.normalisedName(item)))
			{
				return false;
			}
		}

		return true;
	}

	boolean sameContentAs(PageSnapshot other)
	{
		return other != null
			&& total == other.total
			&& Objects.equals(name, other.name)
			&& Objects.equals(missing, other.missing);
	}
}
