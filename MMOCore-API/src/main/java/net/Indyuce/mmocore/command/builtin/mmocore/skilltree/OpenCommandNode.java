package net.Indyuce.mmocore.command.builtin.mmocore.skilltree;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.manager.InventoryManager;
import net.Indyuce.mmocore.skilltree.tree.SkillTree;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenCommandNode extends CommandTreeNode {
    private final Argument<Player> argPlayer;
    private final Argument<SkillTree> argTree;

    public OpenCommandNode(CommandTreeNode parent) {
        super(parent, "open");

        argPlayer = addArgument(Argument.PLAYER);
        argTree = addArgument(Arguments.SKILL_TREE_OR_GLOBAL).withFallback(explorer -> null);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var player = explorer.parse(argPlayer);
        final @Nullable var skillTree = explorer.parse(argTree);

        final var playerData = PlayerData.get(player);
        final var skillTrees = playerData.getProfess().getSkillTrees();
        if (skillTrees.isEmpty()) {
            return explorer.fail("Player class " + playerData.getProfess().getName() + " of " + player.getName() + " has no skill tree");
        }

        final var skillTreeName = skillTree == null ? "Global" : skillTree.getName();

        // Global skill tree view
        if (skillTree == null) {
            Tasks.openInventory(player, () -> InventoryManager.TREE_VIEW.newInventory(playerData));
            return explorer.success("Skill tree &6" + skillTreeName + "&e opened for player &6" + player.getName());
        }

        // Specific skill tree view
        if (skillTrees.stream().noneMatch(candidate -> skillTree.getId().equals(candidate.getId()))) {
            return explorer.fail("Player class " + playerData.getProfess().getName() + " of " + player.getName() + " has no skill tree " + skillTree.getId());
        }

        Tasks.openInventory(player, () -> InventoryManager.SPECIFIC_TREE_VIEW.get(UtilityMethods.kebabCase(skillTree.getId())).newInventory(playerData));
        return explorer.success("Skill tree &6" + skillTreeName + "&e opened for player &6" + player.getName());
    }
}
