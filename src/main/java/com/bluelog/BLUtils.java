package com.bluelog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.runelite.client.util.Text;

public class BLUtils {
  public static String normalizeString(String string) {
    return string.toLowerCase(Locale.ROOT).trim();
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
