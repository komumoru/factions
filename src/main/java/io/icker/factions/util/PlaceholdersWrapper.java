package io.icker.factions.util;

import java.util.function.Function;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class PlaceholdersWrapper {
    private static final Text UNFORMATTED_NULL = Text.of("N/A");
    private static final Text FORMATTED_NULL =
            UNFORMATTED_NULL.copy().formatted(Formatting.DARK_GRAY);

    private static void register(String identifier, Function<User, Text> handler) {
        Placeholders.register(new Identifier(FactionsMod.MODID, identifier), (ctx, argument) -> {
            if (!ctx.hasPlayer())
                return PlaceholderResult.invalid("No player found");

            User member = User.get(ctx.player().getUuid());
            return PlaceholderResult.value(handler.apply(member));
        });
    }

    public static void init() {
        register("name", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return FORMATTED_NULL;

            return Text.literal(faction.getName()).formatted(member.getFaction().getColor());
        });

        register("colorless_name", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return FORMATTED_NULL;

            return Text.of(faction.getName());
        });

        register("chat", (member) -> {
            if (member.chat == User.ChatMode.GLOBAL || !member.isInFaction())
                return Text.of("Global Chat");

            return Text.of("Faction Chat");
        });

        register("rank", (member) -> {
            if (!member.isInFaction())
                return FORMATTED_NULL;

            return Text.of(member.getRankName());
        });

        register("color", (member) -> {
            if (!member.isInFaction())
                return FORMATTED_NULL;

            return Text.of(member.getFaction().getColor().getName());
        });

        register("description", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return FORMATTED_NULL;

            return Text.of(faction.getDescription());
        });

        register("state", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return UNFORMATTED_NULL;

            return Text.of(String.valueOf(faction.isOpen()));

        });

        register("power", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return UNFORMATTED_NULL;

            return Text.of(String.valueOf(faction.getPower()));
        });

        register("power_formatted", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return FORMATTED_NULL;

            int maxPower = Math.max(1, faction.getMaxPower());
            int current = faction.getPower();
            int red = clampColor(mapBoundRange(0, maxPower, 170, 255, maxPower - current));
            int green = clampColor(mapBoundRange(0, maxPower, 170, 255, current));
            return Text.literal(String.valueOf(current)).setStyle(Style.EMPTY
                    .withColor(TextColor.parse(String.format("#%02X%02XAA", red, green))));
        });

        register("max_power", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return UNFORMATTED_NULL;

            return Text.of(String.valueOf(faction.getMaxPower()));
        });

        register("player_power", (member) -> {
            return Text.of(String.valueOf(member.getPower()));
        });

        register("required_power", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return UNFORMATTED_NULL;

            return Text.of(String.valueOf(faction.getDemesne()));
        });

        register("required_power_formatted", (member) -> {
            Faction faction = member.getFaction();
            if (faction == null)
                return FORMATTED_NULL;

            int reqPower = faction.getDemesne();
            boolean overextended = reqPower > faction.getPower();
            int color = overextended ? 0xFF5555 : 0x55FF55;
            return Text.literal(String.valueOf(reqPower))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        });
    }

    private static int mapBoundRange(int fromMin, int fromMax, int toMin, int toMax, int value) {
        if (fromMax == fromMin) {
            return toMax;
        }
        return Math.min(toMax, Math.max(toMin,
                toMin + ((value - fromMin) * (toMax - toMin)) / (fromMax - fromMin)));
    }

    private static int clampColor(int value) {
        return Math.min(255, Math.max(0, value));
    }
}
