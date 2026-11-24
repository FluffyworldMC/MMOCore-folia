package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.api.player.profess.SavedClassInformation;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.command.PlayerCommand;
import net.Indyuce.mmocore.player.Message;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * @see net.Indyuce.mmocore.command.builtin.mmocore.clazz.ClassCommandTreeNode
 * @deprecated
 */
@Deprecated
public class LegacyClassCommandTreeNode extends CommandTreeNode {
    private final Argument<Player> argPlayer;
    private final Argument<PlayerClass> argClass;

    @Deprecated
    public LegacyClassCommandTreeNode(CommandTreeNode parent) {
        super(parent, "class");

        argPlayer = addArgument(Argument.PLAYER);
        argClass = addArgument(Arguments.CLASS);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        Player player = explorer.parse(argPlayer);
        PlayerClass profess = explorer.parse(argClass);
        return PlayerCommand.execute(player, explorer, () -> {
            PlayerData data = PlayerData.get(player);
            if (data.getProfess().equals(profess)) return CommandResult.SUCCESS;

            PlayerChangeClassEvent called = new PlayerChangeClassEvent(data, profess, PlayerChangeClassEvent.Reason.COMMAND_SELECT);
            Bukkit.getPluginManager().callEvent(called);
            if (called.isCancelled()) return explorer.fail("Bukkit event canceled");

            (data.hasSavedClass(profess) ? data.getClassInfo(profess)
                    : new SavedClassInformation(MMOCore.plugin.playerDataManager.getDefaultData())).load(profess, data);
            if (data.isOnline()) {
                Message.CLASS_SELECT.send(data, "class", profess.getName());
            }

            return explorer.success("&6" + player.getName() + "&e is now a &6" + profess.getName());
        });
    }
}
