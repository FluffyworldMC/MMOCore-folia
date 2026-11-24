package net.Indyuce.mmocore.api.quest.trigger;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.util.annotation.BackwardsCompatibility;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CommandTrigger extends Trigger {
	private final String command;

	public CommandTrigger(MMOLineConfig config) {
		super(config);

		config.validate("format");
		command = config.getString("format");
	}

	@Override
	public void apply(PlayerData player) {
		if (!player.isOnline()) return;
		Player onlinePlayer = player.getPlayer();
		Tasks.executeOnEntityThread(onlinePlayer, () -> {
			// Placeholder expansions can read state owned by the player's region.
			String formatted = format(onlinePlayer);
			Tasks.runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted));
		});
	}

	@BackwardsCompatibility(version = "1.12-SNAPSHOT")
	private String format(Player player) {
		// TODO remove use of confusing non-PAPI %player% placeholder
		// To be done with the MMOCore-MythicLib condition merging update
		return MythicLib.plugin.getPlaceholderParser().parse(player, command.replace("%player%", player.getName()));
	}
}
