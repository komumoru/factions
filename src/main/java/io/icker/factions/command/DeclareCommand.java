package io.icker.factions.command;

import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.api.events.RelationshipEvents;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Relationship;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class DeclareCommand implements Command {
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000L;

    private static final HashMap<UUID, PendingWar> PENDING_WARS = new HashMap<>();
    private static final HashMap<String, PendingPeace> PENDING_PEACE = new HashMap<>();

    private record PendingWar(UUID factionId, UUID targetFactionId, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private record PendingPeace(UUID requesterFactionId, UUID targetFactionId, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private record ActionContext(ServerPlayerEntity player, Faction sourceFaction, Faction targetFaction,
            Relationship sourceRel, Relationship targetRel, Relationship.Status priorMutual) {
    }

    private int ally(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ActionContext ctx = getContext(context);
        if (ctx == null)
            return 0;
        return requestStatus(ctx, Relationship.Status.ALLY);
    }

    private int friendly(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ActionContext ctx = getContext(context);
        if (ctx == null)
            return 0;
        return requestStatus(ctx, Relationship.Status.FRIENDLY);
    }

    private int neutral(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ActionContext ctx = getContext(context);
        if (ctx == null)
            return 0;
        return attemptNeutral(ctx);
    }

    private int declinePeace(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ActionContext ctx = getContext(context);
        if (ctx == null)
            return 0;
        return rejectPeace(ctx);
    }

    private int war(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ActionContext ctx = getContext(context);
        if (ctx == null)
            return 0;
        return queueWarConfirmation(ctx);
    }

    private int confirmWar(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ActionContext ctx = getContext(context);
        if (ctx == null)
            return 0;
        return finalizeWar(ctx);
    }

    private ActionContext getContext(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        Faction sourceFaction = Command.getUser(player).getFaction();
        if (sourceFaction == null)
            return null;

        String name = StringArgumentType.getString(context, "faction");
        Faction targetFaction = Faction.getByName(name);

        if (targetFaction == null) {
            new Message("Cannot change faction relationship with a faction that doesn't exist").fail()
                    .send(player, false);
            return null;
        }

        if (sourceFaction.equals(targetFaction)) {
            new Message("Cannot use the declare command on your own faction").fail().send(player, false);
            return null;
        }

        Relationship sourceRel = sourceFaction.getRelationship(targetFaction.getID());
        Relationship targetRel = targetFaction.getRelationship(sourceFaction.getID());
        Relationship.Status priorMutual = sourceRel.status == targetRel.status ? sourceRel.status : null;

        return new ActionContext(player, sourceFaction, targetFaction, sourceRel, targetRel, priorMutual);
    }

    private int requestStatus(ActionContext ctx, Relationship.Status status) {
        if (ctx.sourceRel.status == status && ctx.targetRel.status == status) {
            new Message("That faction relationship has already been declared mutually").fail()
                    .send(ctx.player, false);
            return 0;
        }

        if (ctx.sourceRel.status == status && ctx.targetRel.status != status) {
            new Message("You have already requested this relationship change with %s", ctx.targetFaction.getName())
                    .fail().send(ctx.player, false);
            return 0;
        }

        if (ctx.sourceRel.status == Relationship.Status.WAR || ctx.targetRel.status == Relationship.Status.WAR
                || ctx.sourceFaction.isMutualWar(ctx.targetFaction.getID())) {
            new Message("You are at war with %s. Declare peace before changing relations.",
                    ctx.targetFaction.getName()).fail().send(ctx.player, false);
            return 0;
        }

        Relationship rel = new Relationship(ctx.targetFaction.getID(), status);
        ctx.sourceFaction.setRelationship(rel);
        RelationshipEvents.NEW_DECLARATION.invoker().onNewDecleration(rel);

        Relationship reverse = ctx.targetFaction.getRelationship(ctx.sourceFaction.getID());
        if (reverse.status == status) {
            RelationshipEvents.NEW_MUTUAL.invoker().onNewMutual(rel);
            sendMutualMessage(ctx, status);
            return 1;
        }

        if (ctx.priorMutual != null && ctx.priorMutual != status) {
            RelationshipEvents.END_MUTUAL.invoker().onEndMutual(rel, ctx.priorMutual);
        }

        sendRequestMessages(ctx, status);
        return 1;
    }

    private void sendMutualMessage(ActionContext ctx, Relationship.Status status) {
        Message descriptor = switch (status) {
            case ALLY -> new Message("allies").format(Formatting.GREEN);
            case FRIENDLY -> new Message("friendly").format(Formatting.AQUA);
            case NEUTRAL -> new Message("neutral");
            case WAR -> new Message("at war").format(Formatting.RED);
        };

        new Message("You are now mutually ").add(descriptor).add(" with " + ctx.targetFaction.getName())
                .send(ctx.sourceFaction);
        new Message("You are now mutually ").add(descriptor).add(" with " + ctx.sourceFaction.getName())
                .send(ctx.targetFaction);

        if (status == Relationship.Status.ALLY) {
            new Message("Allied factions now share their power bonus and can perform permitted actions on each other's land.")
                    .format(Formatting.GRAY).send(ctx.sourceFaction);
            new Message(
                    "Allied factions now share their power bonus and can perform permitted actions on each other's land.")
                    .format(Formatting.GRAY).send(ctx.targetFaction);
        }
    }

    private void sendRequestMessages(ActionContext ctx, Relationship.Status status) {
        String adjective = status == Relationship.Status.ALLY ? "an alliance" : "a friendship";
        Formatting color = status == Relationship.Status.ALLY ? Formatting.GREEN : Formatting.AQUA;

        new Message("You have requested %s with %s", adjective, ctx.targetFaction.getName()).format(color)
                .send(ctx.sourceFaction);

        Message request = new Message("%s requested %s.", ctx.sourceFaction.getName(), adjective).format(color);
        request.send(ctx.targetFaction);

        String statusName = status.toString().toLowerCase(Locale.ROOT);
        Message accept = new Message("[Accept]").format(Formatting.GREEN)
                .click(String.format("/factions declare %s %s", statusName, ctx.sourceFaction.getName()))
                .hover("Accept this request");
        Message decline = new Message("[Decline]").format(Formatting.RED)
                .click(String.format("/factions declare neutral %s", ctx.sourceFaction.getName()))
                .hover("Decline this request");

        new Message("Click ").format(Formatting.GRAY).add(accept).add(" or ").add(decline)
                .add(" to respond.").send(ctx.targetFaction);
    }

    private int attemptNeutral(ActionContext ctx) {
        PendingPeace pending = getPendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());

        if (pending != null && pending.requesterFactionId.equals(ctx.targetFaction.getID())) {
            return acceptPeace(ctx, pending);
        }

        if (ctx.sourceFaction.isMutualWar(ctx.targetFaction.getID())) {
            if (pending != null && pending.requesterFactionId.equals(ctx.sourceFaction.getID())) {
                new Message("You have already requested peace with %s", ctx.targetFaction.getName()).fail()
                        .send(ctx.player, false);
                return 0;
            }

            queuePeaceRequest(ctx);
            return 1;
        }

        if (ctx.sourceRel.status == Relationship.Status.NEUTRAL
                && ctx.targetRel.status == Relationship.Status.NEUTRAL) {
            new Message("You are already neutral with this faction").fail().send(ctx.player, false);
            return 0;
        }

        Relationship rel = new Relationship(ctx.targetFaction.getID(), Relationship.Status.NEUTRAL);
        ctx.sourceFaction.setRelationship(rel);
        RelationshipEvents.NEW_DECLARATION.invoker().onNewDecleration(rel);

        boolean targetDowngraded = ctx.targetRel.status == Relationship.Status.ALLY
                || ctx.targetRel.status == Relationship.Status.FRIENDLY;
        if (targetDowngraded) {
            Relationship reverse = new Relationship(ctx.sourceFaction.getID(), Relationship.Status.NEUTRAL);
            ctx.targetFaction.setRelationship(reverse);
            RelationshipEvents.NEW_DECLARATION.invoker().onNewDecleration(reverse);
        }

        if (ctx.priorMutual == Relationship.Status.ALLY || ctx.priorMutual == Relationship.Status.FRIENDLY) {
            RelationshipEvents.END_MUTUAL.invoker().onEndMutual(rel, ctx.priorMutual);
            Message notice = ctx.priorMutual == Relationship.Status.ALLY
                    ? new Message(ctx.sourceFaction.getName() + " broke the alliance.").format(Formatting.RED)
                    : new Message(ctx.sourceFaction.getName() + " ended the friendly relations.")
                            .format(Formatting.YELLOW);
            notice.send(ctx.targetFaction);
        } else if (targetDowngraded) {
            new Message(ctx.sourceFaction.getName() + " set your factions back to neutral.")
                    .format(Formatting.YELLOW).send(ctx.targetFaction);
        }

        new Message("You are now neutral with " + ctx.targetFaction.getName()).send(ctx.sourceFaction);
        return 1;
    }

    private void queuePeaceRequest(ActionContext ctx) {
        putPendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());

        new Message("Peace request sent to " + ctx.targetFaction.getName()).format(Formatting.YELLOW)
                .send(ctx.sourceFaction);

        Message request = new Message(ctx.sourceFaction.getName() + " is requesting peace.").format(Formatting.YELLOW);
        request.send(ctx.targetFaction);

        Message accept = new Message("[Accept Peace]").format(Formatting.GREEN)
                .click(String.format("/factions declare neutral %s", ctx.sourceFaction.getName()))
                .hover("Accept the peace offer");
        Message decline = new Message("[Stay at War]").format(Formatting.RED)
                .click(String.format("/factions declare neutral decline %s", ctx.sourceFaction.getName()))
                .hover("Reject this peace offer");

        new Message("Click ").format(Formatting.GRAY).add(accept).add(" or ").add(decline).add(" to respond.")
                .send(ctx.targetFaction);
    }

    private int rejectPeace(ActionContext ctx) {
        PendingPeace pending = getPendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());

        if (pending == null || !pending.requesterFactionId.equals(ctx.targetFaction.getID())) {
            new Message("There is no pending peace offer from %s", ctx.targetFaction.getName()).fail()
                    .send(ctx.player, false);
            return 0;
        }

        if (pending.isExpired()) {
            removePendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());
            new Message("The peace request from %s has already expired", ctx.targetFaction.getName()).fail()
                    .send(ctx.player, false);
            return 0;
        }

        removePendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());

        new Message("You rejected the peace request from " + ctx.targetFaction.getName()).format(Formatting.RED)
                .send(ctx.sourceFaction);
        new Message(ctx.sourceFaction.getName() + " rejected your peace offer. The war continues.")
                .format(Formatting.RED).send(ctx.targetFaction);
        return 1;
    }

    private int acceptPeace(ActionContext ctx, PendingPeace pending) {
        if (pending.isExpired()) {
            removePendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());
            new Message("The peace request expired").fail().send(ctx.player, false);
            return 0;
        }

        if (!ctx.sourceFaction.isMutualWar(ctx.targetFaction.getID())) {
            removePendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());
            new Message("You are no longer at war with this faction").fail().send(ctx.player, false);
            return 0;
        }

        removePendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());

        Relationship rel = new Relationship(ctx.targetFaction.getID(), Relationship.Status.NEUTRAL);
        Relationship rev = new Relationship(ctx.sourceFaction.getID(), Relationship.Status.NEUTRAL);

        ctx.sourceFaction.setRelationship(rel);
        ctx.targetFaction.setRelationship(rev);

        RelationshipEvents.NEW_DECLARATION.invoker().onNewDecleration(rel);
        RelationshipEvents.NEW_DECLARATION.invoker().onNewDecleration(rev);
        RelationshipEvents.END_MUTUAL.invoker().onEndMutual(rel, Relationship.Status.WAR);

        new Message("Peace with " + ctx.targetFaction.getName() + " has been accepted. You are now neutral.")
                .format(Formatting.GREEN).send(ctx.sourceFaction);
        new Message(ctx.sourceFaction.getName() + " accepted your peace offer. The war has ended.")
                .format(Formatting.GREEN).send(ctx.targetFaction);
        return 1;
    }

    private int queueWarConfirmation(ActionContext ctx) {
        if (ctx.sourceFaction.isMutualWar(ctx.targetFaction.getID())) {
            new Message("You are already mutually at war with this faction").fail().send(ctx.player, false);
            return 0;
        }

        UUID playerId = ctx.player.getUuid();
        PENDING_WARS.put(playerId,
                new PendingWar(ctx.sourceFaction.getID(), ctx.targetFaction.getID(),
                        System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS));

        new Message("⚠ Declaring war on " + ctx.targetFaction.getName() + " is irreversible without their consent.")
                .format(Formatting.RED).send(ctx.player, false);
        new Message("Click ").format(Formatting.GRAY)
                .add(new Message("[Confirm War]").format(Formatting.RED)
                        .click(String.format("/factions declare war confirm %s", ctx.targetFaction.getName()))
                        .hover("Confirm declaring war"))
                .add(" to proceed.").send(ctx.player, false);

        return 1;
    }

    private int finalizeWar(ActionContext ctx) {
        PendingWar pending = PENDING_WARS.remove(ctx.player.getUuid());

        if (pending == null || pending.isExpired()) {
            new Message("War declaration confirmation expired, run the command again").fail()
                    .send(ctx.player, false);
            return 0;
        }

        if (!pending.factionId.equals(ctx.sourceFaction.getID())
                || !pending.targetFactionId.equals(ctx.targetFaction.getID())) {
            new Message("You no longer have a pending war declaration for this faction").fail()
                    .send(ctx.player, false);
            return 0;
        }

        if (ctx.sourceFaction.isMutualWar(ctx.targetFaction.getID())) {
            new Message("You are already mutually at war with this faction").fail().send(ctx.player, false);
            return 0;
        }

        removePendingPeace(ctx.sourceFaction.getID(), ctx.targetFaction.getID());

        Relationship.Status priorMutual = ctx.priorMutual;

        Relationship rel = new Relationship(ctx.targetFaction.getID(), Relationship.Status.WAR);
        Relationship rev = new Relationship(ctx.sourceFaction.getID(), Relationship.Status.WAR);
        ctx.sourceFaction.setRelationship(rel);
        ctx.targetFaction.setRelationship(rev);

        RelationshipEvents.NEW_DECLARATION.invoker().onNewDecleration(rel);
        RelationshipEvents.NEW_DECLARATION.invoker().onNewDecleration(rev);
        RelationshipEvents.NEW_MUTUAL.invoker().onNewMutual(rel);

        if (priorMutual == Relationship.Status.ALLY || priorMutual == Relationship.Status.FRIENDLY) {
            RelationshipEvents.END_MUTUAL.invoker().onEndMutual(rel, priorMutual);
        }

        Message warMessage = new Message("War declared on " + ctx.targetFaction.getName() + "!")
                .format(Formatting.DARK_RED);
        warMessage.send(ctx.sourceFaction);
        Message retaliation = new Message(ctx.sourceFaction.getName() + " has declared war on your faction!")
                .format(Formatting.DARK_RED);
        retaliation.send(ctx.targetFaction);

        Message warning = new Message(
                "PvP is now enabled between both factions. If your power falls below demesne, unsupported lands can be captured even without being connected.")
                        .format(Formatting.GRAY);
        warning.send(ctx.sourceFaction);
        warning.send(ctx.targetFaction);

        return 1;
    }

    private static String peaceKey(UUID a, UUID b) {
        String left = a.toString();
        String right = b.toString();
        if (left.compareTo(right) < 0) {
            return left + ":" + right;
        }
        return right + ":" + left;
    }

    private PendingPeace getPendingPeace(UUID a, UUID b) {
        String key = peaceKey(a, b);
        PendingPeace pending = PENDING_PEACE.get(key);
        if (pending != null && pending.isExpired()) {
            PENDING_PEACE.remove(key);
            pending = null;
        }
        return pending;
    }

    private void putPendingPeace(UUID requester, UUID target) {
        PENDING_PEACE.put(peaceKey(requester, target),
                new PendingPeace(requester, target, System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS));
    }

    private void removePendingPeace(UUID a, UUID b) {
        PENDING_PEACE.remove(peaceKey(a, b));
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("declare").requires(Requires.isLeader())
                .then(CommandManager.literal("ally")
                        .requires(Requires.hasPerms("factions.declare.ally", 0))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(Suggests.allFactions(false)).executes(this::ally)))
                .then(CommandManager.literal("friendly")
                        .requires(Requires.hasPerms("factions.declare.friendly", 0))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(Suggests.allFactions(false)).executes(this::friendly)))
                .then(CommandManager.literal("neutral")
                        .requires(Requires.hasPerms("factions.declare.neutral", 0))
                        .then(CommandManager.literal("decline")
                                .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                        .suggests(Suggests.allFactions(false)).executes(this::declinePeace)))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(Suggests.allFactions(false)).executes(this::neutral)))
                .then(CommandManager.literal("war")
                        .requires(Requires.hasPerms("factions.declare.war", 0))
                        .then(CommandManager.literal("confirm")
                                .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                        .suggests(Suggests.allFactions(false)).executes(this::confirmWar)))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(Suggests.allFactions(false)).executes(this::war)))
                .build();
    }
}

