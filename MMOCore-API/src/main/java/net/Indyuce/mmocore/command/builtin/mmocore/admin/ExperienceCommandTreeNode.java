package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import io.lumine.mythic.lib.util.QuadConsumer;
import io.lumine.mythic.lib.util.TriConsumer;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.command.PlayerCommand;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.PlayerProfessions;
import net.Indyuce.mmocore.experience.Profession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExperienceCommandTreeNode extends CommandTreeNode {
    public ExperienceCommandTreeNode(CommandTreeNode parent) {
        super(parent, "exp");

        addChild(new CheckCommandTreeNode(this));
        addChild(new ActionCommandTreeNode(this, "set",
                false,
                (data, value, split) -> data.setExperience(value),
                (professions, profession, value, split) -> professions.setExperience(profession, value)));
        addChild(new ActionCommandTreeNode(this, "give",
                true,
                (data, value, split) -> data.giveExperience(value, EXPSource.COMMAND, null, split),
                (professions, profession, value, split) -> professions.giveExperience(profession, value, EXPSource.COMMAND, null, split)));
        addChild(new ActionCommandTreeNode(this, "take",
                false,
                (data, value, split) -> data.giveExperience(-value, EXPSource.COMMAND),
                (professions, profession, value, split) -> professions.giveExperience(profession, -value, EXPSource.COMMAND)));
    }

    public static class CheckCommandTreeNode extends CommandTreeNode {
        private final Argument<Player> argPlayer;
        private final Argument<Profession> argProfession;

        public CheckCommandTreeNode(CommandTreeNode parent) {
            super(parent, "check");

            argPlayer = addArgument(Argument.PLAYER);
            argProfession = addArgument(Arguments.PROFESSION.withFallback(explorer -> null));
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            final var player = explorer.parse(this.argPlayer);
            final @Nullable var profession = explorer.parse(this.argProfession);

            return PlayerCommand.execute(player, explorer, () -> {
                PlayerData data = PlayerData.get(player);
                final var formatter = MythicLib.plugin.getMMOConfig().decimal;

                if (profession == null)
                    return explorer.success("&6" + player.getName() + "&e has &6" + formatter.format(data.getExperience()) + "&e class EXP.");

                final var currentExp = data.getCollectionSkills().getExperience(profession);
                return explorer.success("&6" + player.getName() + "&e has &6" + formatter.format(currentExp) + "&e EXP in &6" + profession.getName() + "&e.");
            });
        }
    }

    public static class ActionCommandTreeNode extends CommandTreeNode {
        private final TriConsumer<PlayerData, Double, Boolean> main;
        private final QuadConsumer<PlayerProfessions, Profession, Double, Boolean> profession;

        private final Argument<Player> argPlayer;
        private final Argument<Double> argAmount;
        private final Argument<Profession> argProfession;
        private final Argument<Boolean> argSplit;

        public ActionCommandTreeNode(CommandTreeNode parent,
                                     String type,
                                     boolean hasSplit,
                                     TriConsumer<PlayerData, Double, Boolean> main,
                                     QuadConsumer<PlayerProfessions, Profession, Double, Boolean> profession) {
            super(parent, type);

            this.main = main;
            this.profession = profession;

            argPlayer = addArgument(Argument.PLAYER);
            argProfession = addArgument(Arguments.PROFESSION);
            argAmount = addArgument(Argument.AMOUNT_DOUBLE);
            argSplit = hasSplit ? addArgument(Argument.BOOLEAN.withKey("split").withFallback(explorer -> true)) : null;
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            final var player = explorer.parse(this.argPlayer);
            final var amount = explorer.parse(this.argAmount);
            final @Nullable var profession = explorer.parse(this.argProfession);
            final var split = this.argSplit != null && explorer.parse(this.argSplit);

            return PlayerCommand.execute(player, explorer, () -> {
                PlayerData data = PlayerData.get(player);
                final var formatter = MythicLib.plugin.getMMOConfig().decimal;

                if (profession == null) {
                    main.accept(data, amount, split);
                    return explorer.success("&6" + player.getName() + "&e now has &6" + formatter.format(data.getExperience()) + "&e class EXP.");
                }

                this.profession.accept(data.getCollectionSkills(), profession, amount, split);
                final var currentExp = data.getCollectionSkills().getExperience(profession);
                return explorer.success("&6" + player.getName() + "&e now has &6" + formatter.format(currentExp) + "&e EXP in &6" + profession.getName() + "&e.");
            });
        }
    }
}
