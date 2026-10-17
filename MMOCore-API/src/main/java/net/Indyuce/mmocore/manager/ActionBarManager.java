package net.Indyuce.mmocore.manager;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.gui.editable.placeholder.Placeholders;
import io.lumine.mythic.lib.manager.StatManager;
import io.lumine.mythic.lib.message.actionbar.ActionBarPriority;
import io.lumine.mythic.lib.version.Attributes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class ActionBarManager {
    private int updateTicks;
    private String barFormat;
    private boolean enabled, scheduled;
    private ScheduledTask task;

    public static final int PRIORITY = ActionBarPriority.LOWEST;

    public void reload(ConfigurationSection config) {
        enabled = config.getBoolean("enabled", false);
        updateTicks = config.getInt("ticks-to-update", 5);
        barFormat = config.getString("format", "<No Action Bar Format Found>");

        if (!scheduled && enabled) {
            task = Tasks.runForEachPlayer(1, Math.max(1, updateTicks), this::tickPlayer);
            scheduled = true;
        } else if (scheduled && !enabled) {
            Tasks.cancel(task);
            task = null;
            scheduled = false;
        }
    }

    private void tickPlayer(Player bukkitPlayer) {
        PlayerData player = PlayerData.get(bukkitPlayer);
        if (bukkitPlayer.isDead() || !player.getMMOPlayerData().isPlaying()) return;

        player.getMMOPlayerData().getActionBar().show(PRIORITY, () -> {
            var placeholders = getActionBarPlaceholders(player);
            var rawMessage = player.getProfess().hasActionBar() ? player.getProfess().getActionBar() : barFormat;
            return placeholders.apply(bukkitPlayer, rawMessage);
        });
    }

    public Placeholders getActionBarPlaceholders(PlayerData data) {
        Placeholders holders = new Placeholders();
        holders.register("health", StatManager.format("MAX_HEALTH", data.getPlayer().getHealth()));
        holders.register("max_health", StatManager.format("MAX_HEALTH", data.getPlayer().getAttribute(Attributes.MAX_HEALTH).getValue()));
        holders.register("mana_icon", data.getProfess().getManaDisplay().getIcon());
        holders.register("mana", StatManager.format("MAX_MANA", data.getMana()));
        holders.register("max_mana", StatManager.format("MAX_MANA", data.getStats().getStat("MAX_MANA")));
        holders.register("stamina", StatManager.format("MAX_STAMINA", data.getStamina()));
        holders.register("max_stamina", StatManager.format("MAX_STAMINA", data.getStats().getStat("MAX_STAMINA")));
        holders.register("stellium", StatManager.format("MAX_STELLIUM", data.getStellium()));
        holders.register("max_stellium", StatManager.format("MAX_STELLIUM", data.getStats().getStat("MAX_STELLIUM")));
        holders.register("class", data.getProfess().getName());
        holders.register("xp", MythicLib.plugin.getMMOConfig().decimal.format(data.getExperience()));
        holders.register("armor", StatManager.format("ARMOR", data.getPlayer().getAttribute(Attributes.ARMOR).getValue()));
        holders.register("level", String.valueOf(data.getLevel()));
        holders.register("name", data.getPlayer().getDisplayName());
        return holders;
    }
}
