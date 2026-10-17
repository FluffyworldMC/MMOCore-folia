package net.Indyuce.mmocore.command.builtin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeRoot;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.gui.eco.DepositMenu;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DepositCommand extends CommandTreeRoot {
    private final Argument<Player> argPlayer;

    public DepositCommand(@NotNull ConfigurationSection config) {
        super(config);

        argPlayer = addArgument(Arguments.PLAYER_IF_OP);
    }

    @Override
    @NotNull
    public CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var player = explorer.parse(argPlayer);

        if (MMOCoreUtils.callLegacyCommandEvent(PlayerData.get(player), this)) return CommandResult.FAILURE;

        Tasks.openInventory(player, () -> new DepositMenu(player));
        return CommandResult.SUCCESS;
    }

    // private boolean isNearEnderchest(Location loc) {
    // for (int x = -5; x < 6; x++)
    // for (int y = -5; y < 6; y++)
    // for (int z = -5; z < 6; z++)
    // if (loc.clone().add(x, y, z).getBlock().getType() ==
    // Material.ENDER_CHEST)
    // return true;
    // return false;
    // }
}
