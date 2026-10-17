package net.Indyuce.mmocore.listener.profession;

import io.lumine.mythic.lib.version.Sounds;
import io.lumine.mythic.lib.version.VParticle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.CustomPlayerFishEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.loot.LootBuilder;
import net.Indyuce.mmocore.loot.fishing.FishingDropItem;
import net.Indyuce.mmocore.manager.profession.FishingManager.FishingDropTable;
import net.Indyuce.mmocore.util.Language;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.*;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class FishingListener implements Listener {
    private final Set<UUID> fishing = new HashSet<>();

    private static final Random RANDOM = new Random();

    @EventHandler(priority = EventPriority.LOW)
    public void a(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();

        if (event.getState() == State.BITE && !fishing.contains(player.getUniqueId()) && !player.hasMetadata("NPC")) {

            /*
             * Checks for drop tables. If no drop table, just plain vanilla
             * fishing OTHERWISE initialize fishing, register other listener.
             */
            FishingDropTable table = MMOCore.plugin.fishingManager.calculateDropTable(player, hook);
            if (table == null) return;

            new FishingData(player, hook, table);
            MMOCoreUtils.displayIndicator(hook.getLocation().add(0, 1.25, 0), Language.CAUGHT_FISH.getFormat());
        }
    }

    public class FishingData implements Listener {
        private final Location location;
        private final FishingDropItem caught;
        private final PlayerData playerData;
        private final Player player;
        private final FishHook hook;

        private final int fishStrength, experienceDropped, vanillaExpDropped;

        private int currentPulls;
        private ScheduledTask task;

        /**
         * Used to track the last time the player swung the fishing rod.
         * If the player does not swing the rod at least once every second,
         * the fish will go away and drops will be lost.
         */
        private long last = System.currentTimeMillis();

        private static final long TIME_OUT = 1000;

        public FishingData(Player player, FishHook hook, FishingDropTable table) {
            this.location = hook.getLocation();
            this.caught = table.getRandomItem(PlayerData.get(player));
            this.playerData = PlayerData.get(this.player = player);
            this.hook = hook;

            this.fishStrength = (int) Math.floor(caught.rollTugs() * (1 - PlayerData.get(player).getStats().getStat("FISHING_STRENGTH") / 100));
            this.experienceDropped = caught.rollExperience();
            this.vanillaExpDropped = caught.rollVanillaExp();
            fishing.add(player.getUniqueId());
            task = Tasks.runAtTimer(location, this::tick, 1, 2);
            Bukkit.getPluginManager().registerEvents(this, MMOCore.plugin);

            // Check for instant loot
            if (fishStrength == 0)
                lootFish();
        }

        public void setCriticalFish() {
            currentPulls = fishStrength + 2;
        }

        public boolean isTimedOut() {
            return last + TIME_OUT < System.currentTimeMillis();
        }

        /**
         * @return If the fish is weak enough to be looted by the player.
         */
        public boolean pullOnce() {
            last = System.currentTimeMillis();
            currentPulls++;
            return currentPulls >= fishStrength;
        }

        /**
         * Critical fish's means you catch the fish on the very first try
         */
        public boolean isCriticalFish() {
            return currentPulls > fishStrength + 1;
        }

        private boolean closed;

        private void stopTracking() {
            if (closed) return;
            closed = true;
            fishing.remove(player.getUniqueId());
            Tasks.cancel(task);
            HandlerList.unregisterAll(this);
        }

        private void close() {
            stopTracking();
            hook.getScheduler().execute(MMOCore.plugin, () -> {
                if (hook.isValid()) hook.remove();
            }, null, 0L);
        }

        private void tick(ScheduledTask scheduled) {
            if (closed) {
                scheduled.cancel();
                return;
            }
            if (isTimedOut()) {
                close();
                return;
            }

            location.getWorld().spawnParticle(Particle.CRIT, location, 0, 2 * (RANDOM.nextDouble() - .5), 3, 2 * (RANDOM.nextDouble() - .5), .6);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void a(PlayerFishEvent event) {
            if (event.getPlayer().equals(player) && (event.getState() == State.CAUGHT_FISH || event.getState() == State.FAILED_ATTEMPT || event.getState() == State.REEL_IN)) {

                // Lose the catch if the current fish is gone!
                event.setCancelled(true);
                if (isTimedOut()) {
                    hook.getScheduler().execute(MMOCore.plugin, () -> {
                        hook.getWorld().spawnParticle(VParticle.SMOKE.get(), hook.getLocation(), 16, 0, 0, 0, .1);
                        close();
                    }, null, 0L);
                    return;
                }

                if (currentPulls == 0 && RANDOM.nextDouble() < PlayerData.get(player).getStats().getStat("CRITICAL_FISHING_CHANCE") / 100)
                    setCriticalFish();

                // Check if enough pulls; if not, wait till the next fish event
                if (pullOnce())
                    lootFish();
            }
        }

        public void lootFish() {
            if (closed) return;
            stopTracking();

            ItemStack mainhand = player.getInventory().getItem(EquipmentSlot.HAND);
            MMOCoreUtils.decreaseDurability(player,
                    (mainhand != null && mainhand.getType() == Material.FISHING_ROD) ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND, 1);

            final Location playerLoc = player.getLocation().clone();
            final boolean failed = !isCriticalFish() && RANDOM.nextDouble() < PlayerData.get(player).getStats().getStat("CRITICAL_FISHING_FAILURE_CHANCE") / 100;
            if (failed) {
                hook.getScheduler().execute(MMOCore.plugin, () -> {
                    Vector vec = hook.getLocation().clone().subtract(playerLoc).toVector().setY(0).multiply(3).setY(.5);
                    hook.getWorld().spawnParticle(VParticle.SMOKE.get(), location, 24, 0, 0, 0, .08);
                    Tasks.executeEntity(player, () -> player.setVelocity(vec));
                    if (hook.isValid()) hook.remove();
                }, null, 0L);
                return;
            }

            ItemStack collect = caught.collect(new LootBuilder(playerData));
            if (collect == null) {
                hook.getScheduler().execute(MMOCore.plugin, () -> {
                    hook.getWorld().spawnParticle(VParticle.SMOKE.get(), location, 24, 0, 0, 0, .08);
                    if (hook.isValid()) hook.remove();
                }, null, 0L);
                return;
            }

            final boolean critical = isCriticalFish();
            hook.getScheduler().execute(MMOCore.plugin, () -> {
                Location hookLoc = hook.getLocation().clone();
                Item item = hook.getWorld().dropItemNaturally(hookLoc, collect);

                var called = new CustomPlayerFishEvent(playerData, item);
                Bukkit.getPluginManager().callEvent(called);
                if (called.isCancelled()) {
                    item.remove();
                    if (hook.isValid()) hook.remove();
                    return;
                }

                final var languageEntry = critical ? Language.FISH_OUT_WATER_CRIT : Language.FISH_OUT_WATER;
                MMOCoreUtils.displayIndicator(location.clone().add(0, 1.25, 0), languageEntry.getFormat());
                Vector vec = playerLoc.clone().subtract(hookLoc).toVector();
                vec.setY(vec.getY() * .031 + vec.length() * .05);
                vec.setX(vec.getX() * .08);
                vec.setZ(vec.getZ() * .08);
                item.setVelocity(vec);
                for (int j = 0; j < 8; j++)
                    location.getWorld().spawnParticle(VParticle.FIREWORK.get(), location, 0, 4 * (RANDOM.nextDouble() - .5), RANDOM.nextDouble() + 1, 4 * (RANDOM.nextDouble() - .5), .08);
                if (hook.isValid()) hook.remove();

                Tasks.executeEntity(player, () -> {
                    player.incrementStatistic(Statistic.FISH_CAUGHT);
                    player.getWorld().playSound(player.getLocation(), Sounds.BLOCK_NOTE_BLOCK_HAT, 1, 0);
                    if (vanillaExpDropped > 0) player.giveExp(vanillaExpDropped);
                    if (experienceDropped > 0 && MMOCore.plugin.fishingManager.hasLinkedProfession())
                        playerData.getCollectionSkills().giveExperience(MMOCore.plugin.fishingManager.getLinkedProfession(), experienceDropped, EXPSource.FISHING, hookLoc, true);
                });
            }, null, 0L);
        }
    }
}
