package io.icker.factions.integration;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.ClaimEvents;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.api.persistents.Faction;
import net.minecraft.util.math.ChunkPos;

/**
 * Event listeners to broadcast faction changes to JourneyFactions clients
 */
public class JourneyFactionsEventListeners {
    
    public static void register() {
        FactionsMod.LOGGER.info("Registering JourneyFactions event listeners...");

        // Listen for faction modifications (name, color, etc.)
        FactionEvents.MODIFY.register((faction) -> {
            FactionsMod.LOGGER.debug("Faction modified: {}, broadcasting update", faction.getName());
            JourneyFactionsIntegration.broadcastFactionUpdate(faction);
        });

        // Listen for faction creation
        FactionEvents.CREATE.register((faction, user) -> {
            FactionsMod.LOGGER.debug("Faction created: {}, broadcasting update", faction.getName());
            JourneyFactionsIntegration.broadcastFactionUpdate(faction);
        });

        // Listen for faction disband
        FactionEvents.DISBAND.register((faction) -> {
            FactionsMod.LOGGER.debug("Faction disbanded: {}, broadcasting deletion", faction.getName());
            JourneyFactionsIntegration.broadcastFactionDeletion(faction);
        });

        // Listen for chunk claims - using the ClaimEvents.ADD that receives a Claim object
        ClaimEvents.ADD.register((claim) -> {
            ChunkPos chunk = new ChunkPos(claim.x, claim.z);
            Faction faction = claim.getFaction();
            if (faction != null) {
                FactionsMod.LOGGER.debug("Chunk claimed: {} by {}, broadcasting", chunk, faction.getName());
                JourneyFactionsIntegration.broadcastChunkClaim(chunk, faction);
            }
        });

        // Listen for chunk unclaims - this receives x, z, level, faction parameters
        ClaimEvents.REMOVE.register((x, z, level, faction) -> {
            ChunkPos chunk = new ChunkPos(x, z);
            if (faction != null) {
                FactionsMod.LOGGER.debug("Chunk unclaimed: {} from {}, broadcasting", chunk, faction.getName());
            } else {
                FactionsMod.LOGGER.debug("Chunk unclaimed: {}, broadcasting", chunk);
            }
            JourneyFactionsIntegration.broadcastChunkUnclaim(chunk);
        });

        // Listen for member changes (affects faction power/display)
        FactionEvents.MEMBER_JOIN.register((faction, user) -> {
            FactionsMod.LOGGER.debug("Member joined faction: {}, broadcasting update", faction.getName());
            // Small delay to ensure member is properly added
            scheduleUpdate(faction);
        });

        FactionEvents.MEMBER_LEAVE.register((faction, user) -> {
            FactionsMod.LOGGER.debug("Member left faction: {}, broadcasting update", faction.getName());
            // Small delay to ensure member is properly removed
            scheduleUpdate(faction);
        });

        FactionsMod.LOGGER.info("JourneyFactions event listeners registered successfully");
    }

    /**
     * Schedule a delayed faction update to ensure data consistency
     */
    private static void scheduleUpdate(Faction faction) {
        // Use server scheduler to delay the update slightly
        new Thread(() -> {
            try {
                Thread.sleep(100); // 100ms delay
                JourneyFactionsIntegration.broadcastFactionUpdate(faction);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}