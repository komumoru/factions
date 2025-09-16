package io.icker.factions.command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.compat.compatSkillDamageProtectionfor;
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
                .executes(ctx -> {
                    // Display current values when just /factions compat is used
                    compatSkillDamageProtectionfor currentLevel = FactionsMod.CONFIG.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR;
                    
                    ctx.getSource().sendFeedback(() ->
                            Text.literal("Current compat settings:"), false);
                    ctx.getSource().sendFeedback(() ->
                            Text.literal("- Skill Damage Protection For: " + currentLevel), false);
                    return 1;
                })
                .then(literal("protectionlevel")
                        .executes(ctx -> {
                            // Display current check level
                            compatSkillDamageProtectionfor currentLevel = FactionsMod.CONFIG.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR;
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("Current compatSkillDamageProtectionfor: " + currentLevel), false);
                            return 1;
                        })
                        .then(argument("level", StringArgumentType.word())
                                .suggests(Command.Suggests.enumSuggestion(compatSkillDamageProtectionfor.class))
                                .executes(ctx -> {
                                    String levelStr = StringArgumentType.getString(ctx, "level").toUpperCase();
                                    try {
                                        compatSkillDamageProtectionfor level = compatSkillDamageProtectionfor.valueOf(levelStr);
                                        FactionsMod.CONFIG.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR = level;
                                        FactionsMod.CONFIG.save();
                                        ctx.getSource().sendFeedback(() ->
                                                Text.literal("compatSkillDamageProtectionfor updated: " + level), false);
                                        return 1;
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendError(Text.literal("Invalid value. Options: NONE, ALL, NEUTRAL, FRIENDLY, ALLY"));
                                        return 0;
                                    }
                                })))
                .build();
    }
}