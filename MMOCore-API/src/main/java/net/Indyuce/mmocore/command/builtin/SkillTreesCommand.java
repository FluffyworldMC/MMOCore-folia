package net.Indyuce.mmocore.command.builtin;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeRoot;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.manager.InventoryManager;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.skilltree.tree.SkillTree;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SkillTreesCommand extends CommandTreeRoot {
    private final Argument<SkillTree> argType;

    public SkillTreesCommand(@NotNull ConfigurationSection config) {
        super(config);

        argType = addArgument(MMOCore.plugin.configManager.enableGlobalSkillTreeGUI ? Arguments.SKILL_TREE_OR_GLOBAL.withFallback(e -> null) : Arguments.SKILL_TREE);
        setOnlyForPlayers();
    }

    @Override
    @NotNull
    public CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var data = PlayerData.get((Player) sender);
        if (data.getProfess().getSkillTrees().isEmpty()) {
            Message.NO_SKILL_TREE.send(data);
            return CommandResult.FAILURE;
        }

        final @Nullable var opened = explorer.parse(argType); // null == global
        if (MMOCoreUtils.callLegacyCommandEvent(data, this)) return CommandResult.FAILURE;

        // Global skill tree view
        if (opened == null) {
            Tasks.openInventory((Player) sender, () -> InventoryManager.TREE_VIEW.newInventory(data));
            return CommandResult.FAILURE;
        }

        // Specific skill tree view
        final var classHas = data.getProfess().getSkillTrees().stream().anyMatch(tree -> tree.getId().equals(opened.getId()));
        if (!classHas) {
            Message.NO_CLASS_SKILL_TREE.send(data);
            return CommandResult.FAILURE;
        }

        Tasks.openInventory((Player) sender, () -> InventoryManager.SPECIFIC_TREE_VIEW.get(UtilityMethods.kebabCase(opened.getId())).newInventory(data));
        return CommandResult.SUCCESS;
    }
}
