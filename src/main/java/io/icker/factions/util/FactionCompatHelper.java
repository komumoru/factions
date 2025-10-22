package io.icker.factions.util;

import io.icker.factions.api.compat.compatSkillDamageProtectionfor;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Relationship;
import io.icker.factions.api.persistents.User;
import net.minecraft.server.network.ServerPlayerEntity;

public class FactionCompatHelper {

    public static Faction getFaction(ServerPlayerEntity player) {
        User user = User.get(player.getUuid());
        return user != null ? user.getFaction() : null;
    }

    // public static boolean areFactionsEnemies(ServerPlayerEntity p1, ServerPlayerEntity p2) {
    //     Faction f1 = getFaction(p1);
    //     Faction f2 = getFaction(p2);

    //     if (f1 == null || f2 == null || f1.getID().equals(f2.getID())) return false;

    //     Relationship r1 = f1.getRelationship(f2.getID());
    //     Relationship r2 = f2.getRelationship(f1.getID());

    //     return (r1 != null && r1.status == Relationship.Status.ENEMY)
    //         || (r2 != null && r2.status == Relationship.Status.ENEMY);
    // }

    // public static boolean areFactionsAllies(ServerPlayerEntity p1, ServerPlayerEntity p2) {
    // Faction f1 = getFaction(p1);
    // Faction f2 = getFaction(p2);

    // if (f1 == null || f2 == null || f1.getID().equals(f2.getID())) return false;

    // Relationship r1 = f1.getRelationship(f2.getID());
    // Relationship r2 = f2.getRelationship(f1.getID());

    // return (r1 != null && r1.status == Relationship.Status.ALLY)
    //     || (r2 != null && r2.status == Relationship.Status.ALLY);
    // }

    // public static boolean areFactionsNeutral(ServerPlayerEntity p1, ServerPlayerEntity p2) {
    //     Faction f1 = getFaction(p1);
    //     Faction f2 = getFaction(p2);

    //     if (f1 == null || f2 == null || f1.getID().equals(f2.getID())) return false;

    //     Relationship r1 = f1.getRelationship(f2.getID());
    //     Relationship r2 = f2.getRelationship(f1.getID());

    //     return (r1 != null && r1.status == Relationship.Status.NEUTRAL)
    //         || (r2 != null && r2.status == Relationship.Status.NEUTRAL);
    // }

    public static boolean canDamage(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        compatSkillDamageProtectionfor level = io.icker.factions.FactionsMod.CONFIG.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR;
        return canDamage(attacker, target, level);
    }

    public static boolean canDamage(ServerPlayerEntity attacker, ServerPlayerEntity target, compatSkillDamageProtectionfor level) {
        Faction f1 = FactionCompatHelper.getFaction(attacker);
        Faction f2 = FactionCompatHelper.getFaction(target);

        // Same faction members cannot damage each other
        if (f1 != null && f2 != null && f1.getID().equals(f2.getID())) {
            return false;
        }

        // If either player is factionless, treat as neutral relationship
        if (f1 == null || f2 == null) {
            return isDamageAllowed(Relationship.Status.NEUTRAL, level);
        }

        Relationship r1 = f1.getRelationship(f2.getID());
        Relationship r2 = f2.getRelationship(f1.getID());

        Relationship.Status relationStatus = getWorstRelation(r1, r2);

        return isDamageAllowed(relationStatus, level);
    }

    private static Relationship.Status getWorstRelation(Relationship r1, Relationship r2) {
        boolean r1Enemy = r1 != null && r1.status == Relationship.Status.WAR;
        boolean r2Enemy = r2 != null && r2.status == Relationship.Status.WAR;

        if (r1Enemy || r2Enemy) return Relationship.Status.WAR;

        boolean r1Neutral = r1 == null || r1.status == Relationship.Status.NEUTRAL;
        boolean r2Neutral = r2 == null || r2.status == Relationship.Status.NEUTRAL;

        if (r1Neutral || r2Neutral) return Relationship.Status.NEUTRAL;

        boolean r1Friendly = r1 != null && r1.status == Relationship.Status.FRIENDLY;
        boolean r2Friendly = r2 != null && r2.status == Relationship.Status.FRIENDLY;

        if (r1Friendly || r2Friendly) return Relationship.Status.FRIENDLY;

        return Relationship.Status.ALLY;
    }

    private static boolean isDamageAllowed(Relationship.Status actualStatus, compatSkillDamageProtectionfor configLevel) {
        return switch (configLevel) {
            case ALL -> true; // No protection - all damage allowed
            case ALLY -> actualStatus != Relationship.Status.ALLY; // Allies protected
            case FRIENDLY -> actualStatus != Relationship.Status.ALLY && 
                                actualStatus != Relationship.Status.FRIENDLY; // Friendly+ protected
            case NEUTRAL -> actualStatus == Relationship.Status.WAR; // Only enemies can fight
            case NONE -> false; // No damage allowed
        };
    }
}
