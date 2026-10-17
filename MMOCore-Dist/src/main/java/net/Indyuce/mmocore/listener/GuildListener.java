package net.Indyuce.mmocore.listener;

import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.social.GuildChatEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class GuildListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void a(AsyncPlayerChatEvent event) {
        if (!event.getMessage().startsWith(MMOCore.plugin.nativeGuildManager.getConfig().getPrefix())) return;

        PlayerData data = PlayerData.get(event.getPlayer());
        if (!data.inGuild()) return;

        event.setCancelled(true);

        Tasks.runEntity(event.getPlayer(), () -> {
            final var rawMessage = event.getMessage().substring(MMOCore.plugin.nativeGuildManager.getConfig().getPrefix().length());
            final var called = new GuildChatEvent(data, rawMessage);
            Bukkit.getPluginManager().callEvent(called);
            if (called.isCancelled() || called.getMessage() == null) return;

            String name = data.getPlayer().getName();
            String tag = data.getGuild().getTag();
            String message = called.getMessage();
            data.getGuild().forEachMember(member -> {
                Player online = Bukkit.getPlayer(member);
                if (online != null)
                    Tasks.executeEntity(online, () -> Message.GUILD_CHAT.send(online, "player", name, "tag", tag, "message", message));
            });
        });
    }
}
