package com.bluelog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

class PageSnapshot {
	String name;
	List<String> missing = new ArrayList<>();
	int total;
	long updatedAt;

	PageSnapshot() {
		// for Gson
	}

	PageSnapshot(String name, List<String> missing, int total, long updatedAt) {
		this.name = name;
		this.missing = missing;
		this.total = total;
		this.updatedAt = updatedAt;
	}

	boolean isComplete() {
		return missing == null || missing.isEmpty();
	}

	boolean isOnlyMissing(Predicate<String> ignored) {
		if (isComplete()) {
			return false;
		}

		for (String item : missing) {
			if (!ignored.test(BLUtils.normalizeString(item))) {
				return false;
			}
		}

		return true;
	}

	boolean sameContentAs(PageSnapshot other) {
		return other != null
				&& total == other.total
				&& Objects.equals(name, other.name)
				&& Objects.equals(missing, other.missing);
	}
}
