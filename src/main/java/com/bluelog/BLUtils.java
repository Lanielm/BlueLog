package com.bluelog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.runelite.client.util.Text;

public class BLUtils {
  private static final String MEMBERS_SUFFIX = "(members)";

  public static String normalizeString(String string) {
    return string.toLowerCase(Locale.ROOT).trim();
  }

  // Free worlds append this to members only item names.
  public static String stripMembersSuffix(String itemName) {
    String trimmed = itemName.trim();
    if (!normalizeString(trimmed).endsWith(MEMBERS_SUFFIX)) {
      return trimmed;
    }

    return trimmed.substring(0, trimmed.length() - MEMBERS_SUFFIX.length()).trim();
  }

  public static List<String> textToItemNames(String configuredText) {
    if (configuredText == null || configuredText.trim().isEmpty()) {
      return Collections.emptyList();
    }

    List<String> items = new ArrayList<>();
    for (String part : Text.fromCSV(configuredText)) {
      String cleaned = Text.removeTags(part).trim();
      if (!cleaned.isEmpty()) {
        items.add(cleaned);
      }
    }

    return items;
  }

  public static Set<String> textToItemNamesSet(String configuredText) {
    Set<String> normalisedItems = new LinkedHashSet<>();
    for (String item : textToItemNames(configuredText)) {
      normalisedItems.add(normalizeString(item));
    }

    return normalisedItems;
  }
}
