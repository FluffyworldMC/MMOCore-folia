package net.Indyuce.mmocore.loot.chest;

import io.lumine.mythic.lib.util.lang3.Validate;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.HashableLocation;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class LootChest {
    private final ChestTier tier;
    private final LootChestRegion region;
    private final ReplacedBlock block;
    @Nullable
    private final ScheduledTask effectTask;
    private final ScheduledTask closeTask;

    private boolean active = true;

    /**
     * Called when a loot chest is placed as a Bukkit block, and used
     * to save the data of the block which has been replaced.
     * <p>
     * A placed drop chest may only replace non solid blocks like grass
     * or levels..
     */
    public LootChest(ChestTier tier, LootChestRegion region, Block block) {
        this.tier = tier;
        this.region = region;
        this.block = new ReplacedBlock(block);
        this.effectTask = tier.hasEffect() ? tier.getEffect().startNewRunnable(block.getLocation().add(.5, .5, .5)) : null;
        closeTask = Tasks.runAtLater(block.getLocation(), () -> expire(null), MMOCore.plugin.configManager.lootChestExpireTime);
    }

    public ChestTier getTier() {
        return tier;
    }

    public ReplacedBlock getBlock() {
        return block;
    }

    public LootChestRegion getRegion() {
        return region;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * This does NOT remove the loot chest from the plugin registry.
     * When naturally expiring, content should be lost
     *
     * @param player If a player triggered the unregistration of that chest by
     *               opening and then closing it for the first time. Null when
     *               a loot chests naturally expires or when MMOCore disables.
     */
    public void expire(@Nullable Player player) {

        synchronized (this) {
            Validate.isTrue(active, "Chest has already expired");
            active = false;
        }

        Tasks.cancel(closeTask);
        Tasks.executeAt(block.loc.bukkit(), () -> finishExpire(player));
    }

    private void finishExpire(@Nullable Player player) {
        if (player != null) {
            Tasks.executeEntity(player, () -> Message.CLOSE_LOOT_CHEST.send(player));
            block.loc.getWorld().spawnParticle(Particle.CRIT, block.loc.bukkit().add(.5, .5, .5), 16, 0, 0, 0, .5);

            final Inventory chestInv = ((Chest) block.loc.bukkit().getBlock().getState()).getBlockInventory();
            final Location centerLoc = block.findCenterLocation();
            for (ItemStack drop : chestInv.getContents())
                if (drop != null && drop.getType() != Material.AIR)
                    block.getLocation().getWorld().dropItem(centerLoc, drop);

            chestInv.clear();
        }

        /*
         * Must clean block inventory before replacing block otherwise loots fly
         * off and accumulate on the ground (+during dev phase)
         */

        block.restore();
        Tasks.cancel(effectTask);
    }

    public static class ReplacedBlock {
        private final Material material;
        private final BlockData data;
        private final HashableLocation loc;

        public ReplacedBlock(Block block) {
            this.material = block.getType();
            this.data = block.getBlockData();
            this.loc = new HashableLocation(block.getLocation());
        }

        public HashableLocation getLocation() {
            return loc;
        }

        public Location findCenterLocation() {
            return new Location(getLocation().getWorld(), loc.getX() + .5, loc.getY() + .5, loc.getZ() + .5);
        }

        @Deprecated
        public boolean matches(Location loc) {
            return this.loc.getWorld().equals(loc.getWorld()) && this.loc.getX() == loc.getBlockX() && this.loc.getY() == loc.getBlockY()
                    && this.loc.getZ() == loc.getBlockZ();
        }

        public void restore() {
            Block block = loc.bukkit().getBlock();
            block.setType(material);
            block.setBlockData(data);
        }
    }
}
