package net.Indyuce.mmocore.loot.chest;

import io.lumine.mythic.lib.util.lang3.Validate;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.LootChestSpawnEvent;
import net.Indyuce.mmocore.api.player.PlayerActivity;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.loot.LootBuilder;
import net.Indyuce.mmocore.loot.RandomWeightedRoll;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class LootChestRegion {
    private final String id;

    private final long chestSpawnPeriod;
    private final RegionBounds bounds;
    private final ChestAlgorithmOptions algOptions;
    private final Set<ChestTier> tiers = new LinkedHashSet<>();
    private final ScheduledTask task;

    private static final Random RANDOM = new Random();

    public LootChestRegion(ConfigurationSection config) {
        Validate.notNull(config, "Could not load config");
        id = config.getName().toLowerCase().replace("_", "-").replace(" ", "-");

        bounds = new RegionBounds(config.getConfigurationSection("bounds"));
        chestSpawnPeriod = config.getLong("spawn-period", 5 * 60);
        algOptions = config.contains("algorithm-options") ? new ChestAlgorithmOptions(config.getConfigurationSection("algorithm-options"))
                : ChestAlgorithmOptions.DEFAULT;

        Validate.isTrue(config.isConfigurationSection("tiers"), "Could not find chest tiers");
        for (String key : config.getConfigurationSection("tiers").getKeys(false))
            try {
                tiers.add(new ChestTier(config.getConfigurationSection("tiers." + key)));
            } catch (IllegalArgumentException exception) {
                MMOCore.plugin.getLogger().log(Level.WARNING,
                        "Could not load tier '" + key + "' from chest region '" + id + "': " + exception.getMessage());
            }

        Validate.isTrue(!tiers.isEmpty(), "Your region must have at least one chest tier");

        task = Tasks.runGlobalTimer(this::tick, chestSpawnPeriod * 20, chestSpawnPeriod * 20);
    }

    private void tick() {
        AtomicBoolean claimed = new AtomicBoolean();
        for (Player player : bounds.getWorld().getPlayers()) {
            player.getScheduler().execute(MMOCore.plugin, () -> {
                if (claimed.get() || !bounds.isInRegion(player)) return;
                PlayerData data = PlayerData.get(player);
                if (data.getActivityTimeOut(PlayerActivity.LOOT_CHEST_SPAWN) != 0) return;
                if (!claimed.compareAndSet(false, true)) return;
                spawnChest(data);
            }, null, 0L);
        }
    }

    public String getId() {
        return id;
    }

    public Set<ChestTier> getTiers() {
        return tiers;
    }

    public RegionBounds getBounds() {
        return bounds;
    }

    public long getChestSpawnPeriod() {
        return chestSpawnPeriod;
    }

    public ScheduledTask getRunnable() {
        return task;
    }

    public void spawnChest(PlayerData player) {

        // Apply chest cooldown
        player.setLastActivity(PlayerActivity.LOOT_CHEST_SPAWN);

        // First randomly determine the chest tier
        ChestTier tier = rollTier(player);
        LootBuilder builder = new LootBuilder(player, tier.rollCapacity(player));
        tier.getDropTable().collect(builder);

        // Search columns on the player's region, then place the chest on the column's region.
        trySpawn(player, tier, builder, player.getPlayer().getLocation(), 0);
    }

    private void trySpawn(PlayerData player, ChestTier tier, LootBuilder builder, Location center, int attempt) {
        if (!player.isOnline() || attempt >= algOptions.iterations) return;

        double a = RANDOM.nextDouble() * 2 * Math.PI;
        Vector dir = new Vector(Math.cos(a), 0, Math.sin(a))
                .multiply(algOptions.minRange + RANDOM.nextDouble() * (algOptions.maxRange - algOptions.minRange));
        center.add(dir);
        Location column = center.clone();

        Tasks.executeAt(column, () -> {
            Location found = findSuitable(column);
            if (found == null) {
                if (player.isOnline())
                    Tasks.executeEntity(player.getPlayer(), () -> trySpawn(player, tier, builder, center, attempt + 1));
                return;
            }
            placeChest(player, tier, builder, found);
        });
    }

    private void placeChest(PlayerData player, ChestTier tier, LootBuilder builder, Location location) {
        LootChest lootChest = new LootChest(tier, this, location.getBlock());
        LootChestSpawnEvent event = new LootChestSpawnEvent(player, lootChest, builder);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        List<Integer> slots = new ArrayList<>();
        for (int j = 0; j < 27; j++)
            slots.add(j);

        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        builder.getLoot().forEach(item -> {
            Integer slot = slots.get(RANDOM.nextInt(slots.size()));
            chest.getInventory().setItem(slot, item);
            slots.remove(slot);
        });

        MMOCore.plugin.lootChests.register(lootChest);
    }

    /**
     * @param player Player rolling the tier
     * @return A randomly picked tiers taking into account tier spawn rates
     *         and the player Chance attribute
     */
    @NotNull
    public ChestTier rollTier(PlayerData player) {
        return new RandomWeightedRoll<>(player, tiers, MMOCore.plugin.configManager.lootChestsChanceWeight).rollItem();
    }

    private Location findSuitable(Location random) {
        for (int h = 0; h <= algOptions.height * 2; h++) {
            int z = h % 2 == 0 ? h / 2 : -(h + 1) / 2; // bijective from N to Z
            Location checked = random.clone().add(0, z, 0);
            if (isSuitable(checked))
                return checked;
        }
        return null;
    }

    private boolean isSuitable(Location loc) {
        return !loc.getBlock().getType().isSolid() && loc.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }
}
