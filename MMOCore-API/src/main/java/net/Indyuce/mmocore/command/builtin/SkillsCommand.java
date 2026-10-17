package net.Indyuce.mmocore.command.builtin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeRoot;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.manager.InventoryManager;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SkillsCommand extends CommandTreeRoot {
    private final Argument<Player> argPlayer;

    public SkillsCommand(@NotNull ConfigurationSection config) {
        super(config);

        argPlayer = addArgument(Arguments.PLAYER_IF_OP);
    }

    @Override
    @NotNull
    public CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var player = explorer.parse(argPlayer);

        final var playerData = PlayerData.get(player);
        if (MMOCoreUtils.callLegacyCommandEvent(playerData, this)) return CommandResult.FAILURE;

        if (playerData.getUnlockedSkills().isEmpty()) {
            Message.NO_CLASS_SKILL.send((Player) sender);
            return CommandResult.FAILURE;
        }

        Tasks.openInventory(player, () -> InventoryManager.SKILL_LIST.newInventory(playerData));
        return CommandResult.SUCCESS;
    }
}
