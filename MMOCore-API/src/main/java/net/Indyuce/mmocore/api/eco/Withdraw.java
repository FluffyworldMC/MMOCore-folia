package net.Indyuce.mmocore.api.eco;

import io.lumine.mythic.lib.util.SmartGive;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.Tasks;
import net.Indyuce.mmocore.util.item.CurrencyItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Withdraw implements Listener {
    private static final Set<UUID> WITHDRAWING_PLAYERS = new HashSet<>();

    private final Player player;

    public Withdraw(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public void open() {
        if (isWithdrawing())
            return;

        Message.WITHDRAW_START.send(player);
        Bukkit.getPluginManager().registerEvents(this, MMOCore.plugin);
        Tasks.runEntityLater(player, this::close, 20 * 20);
    }

    public void close() {
        HandlerList.unregisterAll(this);
        WITHDRAWING_PLAYERS.remove(player.getUniqueId());
    }

    public boolean isWithdrawing() {
        return WITHDRAWING_PLAYERS.contains(player.getUniqueId());
    }

    @EventHandler
    public void a(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ())
            return;
        if (!event.getPlayer().equals(player)) return;

        Message.WITHDRAW_CANCEL.send(player);
        close();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void b(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player))
            return;

        event.setCancelled(true);

        final int worth;
        try {
            worth = Integer.parseInt(event.getMessage());
        } catch (Exception e) {
            Message.WITHDRAW_INVALID_AMOUNT.send(player, "arg", event.getMessage());
            return;
        }

        int left = (int) (MMOCore.plugin.economy.getEconomy().getBalance(player) - worth);
        if (left < 0) {
            Message.WITHDRAW_NOT_ENOUGH_MONEY.send(player, "left", -left);
            return;
        }

        close();

        Tasks.runEntity(player, () -> {
            MMOCore.plugin.economy.getEconomy().withdrawPlayer(player, worth);
            withdrawAlgorithm(worth);
            Message.WITHDRAW_SUCCESS.send(player, "worth", worth);
        });
    }

    @Deprecated
    public void withdrawAlgorythm(int worth) {
        withdrawAlgorithm(worth);
    }

    public void withdrawAlgorithm(int worth) {
        int note = worth / 10 * 10;
        int coins = worth - note;

        var smart = new SmartGive(player);
        if (note > 0)
            smart.give(new CurrencyItemBuilder("NOTE", note).build());

        ItemStack coinsItem = new CurrencyItemBuilder("GOLD_COIN", 1).build();
        coinsItem.setAmount(coins);
        smart.give(coinsItem);
    }

    @EventHandler
    public void c(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) close();
    }
}
