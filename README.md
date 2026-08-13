# Blue Log

A RuneLite plugin that colours a Collection Log section **blue** when the only items you are
still missing from it are ones you have listed yourself.

The game already colours section names orange (incomplete) and green (complete). Blue Log adds a
third state in between: *"this section is finished apart from the item(s) I told it about."*

## How it decides

A section is painted blue when **both** are true:

- it is not complete, and
- **every** item you are still missing in it appears in your list.

So if you list `Twisted bow`, then Chambers of Xeric turns blue only when the twisted bow is the
single remaining item. If you are also missing the elder maul, it stays orange. Listing several
items is fine — a section turns blue when all of its remaining items are covered by the list.

Matching is on the exact item name, ignoring case and surrounding whitespace.

## Important limitation, please read

**The client is only sent item data for the collection log page you are currently looking at.**
Nothing in the RuneLite API exposes the full log up front — this is why every collection log
plugin (collectionlog.net, RuneProfile, and the rest) asks you to click through your pages.

Blue Log therefore learns a section the first time you open it, and remembers it per character.
A section you have never opened has no data and cannot be judged, so it keeps its normal colour.

Practical version:

1. Open the collection log and click through the sections you care about, once.
2. From then on they are cached and will be coloured whenever you open the log.
3. After you get a new drop, reopen that section so the cache picks up the change.

Turn on **Mark unscanned sections** in the config if you want the never-opened sections shown in
grey, so it is obvious which ones still need a visit.

The cache is stored against your RuneLite RuneScape profile, so each character has its own.

## Configuration

| Setting | What it does |
| --- | --- |
| **Allowed missing items** | The list of item names, one per line (commas work too). |
| **Ignore all pets** | Adds every pet you are still missing to the list. See below. |
| **Near-complete colour** | Colour for a section that is only missing listed items. Default blue. |
| **Mark unscanned sections** | Colour sections never opened on this character. Default off. |
| **Unscanned colour** | Colour used for those. Default grey. |

Completed sections are never repainted, so they stay green.

Inside the open section, any item slot holding an allowed item gets a small blue dot in its bottom
left corner, in the same colour, so you can see at a glance which items are the reason a section
counts as near complete.

### Preset lists

**Ignore all pets** is a preset: rather than shipping a hardcoded list of pet names, it reads the
items you are missing from your own **All Pets** entry and adds them to the allowed list. That way
it stays correct when new pets are added to the game, and the names always match the game exactly.

The catch is the same one as everywhere else in this plugin: open the All Pets page once, or the
preset has nothing to read and does nothing.

Note that this also turns the All Pets entry itself blue, since by definition everything it is
missing is a pet.

## Building

There is no Gradle wrapper checked in. With Gradle installed:

```bash
gradle build
```

To generate a wrapper so contributors do not need Gradle installed:

```bash
gradle wrapper --gradle-version 8.7
```

The RuneLite client dependency is pulled from `https://repo.runelite.net`, so the first build
needs network access.

`gradle.properties` points the Gradle daemon at a JDK, because `java` on this machine's PATH is a
JRE with no compiler. It holds a machine specific path and is gitignored; delete it if `JAVA_HOME`
already points at a JDK.

## Running it

```bash
gradle run
```

That starts a RuneLite client with the plugin loaded as a built-in, in developer mode. You can
also run `BlueLogPluginTest.main` from your IDE, which does the same thing.

## Submitting to the plugin hub

The layout follows the [example-plugin](https://github.com/runelite/example-plugin) template:
`runelite-plugin.properties` carries `build=standard` and a `version`, `runeLiteVersion` is
`latest.release`, the licence is BSD 2-Clause, and there are no third party dependencies beyond
what `runelite-client` already brings in, so no dependency verification hashes are needed.

Before submitting, confirm the `author` field in `runelite-plugin.properties` is the name you want
published, and add a `support=` line if you want an issues link on the hub listing.

## Implementation notes

- Recolouring hangs off `ScriptPostFired` for `ScriptID.COLLECTION_DRAW_LIST`, which the game runs
  after it builds the section list. Painting in the same call avoids a frame of orange.
- Item slots are not populated until the tick settles, so the page is read back on
  `ClientThread.invokeLater` and the list is repainted if anything changed.
- Obtained vs missing comes from widget opacity: the game fades out items you do not have, so
  `getOpacity() == 0` means obtained.
- Widget ids come from `net.runelite.api.gameval.InterfaceID.Collection`. The interface group id is
  derived from a component id rather than hardcoded.
- Section names are not one list. Each tab has its own text layer — `BOSS_TEXT`, `RAID_TEXT`,
  `CLUE_TEXT`, `MINIGAME_TEXT`, `OTHER_TEXT` — whose dynamic children are the section names, so all
  five are walked. Item slots are the dynamic children of `ITEMS_CONTENTS`, not `ITEMS` — `ITEMS`
  is just the outer container the game resizes.
- `gradle dumpFields` prints the constants of any class on the compile classpath, which is the
  reliable way to check these ids against the RuneLite version you actually resolved.
- `901389` is the green the game uses for a completed section; entries already that colour are
  skipped.
