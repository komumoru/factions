package io.icker.factions.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import java.util.Optional;
import java.util.UUID;

public class KickCommand implements Command {
    private int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        String name = StringArgumentType.getString(context, "player");

        User target;

        Optional<GameProfile> profile;
        if ((profile = source.getServer().getUserCache().findByName(name)).isPresent()) {
            target = User.get(profile.get().getId());
        } else {
            try {
                target = User.get(UUID.fromString(name));
            } catch (Exception e) {
                new Message("No such player %s!", name)
                        .format(Formatting.RED)
                        .send(player, false);
                return 0;
            }
        }

        if (target.getID().equals(player.getUuid())) {
            new Message("Cannot kick yourself")
                .fail()
                .send(player, false);
            return 0;
        }

        User selfUser = Command.getUser(player);

        if (target.getFaction() == null || !target.getFaction().equals(selfUser.getFaction())) {
            new Message("Cannot kick someone that is not in your faction")
                .fail()
                .send(player, false);
            return 0;
        }

        if (selfUser.rank == User.Rank.LEADER
                && (target.rank == User.Rank.LEADER || target.rank == User.Rank.OWNER)) {
            new Message("Cannot kick members with a higher of equivalent rank")
                .fail()
                .send(player, false);
            return 0;
        }

        ServerPlayerEntity targetPlayer =
                player.getServer().getPlayerManager().getPlayer(target.getID());

        target.leaveFaction();

        if (targetPlayer != null) {
            context.getSource().getServer().getPlayerManager().sendCommandTree(targetPlayer);

            new Message("You have been kicked from the faction by %s",
                                    player.getName().getString())
                    .send(targetPlayer, false);
        }

        new Message("Kicked %s", profile.map((found_profile) -> found_profile.getName())
                                        .orElse("unknown")).send(player, false);

        return 1;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("kick")
                .requires(
                    Requires.multiple(Requires.isLeader(),
                        Requires.hasPerms("factions.kick", 0)))
                .then(
                    CommandManager.argument("player", EntityArgumentType.player())
                        .suggests(Suggests.allPlayersInYourFactionButYou())
                        .executes(this::run))
                .build();
    }
}
