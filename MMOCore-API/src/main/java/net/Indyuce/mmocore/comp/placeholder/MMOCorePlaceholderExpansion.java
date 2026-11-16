package net.Indyuce.mmocore.comp.placeholder;

import io.lumine.mythic.lib.comp.placeholder.api.PlaceholderEntry;
import io.lumine.mythic.lib.comp.placeholder.api.PluginPlaceholderExpansion;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public class MMOCorePlaceholderExpansion extends PluginPlaceholderExpansion<PlayerData> {
    public MMOCorePlaceholderExpansion(MMOCore plugin) {
        super(plugin);
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String raw) {
        String result = MMOCore.plugin.classPlaceholders.request(player, raw);
        return result != null ? result : super.onRequest(player, raw);
    }

    @Override
    public @NotNull Iterable<PlaceholderEntry<PlayerData>> getPlaceholderRegistry() {
        return Arrays.asList(PlaceholderEnum.values());
    }

    @Override
    public @NotNull PlayerData getPlayerData(OfflinePlayer player) {
        return PlayerData.get(player);
    }
}
