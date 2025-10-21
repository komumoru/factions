package io.icker.factions.command;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class ClaimCommand implements Command {
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000L;
    private static final Map<UUID, PendingClaim> PENDING_CLAIMS = new HashMap<>();
    private static final Map<UUID, PendingUnclaim> PENDING_UNCLAIMS = new HashMap<>();

    private static class PendingClaim {
        final UUID factionId;
        final String dimension;
        final ChunkPos origin;
        final int size;
        final long expiresAt;

        PendingClaim(UUID factionId, String dimension, ChunkPos origin, int size) {
            this.factionId = factionId;
            this.dimension = dimension;
            this.origin = origin;
            this.size = size;
            this.expiresAt = System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private static class PendingUnclaim {
        final UUID factionId;
        final String dimension;
        final ChunkPos origin;
        final int size;
        final long expiresAt;

        PendingUnclaim(UUID factionId, String dimension, ChunkPos origin, int size) {
            this.factionId = factionId;
            this.dimension = dimension;
            this.origin = origin;
            this.size = size;
            this.expiresAt = System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private static class ClaimPlan {
        final ArrayList<ChunkPos> chunks;
        final HashSet<Claim> claimsToRemove;

        ClaimPlan(ArrayList<ChunkPos> chunks, HashSet<Claim> claimsToRemove) {
            this.chunks = chunks;
            this.claimsToRemove = claimsToRemove;
        }
    }

    private int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        List<Claim> claims = Command.getUser(player).getFaction().getClaims();
        int count = claims.size();

        new Message("You have ").add(new Message(String.valueOf(count)).format(Formatting.YELLOW))
                .add(" claim%s", count == 1 ? "" : "s").send(source.getPlayerOrThrow(), false);

        if (count == 0)
            return 1;

        HashMap<String, ArrayList<Claim>> claimsMap = new HashMap<String, ArrayList<Claim>>();

        claims.forEach(claim -> {
            claimsMap.putIfAbsent(claim.level, new ArrayList<Claim>());
            claimsMap.get(claim.level).add(claim);
        });

        Message claimText = new Message("");
        claimsMap.forEach((level, array) -> {
            level = Pattern.compile("_([a-z])").matcher(level.split(":", 2)[1])
                    .replaceAll(m -> " " + m.group(1).toUpperCase());
            level = level.substring(0, 1).toUpperCase() + level.substring(1);
            claimText.add("\n");
            claimText.add(new Message(level).format(Formatting.GRAY));
            claimText.filler("»");
            claimText.add(array.stream().map(claim -> String.format("(%d,%d)", claim.x, claim.z))
                    .collect(Collectors.joining(", ")));
        });

        claimText.format(Formatting.ITALIC).send(source.getPlayerOrThrow(), false);
        return 1;
    }

    private int add(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return attemptClaim(context, 1);
    }

    private int addSize(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int size = IntegerArgumentType.getInteger(context, "size");
        return attemptClaim(context, size);
    }

    private int attemptClaim(CommandContext<ServerCommandSource> context, int size)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            return 0;
        }

        int maxSize = Math.max(1, FactionsMod.CONFIG.CLAIM.MAX_BATCH_SIZE);
        if (size > maxSize) {
            new Message("Claims are limited to a maximum size of %d", maxSize).fail().send(player, false);
            return 0;
        }

        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos origin = player.getWorld().getChunk(player.getBlockPos()).getPos();

        ClaimPlan plan = computeClaim(player, faction, origin, dimension, size);
        if (plan == null) {
            return 0;
        }

        if (FactionsMod.CONFIG.CLAIM.REQUIRE_FIRST_CLAIM_CONFIRMATION && faction.getDemesne() == 0
                && !faction.isFirstClaimConfirmed()) {
            queueFirstClaimConfirmation(player, faction, origin, dimension, size);
            return 1;
        }

        int result = applyClaimPlan(player, faction, dimension, size, plan);
        if (result == 1) {
            faction.setFirstClaimConfirmed(true);
        }
        return result;
    }

    private int confirmClaim(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        UUID playerId = player.getUuid();
        PendingClaim pending = PENDING_CLAIMS.remove(playerId);

        if (pending == null) {
            new Message("No pending claim to confirm").fail().send(player, false);
            return 0;
        }

        if (pending.isExpired()) {
            new Message("Claim confirmation expired, please run the command again").fail().send(player,
                    false);
            return 0;
        }

        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        if (faction == null || !faction.getID().equals(pending.factionId)) {
            new Message("You are no longer able to confirm this claim").fail().send(player, false);
            return 0;
        }

        ClaimPlan plan = computeClaim(player, faction, pending.origin, pending.dimension, pending.size);
        if (plan == null) {
            return 0;
        }

        int result = applyClaimPlan(player, faction, pending.dimension, pending.size, plan);
        if (result == 1) {
            faction.setFirstClaimConfirmed(true);
        }
        return result;
    }

    private ClaimPlan computeClaim(ServerPlayerEntity player, Faction faction, ChunkPos origin,
            String dimension, int size) {
        if (!dimension.equals(World.OVERWORLD.getValue().toString())) {
            new Message("Claims are only allowed in the Overworld").fail().send(player, false);
            return null;
        }

        ArrayList<ChunkPos> chunks = new ArrayList<>();
        HashMap<UUID, Integer> enemyClaimsTaken = new HashMap<>();
        HashSet<Claim> claimsToRemove = new HashSet<>();

        for (int x = -size + 1; x < size; x++) {
            for (int z = -size + 1; z < size; z++) {
                ChunkPos chunkPos = new ChunkPos(origin.x + x, origin.z + z);
                Claim existingClaim = Claim.get(chunkPos.x, chunkPos.z, dimension);

                if (existingClaim != null) {
                    Faction owner = existingClaim.getFaction();
                    if (owner != null && owner.getID() == faction.getID()) {
                        if (size == 1) {
                            new Message("Your faction already owns this chunk").fail().send(player,
                                    false);
                            return null;
                        }
                        continue;
                    }

                    if (owner == null || !faction.isMutualEnemies(owner.getID())) {
                        new Message("You must be mutual enemies with %s to claim this chunk",
                                owner == null ? "this faction" : owner.getName()).fail()
                                        .send(player, false);
                        return null;
                    }

                    int alreadyTaken = enemyClaimsTaken.getOrDefault(owner.getID(), 0);
                    int remainingDemesne = owner.getDemesne() - alreadyTaken;
                    if (remainingDemesne <= owner.getPower()) {
                        new Message("%s still has enough power to protect this chunk",
                                owner.getName()).fail().send(player, false);
                        return null;
                    }

                    enemyClaimsTaken.put(owner.getID(), alreadyTaken + 1);
                    claimsToRemove.add(existingClaim);
                }

                chunks.add(chunkPos);
            }
        }

        if (chunks.isEmpty()) {
            new Message("No new chunks to claim").fail().send(player, false);
            return null;
        }

        if (faction.getPower() < faction.getDemesne() + chunks.size()) {
            new Message("Not enough faction power to claim chunk" + (chunks.size() > 1 ? "s" : ""))
                    .fail().send(player, false);
            return null;
        }

        List<Claim> existingLevelClaims = faction.getClaims().stream()
                .filter(claim -> claim.level.equals(dimension)).toList();

        if (!existingLevelClaims.isEmpty()) {
            int connectedIndex = -1;
            for (int i = 0; i < chunks.size(); i++) {
                ChunkPos chunk = chunks.get(i);
                if (faction.canClaimConnected(chunk.x, chunk.z, dimension, existingLevelClaims)) {
                    connectedIndex = i;
                    break;
                }
            }

            if (connectedIndex == -1) {
                new Message("Claims must be connected to your existing territory").fail().send(player,
                        false);
                return null;
            }

            if (connectedIndex != 0) {
                Collections.swap(chunks, 0, connectedIndex);
            }
        }

        return new ClaimPlan(chunks, claimsToRemove);
    }

    private int applyClaimPlan(ServerPlayerEntity player, Faction faction, String dimension, int size,
            ClaimPlan plan) {
        for (ChunkPos chunk : plan.chunks) {
            Claim existing = Claim.get(chunk.x, chunk.z, dimension);
            if (existing != null && existing.getFaction() != null
                    && existing.getFaction().getID() != faction.getID()
                    && plan.claimsToRemove.contains(existing)) {
                plan.claimsToRemove.remove(existing);
                existing.remove();
            }

            if (!faction.addClaim(chunk.x, chunk.z, dimension)) {
                new Message("Claims must be connected to your existing territory").fail().send(player,
                        false);
                return 0;
            }
        }

        if (size == 1) {
            ChunkPos chunk = plan.chunks.get(0);
            new Message("Chunk (%d, %d) claimed by %s", chunk.x, chunk.z, player.getName().getString())
                    .send(faction);
        } else {
            ChunkPos chunk = plan.chunks.get(0);
            new Message("Chunks (%d, %d) to (%d, %d) claimed by %s", chunk.x, chunk.z,
                    chunk.x + size - 1, chunk.z + size - 1, player.getName().getString()).send(faction);
        }

        return 1;
    }

    private int remove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return attemptRemove(context, 1);
    }

    private int removeSize(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        int size = IntegerArgumentType.getInteger(context, "size");
        return attemptRemove(context, size);
    }

    private int attemptRemove(CommandContext<ServerCommandSource> context, int size)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            return 0;
        }

        int maxSize = Math.max(1, FactionsMod.CONFIG.CLAIM.MAX_BATCH_SIZE);
        if (size > maxSize) {
            new Message("Removals are limited to a maximum size of %d", maxSize).fail().send(player,
                    false);
            return 0;
        }

        if (faction.isUnclaimOnCooldown()) {
            String remaining = formatDuration(faction.getUnclaimCooldownRemaining());
            new Message("Your faction can remove another claim in %s", remaining).fail().send(player,
                    false);
            return 0;
        }

        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos origin = player.getWorld().getChunk(player.getBlockPos()).getPos();

        List<Claim> claims = collectRemovableClaims(player, faction, user, origin, dimension, size);
        if (claims == null || claims.isEmpty()) {
            return 0;
        }

        if (FactionsMod.CONFIG.CLAIM.REQUIRE_UNCLAIM_CONFIRMATION) {
            queueUnclaimConfirmation(player, faction, origin, dimension, size);
            return 1;
        }

        return performRemoval(player, faction, claims, size, dimension, origin);
    }

    private int confirmRemove(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        UUID playerId = player.getUuid();
        PendingUnclaim pending = PENDING_UNCLAIMS.remove(playerId);

        if (pending == null) {
            new Message("No pending claim removal to confirm").fail().send(player, false);
            return 0;
        }

        if (pending.isExpired()) {
            new Message("Claim removal confirmation expired, please try again").fail().send(player,
                    false);
            return 0;
        }

        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        if (faction == null || !faction.getID().equals(pending.factionId)) {
            new Message("You can no longer confirm this claim removal").fail().send(player, false);
            return 0;
        }

        if (faction.isUnclaimOnCooldown()) {
            String remaining = formatDuration(faction.getUnclaimCooldownRemaining());
            new Message("Your faction can remove another claim in %s", remaining).fail().send(player,
                    false);
            return 0;
        }

        List<Claim> claims = collectRemovableClaims(player, faction, user, pending.origin,
                pending.dimension, pending.size);
        if (claims == null || claims.isEmpty()) {
            return 0;
        }

        return performRemoval(player, faction, claims, pending.size, pending.dimension, pending.origin);
    }

    private List<Claim> collectRemovableClaims(ServerPlayerEntity player, Faction faction, User user,
            ChunkPos origin, String dimension, int size) {
        ArrayList<Claim> claims = new ArrayList<>();

        if (size == 1) {
            Claim existingClaim = Claim.get(origin.x, origin.z, dimension);
            if (existingClaim == null) {
                new Message("Cannot remove a claim on an unclaimed chunk").fail().send(player, false);
                return null;
            }

            if (!user.bypass && existingClaim.getFaction() != null
                    && existingClaim.getFaction().getID() != faction.getID()) {
                new Message("Cannot remove a claim owned by another faction").fail().send(player,
                        false);
                return null;
            }

            claims.add(existingClaim);
            return claims;
        }

        for (int x = -size + 1; x < size; x++) {
            for (int z = -size + 1; z < size; z++) {
                ChunkPos chunkPos = new ChunkPos(origin.x + x, origin.z + z);
                Claim existingClaim = Claim.get(chunkPos.x, chunkPos.z, dimension);

                if (existingClaim == null) {
                    continue;
                }

                if (!user.bypass && existingClaim.getFaction() != null
                        && existingClaim.getFaction().getID() != faction.getID()) {
                    continue;
                }

                claims.add(existingClaim);
            }
        }

        if (claims.isEmpty()) {
            new Message("There are no removable claims in this area").fail().send(player, false);
        }

        return claims;
    }

    private int performRemoval(ServerPlayerEntity player, Faction faction, List<Claim> claims, int size,
            String dimension, ChunkPos origin) {
        HashSet<String> removedKeys = new HashSet<>();
        for (Claim claim : claims) {
            if (claim == null) {
                continue;
            }
            String key = claim.getKey();
            if (removedKeys.contains(key)) {
                continue;
            }
            removedKeys.add(key);
            claim.remove();
        }

        if (removedKeys.isEmpty()) {
            return 0;
        }

        long cooldownMillis = Math.max(0L,
                (long) FactionsMod.CONFIG.CLAIM.UNCLAIM_COOLDOWN_SECONDS * 1000L);
        faction.setUnclaimCooldownExpiry(System.currentTimeMillis() + cooldownMillis);

        if (size == 1) {
            Claim removed = claims.get(0);
            new Message("Claim (%d, %d) removed by %s", removed.x, removed.z,
                    player.getName().getString()).send(faction);
        } else {
            int minX = origin.x - size + 1;
            int minZ = origin.z - size + 1;
            int maxX = origin.x + size - 1;
            int maxZ = origin.z + size - 1;
            new Message("Claims (%d, %d) to (%d, %d) removed by %s", minX, minZ, maxX, maxZ,
                    player.getName().getString()).send(faction);
        }

        if (faction.getDemesne() == 0) {
            faction.setFirstClaimConfirmed(false);
        }

        return 1;
    }

    private static void queueUnclaimConfirmation(ServerPlayerEntity player, Faction faction, ChunkPos origin,
            String dimension, int size) {
        PENDING_UNCLAIMS.put(player.getUuid(),
                new PendingUnclaim(faction.getID(), dimension, origin, size));
        sendUnclaimWarning(player, size);
    }

    public static void queueFirstClaimConfirmation(ServerPlayerEntity player, Faction faction, ChunkPos origin,
            String dimension, int size) {
        PENDING_CLAIMS.put(player.getUuid(), new PendingClaim(faction.getID(), dimension, origin, size));
        sendFirstClaimWarning(player, size);
    }

    private static void sendFirstClaimWarning(ServerPlayerEntity player, int size) {
        String cooldownText = formatSeconds(FactionsMod.CONFIG.CLAIM.UNCLAIM_COOLDOWN_SECONDS);
        new Message("⚠ Claiming your first land is an important decision.").format(Formatting.YELLOW)
                .send(player, false);
        new Message(" - Removing land triggers a %s cooldown for your faction.", cooldownText)
                .format(Formatting.GRAY).send(player, false);
        new Message(" - Claims must connect to your existing territory.").format(Formatting.GRAY)
                .send(player, false);
        new Message(" - Valuable resources can only be mined on land your faction owns.")
                .format(Formatting.GRAY).send(player, false);

        if (size > 1) {
            new Message("This request covers a %dx%d area.", size, size).format(Formatting.GRAY)
                    .send(player, false);
        }

        Message confirm = new Message("[Confirm Claim]").format(Formatting.GREEN)
                .click("/f claim add confirm")
                .hover("Confirm claiming your first land");
        new Message("Click ").format(Formatting.GRAY).add(confirm).add(" to proceed.").send(player, false);
    }

    private static void sendUnclaimWarning(ServerPlayerEntity player, int size) {
        String cooldownText = formatSeconds(FactionsMod.CONFIG.CLAIM.UNCLAIM_COOLDOWN_SECONDS);
        new Message("⚠ Removing this claim will trigger a %s cooldown for your faction.", cooldownText)
                .format(Formatting.YELLOW).send(player, false);
        if (size > 1) {
            new Message("You are about to remove a %dx%d area.", size, size).format(Formatting.GRAY)
                    .send(player, false);
        }
        Message confirm = new Message("[Confirm Removal]").format(Formatting.RED)
                .click("/f claim remove confirm")
                .hover("Confirm removing this claim");
        new Message("Click ").format(Formatting.GRAY).add(confirm).add(" to continue.")
                .send(player, false);
    }

    private static String formatSeconds(long seconds) {
        return formatDuration(seconds * 1000L);
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).toSeconds();

        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h");
        }
        if (minutes > 0) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(minutes).append("m");
        }
        if (builder.length() == 0) {
            builder.append(Math.max(1, seconds)).append("s");
        }
        return builder.toString();
    }

    private int removeAll(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        Faction faction = Command.getUser(player).getFaction();
        if (faction == null) {
            return 0;
        }

        if (faction.isUnclaimOnCooldown()) {
            String remaining = formatDuration(faction.getUnclaimCooldownRemaining());
            new Message("Your faction can remove another claim in %s", remaining).fail().send(player, false);
            return 0;
        }

        List<Claim> claims = faction.getClaims();
        if (claims.isEmpty()) {
            new Message("Your faction has no claims to remove").fail().send(player, false);
            return 0;
        }

        for (Claim claim : claims) {
            claim.remove();
        }

        long cooldownMillis = Math.max(0L,
                (long) FactionsMod.CONFIG.CLAIM.UNCLAIM_COOLDOWN_SECONDS * 1000L);
        faction.setUnclaimCooldownExpiry(System.currentTimeMillis() + cooldownMillis);
        faction.setFirstClaimConfirmed(false);

        new Message("All claims removed by %s", player.getName().getString()).send(faction);
        return 1;
    }

    private int auto(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        User user = Command.getUser(player);
        user.autoclaim = !user.autoclaim;

        new Message("Successfully toggled autoclaim").filler("·")
                .add(new Message(user.autoclaim ? "ON" : "OFF")
                        .format(user.autoclaim ? Formatting.GREEN : Formatting.RED))
                .send(player, false);

        return 1;
    }

    @SuppressWarnings("")
    private int setAccessLevel(CommandContext<ServerCommandSource> context, boolean increase)
            throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = (ServerWorld) player.getWorld();

        ChunkPos chunkPos = world.getChunk(player.getBlockPos()).getPos();
        String dimension = world.getRegistryKey().getValue().toString();

        Claim claim = Claim.get(chunkPos.x, chunkPos.z, dimension);

        if (claim == null) {
            new Message("Cannot change access level on unclaimed chunk").fail().send(player, false);
            return 0;
        }

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (!user.bypass && claim.getFaction().getID() != faction.getID()) {
            new Message("Cannot change access level on another factions claim").fail().send(player,
                    false);
            return 0;
        }

        if (increase) {
            if (claim.accessLevel.ordinal() <= user.rank.ordinal()) {
                new Message("Cannot increase access level to higher then your rank")
                        .fail()
                        .send(player, false);
                return 0;
            }

            switch (claim.accessLevel) {
                case OWNER -> {
                    new Message("Cannot increase access level as it is already at its maximum.")
                            .fail().send(player, false);
                    return 0;
                }
                case LEADER -> claim.accessLevel = User.Rank.OWNER;
                case COMMANDER -> claim.accessLevel = User.Rank.LEADER;
                case MEMBER -> claim.accessLevel = User.Rank.COMMANDER;
                case GUEST -> throw new UnsupportedOperationException("Unimplemented case: " + claim.accessLevel);
                default -> throw new IllegalArgumentException("Unexpected value: " + claim.accessLevel);
            }
        } else {
            if (claim.accessLevel.ordinal() <= user.rank.ordinal()) {
                new Message("Cannot decrease access level from higher then your rank")
                        .fail()
                        .send(player, false);
                return 0;
            }

            switch (claim.accessLevel) {
                case OWNER -> claim.accessLevel = User.Rank.LEADER;
                case LEADER -> claim.accessLevel = User.Rank.COMMANDER;
                case COMMANDER -> claim.accessLevel = User.Rank.MEMBER;
                case MEMBER -> {
                    new Message("Cannot decrease access level as it is already at its minimum.")
                            .fail().send(player, false);
                    return 0;
                }
                case GUEST -> throw new UnsupportedOperationException("Unimplemented case: " + claim.accessLevel);
                default -> throw new IllegalArgumentException("Unexpected value: " + claim.accessLevel);
            }
        }

        new Message("Claim (%d, %d) changed to level %s by %s", claim.x, claim.z,
                claim.accessLevel.toString(), player.getName().getString()).send(faction);
        return 1;
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        int maxSize = Math.max(1, FactionsMod.CONFIG.CLAIM.MAX_BATCH_SIZE);

        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("claim")
                .requires(Requires.isCommander());

        LiteralArgumentBuilder<ServerCommandSource> add = CommandManager.literal("add")
                .requires(Requires.hasPerms("factions.claim.add", 0));
        add.then(CommandManager.literal("confirm")
                .requires(Requires.hasPerms("factions.claim.add", 0))
                .executes(this::confirmClaim));
        add.then(CommandManager.argument("size", IntegerArgumentType.integer(1, maxSize))
                .requires(Requires.hasPerms("factions.claim.add.size", 0))
                .executes(this::addSize)
                .then(CommandManager.literal("force")
                        .requires(Requires.hasPerms("factions.claim.add.force", 0).and(Requires.isLeader()))
                        .executes(this::addSize)));
        add.executes(this::add);
        root.then(add);

        root.then(CommandManager.literal("list")
                .requires(Requires.hasPerms("factions.claim.list", 0))
                .executes(this::list));

        LiteralArgumentBuilder<ServerCommandSource> remove = CommandManager.literal("remove")
                .requires(Requires.hasPerms("factions.claim.remove", 0).and(Requires.isLeader()));
        remove.then(CommandManager.literal("confirm")
                .requires(Requires.hasPerms("factions.claim.remove", 0).and(Requires.isLeader()))
                .executes(this::confirmRemove));
        remove.then(CommandManager.argument("size", IntegerArgumentType.integer(1, maxSize))
                .requires(Requires.hasPerms("factions.claim.remove.size", 0))
                .executes(this::removeSize));
        if (FactionsMod.CONFIG.CLAIM.ALLOW_REMOVE_ALL) {
            remove.then(CommandManager.literal("all")
                    .requires(Requires.hasPerms("factions.claim.remove.all", 0))
                    .executes(this::removeAll));
        }
        remove.executes(this::remove);
        root.then(remove);

        root.then(CommandManager.literal("auto")
                .requires(Requires.hasPerms("factions.claim.auto", 0))
                .executes(this::auto));

        LiteralArgumentBuilder<ServerCommandSource> access = CommandManager.literal("access")
                .requires(Requires.hasPerms("factions.claim.access", 0));
        access.then(CommandManager.literal("increase")
                .requires(Requires.hasPerms("factions.claim.access.increase", 0))
                .executes(ctx -> setAccessLevel(ctx, true)));
        access.then(CommandManager.literal("decrease")
                .requires(Requires.hasPerms("factions.claim.access.decrease", 0))
                .executes(ctx -> setAccessLevel(ctx, false)));
        root.then(access);

        return root.build();
    }
}
