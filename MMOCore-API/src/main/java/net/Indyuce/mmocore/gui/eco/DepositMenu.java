package net.Indyuce.mmocore.gui.eco;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import io.lumine.mythic.lib.gui.PluginInventory;
import io.lumine.mythic.lib.util.SmartGive;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.Tasks;
import net.Indyuce.mmocore.util.item.SimpleItemBuilder;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class DepositMenu extends PluginInventory {
    private ItemStack depositItem;
    private int deposit;

    private Inventory lastBukkitInventory;

    /**
     * Every time an item is clicked in the inventory, an inventory
     * update is scheduled. If nothing happens for the next 10 ticks
     * then the update is processed. If another item is clicked within
     * this delay the task is cancelled and scheduled for later
     */
    private ScheduledTask updateTask;

    public DepositMenu(Player player) {
        super(player);
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, "Deposit");
        updateDeposit(inv);
        return lastBukkitInventory = inv;
    }

    @Override
    public void onClick(InventoryClickEvent event) {

        if (event.getCurrentItem() != null && event.getCurrentItem().isSimilar(depositItem)) {
            event.setCancelled(true);

            updateDeposit(event.getInventory());
            if (deposit <= 0) return;

            EconomyResponse response = MMOCore.plugin.economy.getEconomy().depositPlayer(player, deposit);
            if (!response.transactionSuccess()) return;

            event.getInventory().clear();
            player.closeInventory();
            Message.DEPOSIT_SUCCESS.send(player, "worth", String.valueOf(deposit));
            return;
        }

        // Can only move around currency items
        var worth = NBTItem.get(event.getCurrentItem()).getInteger("RpgWorth");
        if (worth < 1) event.setCancelled(true);
        else scheduleUpdate(event.getInventory());
    }

    @Override
    public void onClose() {

        Tasks.cancel(updateTask);

        // Give all items back
        var smart = new SmartGive(player);
        for (int j = 0; j < 26; j++) {
            ItemStack item = lastBukkitInventory.getItem(j);
            if (item != null)
                smart.give(item);
        }
    }

    private void scheduleUpdate(Inventory inv) {
        Tasks.cancel(updateTask);

        updateTask = Tasks.runEntityLater(player, () -> {
            updateTask = null;
            updateDeposit(inv);
        }, 10);
    }

    private void updateDeposit(Inventory inv) {
        if (updateTask != null) {
            Tasks.cancel(updateTask);
            updateTask = null;
        }

        deposit = MMOCoreUtils.getWorth(inv.getContents());
        inv.setItem(26, depositItem = new SimpleItemBuilder("DEPOSIT_ITEM").addPlaceholders("worth", String.valueOf(deposit)).build());
    }
}
