package net.Indyuce.mmocore.comp.placeholder;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.manager.data.sql.SQLDatabaseImpl;
import net.Indyuce.mmocore.player.ClassDataContainer;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Placeholder rendering only reads snapshots: no JDBC, player creation or region hopping. */
public final class ClassPlaceholders {
    private final Map<UUID, Snapshot> players = new ConcurrentHashMap<>();
    private final Map<UUID, String> legacyNames = new ConcurrentHashMap<>();
    private volatile Map<String, List<ClassRanking.Entry>> saved = Collections.emptyMap();
    private volatile Map<String, List<ClassRanking.Entry>> rankings = Collections.emptyMap();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private ScheduledTask captureTask, rankingTask, databaseTask;
    private volatile boolean closed;
    private int size;

    public void start() {
        size = Math.max(1, Math.min(1000, MMOCore.plugin.getConfig().getInt("class-placeholders.top-size", 100)));
        List<String> classIds = new ArrayList<>();
        MMOCore.plugin.classManager.getAll().forEach(profess -> classIds.add(profess.getId()));
        captureTask = Tasks.runForEachPlayer(1, 20, player -> {
            PlayerData data = MMOCore.plugin.playerDataManager.getOrNull(player);
            if (data == null || !data.isSessionReady()) { players.remove(player.getUniqueId()); return; }
            Map<String, Values> classes = new HashMap<>();
            for (String id : new HashSet<>(data.getSavedClasses())) {
                ClassDataContainer info = data.getClassInfo(id);
                if (info != null) classes.put(normalize(id), new Values(info));
            }
            Values active = new Values(data);
            active.metrics.put("health", number(player.getHealth()));
            classes.put(normalize(data.getProfess().getId()), active);
            players.put(player.getUniqueId(), new Snapshot(data.getEffectiveId(), player.getName(), classes));
        });
        rankingTask = Tasks.runGlobalTimer(() -> {
            Set<UUID> online = new HashSet<>();
            Bukkit.getOnlinePlayers().forEach(player -> online.add(player.getUniqueId()));
            players.keySet().removeIf(uuid -> !online.contains(uuid));
            Map<String, List<ClassRanking.Entry>> live = new HashMap<>();
            players.values().forEach(player -> player.classes.forEach((id, values) ->
                    live.computeIfAbsent(id, ignored -> new ArrayList<>()).add(
                            new ClassRanking.Entry(player.effectiveId, player.name, values.level, values.experience))));
            Map<String, List<ClassRanking.Entry>> next = new HashMap<>();
            Set<String> ids = new HashSet<>(saved.keySet());
            ids.addAll(live.keySet());
            for (String id : ids) next.put(id, ClassRanking.merge(saved.getOrDefault(id, Collections.emptyList()),
                    live.getOrDefault(id, Collections.emptyList()), size));
            rankings = Collections.unmodifiableMap(next);
        }, 2, 20);
        long interval = Math.max(5, MMOCore.plugin.getConfig().getLong("class-placeholders.top-refresh-interval", 60));
        databaseTask = Tasks.runGlobalTimer(() -> refresh(classIds), 1, interval * 20L);
    }

    private void refresh(List<String> classIds) {
        if (closed || !(MMOCore.plugin.playerDataManager.getDatabase() instanceof SQLDatabaseImpl)
                || !refreshing.compareAndSet(false, true)) return;
        Bukkit.getAsyncScheduler().runNow(MMOCore.plugin, task -> {
            try {
                SQLDatabaseImpl database = (SQLDatabaseImpl) MMOCore.plugin.playerDataManager.getDatabase();
                Map<String, List<ClassRanking.Entry>> next = new HashMap<>();
                // Extra candidates retain a full top list when online players' levels decrease.
                int limit = size + players.size();
                for (String id : classIds) {
                    List<ClassRanking.Entry> entries = new ArrayList<>();
                    for (ClassRanking.Entry entry : database.getClassLeaderboard(id, limit)) {
                        String name = entry.name;
                        if (name.equals(entry.uuid.toString())) name = legacyNames.computeIfAbsent(entry.uuid, uuid -> {
                            String cachedName = Bukkit.getOfflinePlayer(uuid).getName();
                            return cachedName == null ? uuid.toString() : cachedName;
                        });
                        entries.add(new ClassRanking.Entry(entry.uuid, name, entry.level, entry.experience));
                    }
                    next.put(normalize(id), Collections.unmodifiableList(entries));
                }
                if (!closed) saved = Collections.unmodifiableMap(next);
            } catch (Exception exception) {
                MMOCore.plugin.getLogger().log(Level.WARNING, "Could not refresh class leaderboard cache", exception);
            } finally { refreshing.set(false); }
        });
    }

