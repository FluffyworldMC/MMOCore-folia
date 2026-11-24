package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import io.lumine.mythic.lib.util.TriConsumer;
import net.Indyuce.mmocore.api.event.PlayerLevelChangeEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.command.PlayerCommand;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.PlayerProfessions;
import net.Indyuce.mmocore.experience.Profession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public class LevelCommandTreeNode extends CommandTreeNode {
    public LevelCommandTreeNode(CommandTreeNode parent) {
        super(parent, "level");

        addChild(new ActionCommandTreeNode(this,
                "set",
                (player, newLevel) -> player.setLevel(newLevel, PlayerLevelChangeEvent.Reason.COMMAND),
                (player, profession, amount) -> player.setLevel(profession, amount, PlayerLevelChangeEvent.Reason.COMMAND)));
        addChild(new ActionCommandTreeNode(this,
                "give",
                (data, value) -> data.giveLevels(value, EXPSource.COMMAND),
                (professions, profession, value) -> professions.giveLevels(profession, value, EXPSource.COMMAND)));
        addChild(new ActionCommandTreeNode(this,
                "take",
                (playerData, amount) -> playerData.setLevel(playerData.getLevel() - amount, PlayerLevelChangeEvent.Reason.COMMAND),
                (player, profession, amount) -> player.setLevel(profession, player.getLevel(profession) - amount, PlayerLevelChangeEvent.Reason.COMMAND)));
    }

    public static class ActionCommandTreeNode extends CommandTreeNode {
        private final BiConsumer<PlayerData, Integer> main;
        private final TriConsumer<PlayerProfessions, Profession, Integer> profession;

        private final Argument<Player> argPlayer;
        private final Argument<Profession> argProfession;
        private final Argument<Integer> argAmount;

        public ActionCommandTreeNode(CommandTreeNode parent, String type, BiConsumer<PlayerData, Integer> main,
                                     TriConsumer<PlayerProfessions, Profession, Integer> profession) {
            super(parent, type);

            this.main = main;
            this.profession = profession;

            argPlayer = addArgument(Argument.PLAYER);
            argProfession = addArgument(Arguments.PROFESSION);
            argAmount = addArgument(Argument.AMOUNT_INT);
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            final var player = explorer.parse(argPlayer);
            final var profession = explorer.parse(argProfession);
            final var amount = explorer.parse(argAmount);

            return PlayerCommand.execute(player, explorer, () -> {
                PlayerData data = PlayerData.get(player);
                if (profession == null) {
                    main.accept(data, amount);
                    return explorer.success("&6" + player.getName() + "&e is now Lvl &6" + data.getLevel());
                }

                this.profession.accept(data.getCollectionSkills(), profession, amount);
                return explorer.success("&6" + player.getName() + "&e is now Lvl &6" + data.getCollectionSkills().getLevel(profession) + "&e in &6" + profession.getName());
            });
        }
    }
}
