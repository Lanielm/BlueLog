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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
	 * True when the player is only missing items the user has explicitly allowed, and is
	 * genuinely still missing at least one of them.
	 */
	boolean isOnlyMissing(Set<String> allowedLowercase)
	{
		if (isComplete() || allowedLowercase.isEmpty())
		{
			return false;
		}

		for (String item : missing)
		{
			if (!allowedLowercase.contains(item.toLowerCase(Locale.ROOT)))
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
