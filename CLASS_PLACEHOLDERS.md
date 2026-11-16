# Class placeholders

Requires PlaceholderAPI. Replace `WARRIOR` with the actual class ID, for example
`MAGE` or `DARK_MAGE`. Class IDs in these placeholders are case-insensitive.
Existing placeholders such as `%mmocore_level%`, `%mmocore_class_id%` and
`%mmocore_class_points%` still work as before.

## The viewing player's class data

These return that class's own data, even when the player is currently using a
different class. A class the player has not played, a missing player context or
an unloaded/offline player returns `0`. They do not load offline player data.

| Placeholder | Value |
| --- | --- |
| `%mmocore_class_level_WARRIOR%` | Level |
| `%mmocore_class_exp_WARRIOR%` | Current EXP (`experience` is an alias for `exp`) |
| `%mmocore_class_attribute_points_WARRIOR%` | Unspent attribute points |
| `%mmocore_class_skill_points_WARRIOR%` | Unspent skill points |
| `%mmocore_class_attribute_reallocation_points_WARRIOR%` | Attribute reallocation points (`attribute_realloc_points` is an alias) |
| `%mmocore_class_skill_reallocation_points_WARRIOR%` | Skill reallocation points |
| `%mmocore_class_skill_tree_reallocation_points_WARRIOR%` | Skill tree reallocation points |
| `%mmocore_class_attribute_WARRIOR:strength%` | Points assigned to the specified attribute |
| `%mmocore_class_skill_WARRIOR:WARRIOR_FURIOUSSLASH%` | Specified skill's level; use the registered skill ID |
| `%mmocore_class_health_WARRIOR%` | Health |
| `%mmocore_class_mana_WARRIOR%` | Mana |
| `%mmocore_class_stamina_WARRIOR%` | Stamina |
| `%mmocore_class_stellium_WARRIOR%` | Stellium |

Online values are captured on each player's region thread about once per second.
An inactive class's resource values are those stored when leaving that class.

## Top level per class

| Placeholder | Value |
| --- | --- |
| `%mmocore_class_top_WARRIOR_1_name%` | Name of the number 1 Warrior |
| `%mmocore_class_top_WARRIOR_1_level%` | Their Warrior level |
| `%mmocore_class_top_WARRIOR_1_exp%` | Their Warrior EXP (`experience` is an alias) |
| `%mmocore_class_top_WARRIOR_1_uuid%` | Their effective player/profile UUID |
| `%mmocore_class_top_MAGE_2_name%` | Name of the number 2 Mage |
| `%mmocore_class_top_MAGE_2_level%` | Their Mage level |

Replace `1` with any position up to `class-placeholders.top-size` (default 100).
Players are ordered by **level descending, EXP descending, then UUID ascending**.
The same player can appear in multiple class rankings. With profile support,
each effective profile UUID is a separate entry. Empty ranks return `-` for
name/UUID and `0` for numbers. Top placeholders work without a viewing player.

For a hologram line, for example:

```text
Warrior #1: %mmocore_class_top_WARRIOR_1_name% - Lv. %mmocore_class_top_WARRIOR_1_level%
Mage #1: %mmocore_class_top_MAGE_1_name% - Lv. %mmocore_class_top_MAGE_1_level%
```

## Cache and database cost

Placeholder requests only read memory, including when HUDs/holograms request them
frequently. They never query MySQL or trigger player-data loading.

```yaml
class-placeholders:
  top-size: 100
  top-refresh-interval: 60 # Seconds, minimum 5.
```

MySQL refreshes saved leaderboard candidates asynchronously once per interval:
**one bounded SELECT per configured class per refresh**, with a class/level/EXP
index. Five classes at the default interval add about five SELECTs per minute per
server, independent of placeholder render frequency. Local online snapshots are
merged with saved results about once per second, so local leveling and class
switches appear without waiting for autosave. Offline players and players on
other servers use their last saved database values, refreshed every 60 seconds.
The existing `mysql.live-sync-interval` setting is separate and unchanged.

Names are saved in the class table on player load/save, including logout saves.
Older rows without a name use the server's cached offline-player name where
available. If no name is known (including an unmapped old profile), the UUID is
shown until the player joins and the plugin records their name.

In YAML mode, personal placeholders and local online rankings work; the global
offline leaderboard requires MySQL. Settings take effect on restart. Install
this JAR on all servers sharing the class database; the `player_name` column and
ranking index are added automatically.

Validation: `mvn package` runs parser, ranking and JDBC regression tests. A live
Paper/Folia server with PlaceholderAPI is still needed for in-game verification.
