package net.Indyuce.mmocore.command.builtin;

import io.lumine.mythic.lib.command.CommandDisabledException;
import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeRoot;
import io.lumine.mythic.lib.command.argument.Argument;
import io.lumine.mythic.lib.util.lang3.Validate;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.social.Request;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.manager.InventoryManager;
import net.Indyuce.mmocore.party.provided.MMOCorePartyModule;
import net.Indyuce.mmocore.party.provided.PartyInvite;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PartyCommand extends CommandTreeRoot {
    private final Argument<String> argAction;

    public PartyCommand(ConfigurationSection config) {
        super(config);

        argAction = addArgument(Arguments.ACCEPT_OR_DENY_OPTIONAL);

        // Only enable if MMOCore party module is on.
        if (!(MMOCore.plugin.partyModule instanceof MMOCorePartyModule)) throw new CommandDisabledException();
    }

    @Override
    @NotNull
    public CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var action = explorer.parse(argAction);

        final var playerData = PlayerData.get((Player) sender);
        if (MMOCoreUtils.callLegacyCommandEvent(playerData, this)) return CommandResult.FAILURE;

        // Open inventory
        if (action == null) {
            final var player = (Player) sender;
            Tasks.openInventory(player, () -> playerData.getParty() != null
                    ? InventoryManager.PARTY_VIEW.newInventory(playerData)
                    : InventoryManager.PARTY_CREATION.newInventory(playerData));
            return CommandResult.SUCCESS;
        }

        final @Nullable PartyInvite invite;
        if (args.length > 1)

            // Search by request ID
            try {
                final Request req = MMOCore.plugin.requestManager.getRequest(UUID.fromString(args[1]));
                Validate.isTrue(req instanceof PartyInvite && !req.isTimedOut());
                invite = (PartyInvite) req;
                Validate.isTrue(((MMOCorePartyModule) MMOCore.plugin.partyModule).isRegistered(invite.getParty()));
            } catch (Exception exception) {
                return CommandResult.FAILURE;
            }

            // Search by target player
        else invite = MMOCore.plugin.requestManager.findRequest(playerData, PartyInvite.class);

        // No invite found with given identifier/target player
        if (invite == null) {
            Message.PARTY_NO_PENDING_INVITE.send(playerData);
            return CommandResult.FAILURE;
        }

        if (args[0].equalsIgnoreCase("accept")) invite.accept();
        else if (args[0].equalsIgnoreCase("deny")) invite.deny();

        return CommandResult.SUCCESS;
    }
}
