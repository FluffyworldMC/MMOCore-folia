package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.command.PlayerCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * @see net.Indyuce.mmocore.command.builtin.mmocore.clazz.ForceCommandTreeNode
 * @deprecated
 */
@Deprecated
public class LegacyForceClassCommandTreeNode extends CommandTreeNode {
    public LegacyForceClassCommandTreeNode(CommandTreeNode parent) {
        super(parent, "force-class");

        addArgument(Argument.PLAYER);
        addArgument(Arguments.CLASS);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        if (args.length < 4)
            return CommandResult.THROW_USAGE;

        Player player = Bukkit.getPlayer(args[2]);
        if (player == null) return explorer.fail("Could not find the player called " + args[2] + ".");

        String format = args[3].toUpperCase().replace("-", "_");
        if (!MMOCore.plugin.classManager.has(format)) return explorer.fail("Could not find class " + format + ".");

        PlayerClass profess = MMOCore.plugin.classManager.get(format);

        return PlayerCommand.execute(player, explorer, () -> {
            PlayerData data = PlayerData.get(player);
            final var called = new PlayerChangeClassEvent(data, profess, PlayerChangeClassEvent.Reason.COMMAND_FORCE);
            Bukkit.getPluginManager().callEvent(called);
            if (called.isCancelled()) return explorer.fail("Bukkit event canceled");

            data.setClass(profess);
            return explorer.success("&6" + player.getName() + "&e is now a &6" + profess.getName());
        });
    }
}
