package net.Indyuce.mmocore.comp.placeholder;

import java.util.*;
import java.util.stream.Collectors;

/** Immutable leaderboard entries; ties use EXP, then UUID for stable positions. */
public final class ClassRanking {
    private ClassRanking() { }

    public static final class Entry {
        public final UUID uuid;
        public final String name;
        public final int level;
        public final double experience;

        public Entry(UUID uuid, String name, int level, double experience) {
            this.uuid = uuid;
            this.name = name == null || name.isEmpty() ? uuid.toString() : name;
            this.level = level;
            this.experience = experience;
        }
    }

    public static List<Entry> merge(List<Entry> saved, Collection<Entry> live, int limit) {
        Map<UUID, Entry> entries = new HashMap<>();
        saved.forEach(entry -> entries.put(entry.uuid, entry));
        live.forEach(entry -> entries.put(entry.uuid, entry));
        return entries.values().stream().sorted(Comparator.comparingInt((Entry entry) -> entry.level).reversed()
                .thenComparing(Comparator.comparingDouble((Entry entry) -> entry.experience).reversed())
                .thenComparing(entry -> entry.uuid.toString())).limit(limit).collect(Collectors.toUnmodifiableList());
    }
}
