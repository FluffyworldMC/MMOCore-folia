package net.Indyuce.mmocore.util;

import io.lumine.mythic.lib.gui.PluginInventory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.MMOCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Schedules work through Paper's region schedulers so the same code runs on
 * Paper and Folia. Folia rejects {@link Bukkit#getScheduler()}.
 */
public final class Tasks {
    private Tasks() {
    }

    public static void cancel(ScheduledTask task) {
        if (task != null && !task.isCancelled()) task.cancel();
    }

    public static ScheduledTask runGlobal(Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(MMOCore.plugin, scheduled -> task.run());
    }

    public static ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        if (delayTicks <= 0) return runGlobal(task);
        return Bukkit.getGlobalRegionScheduler().runDelayed(MMOCore.plugin, scheduled -> task.run(), delayTicks);
    }

    public static ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(MMOCore.plugin, scheduled -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static ScheduledTask runGlobalTimer(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(MMOCore.plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static void executeAt(Location location, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }

        // onDisable runs off the chunk's region. Wait so block cleanup still happens.
        if (!MMOCore.plugin.isEnabled()) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Bukkit.getRegionScheduler().execute(MMOCore.plugin, location, () -> {
                    try {
                        task.run();
                    } finally {
                        latch.countDown();
                    }
                });
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        Bukkit.getRegionScheduler().execute(MMOCore.plugin, location, task);
    }

    public static ScheduledTask runAt(Location location, Runnable task) {
        return Bukkit.getRegionScheduler().run(MMOCore.plugin, location, scheduled -> task.run());
    }

    public static ScheduledTask runAtLater(Location location, Runnable task, long delayTicks) {
        if (delayTicks <= 0) return runAt(location, task);
        return Bukkit.getRegionScheduler().runDelayed(MMOCore.plugin, location, scheduled -> task.run(), delayTicks);
    }

    public static ScheduledTask runAtTimer(Location location, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler().runAtFixedRate(MMOCore.plugin, location, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static void executeEntity(Entity entity, Runnable task) {
        entity.getScheduler().execute(MMOCore.plugin, task, null, 0L);
    }

    public static ScheduledTask runEntity(Entity entity, Runnable task) {
        return entity.getScheduler().run(MMOCore.plugin, scheduled -> task.run(), null);
    }

    public static ScheduledTask runEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (delayTicks <= 0) return runEntity(entity, task);
        return entity.getScheduler().runDelayed(MMOCore.plugin, scheduled -> task.run(), null, delayTicks);
    }

    public static ScheduledTask runEntityTimer(Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(MMOCore.plugin, task, null, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    /**
     * Builds and opens a plugin inventory for the given player on the region
     * thread that owns them. Folia (and Canvas) refuse to initialize a menu
     * from a thread that does not own the player's region, which happens for
     * instance when a command targeting a player is sent from the console or
     * a command block.
     *
     * @param player    Player the inventory is opened for
     * @param inventory Supplier building the inventory, run on the region thread
     */
    public static void openInventory(Player player, Supplier<? extends PluginInventory> inventory) {
        runEntity(player, () -> inventory.get().open());
    }

    /**
     * Repeating global tick that hops onto each online player's region before touching that player.
     */
    public static ScheduledTask runForEachPlayer(long delayTicks, long periodTicks, Consumer<Player> action) {
        return runGlobalTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers())
                player.getScheduler().execute(MMOCore.plugin, () -> action.accept(player), null, 0L);
        }, delayTicks, periodTicks);
    }
}
