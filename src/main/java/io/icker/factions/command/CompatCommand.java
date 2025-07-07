package io.icker.factions.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.compat.compatRelationshipCheckLevel;
import io.icker.factions.api.compat.compatRelationshipCheckType;
import io.icker.factions.util.Command;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class CompatCommand implements Command {

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return literal("compat")
                .requires(Command.Requires.isAdmin())
                .then(literal("checklevel")
                        .then(argument("level", StringArgumentType.word())
                                .suggests(Command.Suggests.enumSuggestion(compatRelationshipCheckLevel.class))
                                .executes(ctx -> {
                                    String levelStr = StringArgumentType.getString(ctx, "level").toUpperCase();
                                    try {
                                        compatRelationshipCheckLevel level = compatRelationshipCheckLevel.valueOf(levelStr);
                                        FactionsMod.CONFIG.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_LEVEL = level;
                                        FactionsMod.CONFIG.save();

                                        ctx.getSource().sendFeedback(() ->
                                                Text.literal("compatRelationshipCheckLevel updated : " + level), false);
                                        return 1;
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendError(Text.literal("Invalid value. Options : ENEMY, NEUTRAL, ALLY"));
                                        return 0;
                                    }
                                })))
                .then(literal("checktype")
                        .then(argument("type", StringArgumentType.word())
                                .suggests(Command.Suggests.enumSuggestion(compatRelationshipCheckType.class))
                                .executes(ctx -> {
                                    String typeStr = StringArgumentType.getString(ctx, "type").toUpperCase();
                                    try {
                                        compatRelationshipCheckType type = compatRelationshipCheckType.valueOf(typeStr);
                                        FactionsMod.CONFIG.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_TYPE = type;
                                        FactionsMod.CONFIG.save();

                                        ctx.getSource().sendFeedback(() ->
                                                Text.literal("compatRelationshipCheckType updated : " + type), false);
                                        return 1;
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendError(Text.literal("Invalid value. Options : ONE_SIDED, MUTUAL"));
                                        return 0;
                                    }
                                })))
                .build();
    }
}