    public String request(OfflinePlayer player, String raw) {
        ClassPlaceholderRequest request = ClassPlaceholderRequest.parse(raw);
        if (request == null) return null;
        if (request.rank > 0) {
            List<ClassRanking.Entry> entries = rankings.getOrDefault(request.classId, Collections.emptyList());
            if (request.rank > entries.size()) return request.metric.equals("name") || request.metric.equals("uuid") ? "-" : "0";
            ClassRanking.Entry entry = entries.get(request.rank - 1);
            switch (request.metric) {
                case "name": return entry.name;
                case "uuid": return entry.uuid.toString();
                case "level": return Integer.toString(entry.level);
                default: return number(entry.experience);
            }
        }
        Snapshot snapshot = player == null ? null : players.get(player.getUniqueId());
        Values values = snapshot == null ? null : snapshot.classes.get(request.classId);
        if (values == null) return "0";
        if (request.metric.equals("attribute")) return Integer.toString(values.attributes.getOrDefault(
                request.key.toLowerCase(Locale.ROOT).replace('_', '-'), 0));
        if (request.metric.equals("skill")) return Integer.toString(values.skills.getOrDefault(request.key.toUpperCase(Locale.ROOT), 0));
        return values.metrics.getOrDefault(request.metric, "0");
    }

    public void close() {
        closed = true;
        Tasks.cancel(captureTask);
        Tasks.cancel(rankingTask);
        Tasks.cancel(databaseTask);
        players.clear();
        legacyNames.clear();
        saved = Collections.emptyMap();
        rankings = Collections.emptyMap();
    }

    private static String normalize(String id) { return id.toUpperCase(Locale.ROOT); }
    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }

    private static final class Snapshot {
        final UUID effectiveId;
        final String name;
        final Map<String, Values> classes;
        Snapshot(UUID effectiveId, String name, Map<String, Values> classes) {
            this.effectiveId = effectiveId;
            this.name = name;
            this.classes = Collections.unmodifiableMap(classes);
        }
    }

    private static final class Values {
        final int level;
        final double experience;
        final Map<String, String> metrics = new HashMap<>();
        final Map<String, Integer> attributes, skills;
        Values(ClassDataContainer data) {
            level = data.getLevel();
            experience = data.getExperience();
            attributes = new HashMap<>(data.mapAttributeLevels());
            skills = new HashMap<>(data.mapSkillLevels());
            metrics.put("level", Integer.toString(level));
            metrics.put("experience", number(experience));
            metrics.put("exp", number(experience));
            metrics.put("attribute_points", Integer.toString(data.getAttributePoints()));
            metrics.put("skill_points", Integer.toString(data.getSkillPoints()));
            metrics.put("attribute_reallocation_points", Integer.toString(data.getAttributeReallocationPoints()));
            metrics.put("attribute_realloc_points", Integer.toString(data.getAttributeReallocationPoints()));
            metrics.put("skill_reallocation_points", Integer.toString(data.getSkillReallocationPoints()));
            metrics.put("skill_tree_reallocation_points", Integer.toString(data.getSkillTreeReallocationPoints()));
            metrics.put("health", number(data.getLastHealth()));
            metrics.put("mana", number(data.getMana()));
            metrics.put("stamina", number(data.getStamina()));
            metrics.put("stellium", number(data.getStellium()));
        }
    }
}
