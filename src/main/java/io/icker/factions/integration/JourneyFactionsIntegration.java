package io.icker.factions.integration;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.core.FactionsManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles network communication to JourneyFactions client mod
 */
public class JourneyFactionsIntegration {
    
    // Packet identifiers - must match client-side JourneyFactions mod
    public static final Identifier FACTION_DATA_SYNC = new Identifier("factions", "faction_data_sync");
    public static final Identifier FACTION_UPDATE = new Identifier("factions", "faction_update");
    public static final Identifier CHUNK_CLAIM = new Identifier("factions", "chunk_claim");
    public static final Identifier CHUNK_UNCLAIM = new Identifier("factions", "chunk_unclaim");
    public static final Identifier FACTION_DELETE = new Identifier("factions", "faction_delete");
    public static final Identifier CLIENT_REQUEST_DATA = new Identifier("factions", "client_request_data");

    public static void initialize() {
        FactionsMod.LOGGER.info("Initializing JourneyFactions integration...");

        // Handle client requests for faction data
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_REQUEST_DATA, (server, player, handler, buf, responseSender) -> {
            FactionsMod.LOGGER.info("Player {} requested faction data for JourneyMap", player.getName().getString());
            
            server.execute(() -> {
                sendFactionDataToPlayer(player);
            });
        });

        // Send faction data when players join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Small delay to ensure client is ready
            server.execute(() -> {
                sendFactionDataToPlayer(handler.player);
            });
        });

        // Register event listeners for faction changes
        JourneyFactionsEventListeners.register();

        FactionsMod.LOGGER.info("JourneyFactions integration initialized successfully");
    }

    /**
     * Send all faction data to a player
     */
    public static void sendFactionDataToPlayer(ServerPlayerEntity player) {
        try {
            FactionsMod.LOGGER.debug("Sending faction data to player: {}", player.getName().getString());
            
            PacketByteBuf buf = PacketByteBufs.create();
            
            // Get all factions
            Collection<Faction> allFactions = Faction.all();
            
            buf.writeVarInt(allFactions.size());
            FactionsMod.LOGGER.debug("Sending {} factions to {}", allFactions.size(), player.getName().getString());
            
            for (Faction faction : allFactions) {
                writeFactionToBuffer(buf, faction);
            }
            
            ServerPlayNetworking.send(player, FACTION_DATA_SYNC, buf);
            FactionsMod.LOGGER.debug("Faction data sent successfully to {}", player.getName().getString());
            
        } catch (Exception e) {
            FactionsMod.LOGGER.error("Error sending faction data to player: " + player.getName().getString(), e);
        }
    }

    /**
     * Broadcast faction update to all players
     */
    public static void broadcastFactionUpdate(Faction faction) {
        try {
            FactionsMod.LOGGER.debug("Broadcasting faction update: {}", faction.getName());
            
            PacketByteBuf buf = PacketByteBufs.create();
            writeFactionToBuffer(buf, faction);
            
            // Send to all online players
            for (ServerPlayerEntity player : FactionsManager.playerManager.getPlayerList()) {
                ServerPlayNetworking.send(player, FACTION_UPDATE, buf);
            }
            
        } catch (Exception e) {
            FactionsMod.LOGGER.error("Error broadcasting faction update for: " + faction.getName(), e);
        }
    }

    /**
     * Broadcast chunk claim to all players
     */
    public static void broadcastChunkClaim(ChunkPos chunk, Faction faction) {
        try {
            FactionsMod.LOGGER.debug("Broadcasting chunk claim: {} by {}", chunk, faction.getName());
            
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(faction.getID().toString()); // Use faction ID
            buf.writeInt(chunk.x);
            buf.writeInt(chunk.z);
            
            // Send to all online players
            for (ServerPlayerEntity player : FactionsManager.playerManager.getPlayerList()) {
                ServerPlayNetworking.send(player, CHUNK_CLAIM, buf);
            }
            
        } catch (Exception e) {
            FactionsMod.LOGGER.error("Error broadcasting chunk claim", e);
        }
    }

    /**
     * Broadcast chunk unclaim to all players
     */
    public static void broadcastChunkUnclaim(ChunkPos chunk) {
        try {
            FactionsMod.LOGGER.debug("Broadcasting chunk unclaim: {}", chunk);
            
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(chunk.x);
            buf.writeInt(chunk.z);
            
            // Send to all online players  
            for (ServerPlayerEntity player : FactionsManager.playerManager.getPlayerList()) {
                ServerPlayNetworking.send(player, CHUNK_UNCLAIM, buf);
            }
            
        } catch (Exception e) {
            FactionsMod.LOGGER.error("Error broadcasting chunk unclaim", e);
        }
    }

    /**
     * Broadcast faction deletion to all players
     */
    public static void broadcastFactionDeletion(Faction faction) {
        try {
            FactionsMod.LOGGER.debug("Broadcasting faction deletion: {}", faction.getName());
            
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(faction.getID().toString());
            
            // Send to all online players
            for (ServerPlayerEntity player : FactionsManager.playerManager.getPlayerList()) {
                ServerPlayNetworking.send(player, FACTION_DELETE, buf);
            }
            
        } catch (Exception e) {
            FactionsMod.LOGGER.error("Error broadcasting faction deletion", e);
        }
    }

    /**
     * Write faction data to packet buffer
     */
    private static void writeFactionToBuffer(PacketByteBuf buf, Faction faction) {
        try {
            buf.writeString(faction.getID().toString());           // Faction ID (UUID as string)
            buf.writeString(faction.getName());                    // Faction name  
            buf.writeString(getFormattedName(faction));            // Display name with color
            
            // Faction type - convert to ordinal for client
            int typeOrdinal = getFactionTypeOrdinal(faction);
            buf.writeVarInt(typeOrdinal);
            
            // Color - extract from faction
            Color factionColor = getFactionColor(faction);
            if (factionColor != null) {
                buf.writeBoolean(true);
                buf.writeInt(factionColor.getRGB());
            } else {
                buf.writeBoolean(false);
            }
            
            // Claimed chunks - convert from List<Claim> to Set<ChunkPos>
            Set<ChunkPos> chunks = getChunkPosFromClaims(faction);
            buf.writeVarInt(chunks.size());
            for (ChunkPos chunk : chunks) {
                buf.writeInt(chunk.x);
                buf.writeInt(chunk.z);
            }
            
        } catch (Exception e) {
            FactionsMod.LOGGER.error("Error writing faction to buffer: " + faction.getName(), e);
            throw e;
        }
    }

    /**
     * Get formatted faction name with color
     */
    private static String getFormattedName(Faction faction) {
        if (faction.getColor() != null) {
            return faction.getColor() + faction.getName();
        }
        return faction.getName();
    }

    /**
     * Convert faction claims to ChunkPos set
     */
    private static Set<ChunkPos> getChunkPosFromClaims(Faction faction) {
        Set<ChunkPos> chunkPosSet = new HashSet<>();
        
        try {
            // Get the claims list and convert to ChunkPos
            List<Claim> claims = faction.getClaims();
            for (Claim claim : claims) {
                // Use the public x and z fields directly
                chunkPosSet.add(new ChunkPos(claim.x, claim.z));
            }
        } catch (Exception e) {
            FactionsMod.LOGGER.error("Error converting faction claims to ChunkPos", e);
        }
        
        return chunkPosSet;
    }

    /**
     * Convert faction to client faction type ordinal
     */
    private static int getFactionTypeOrdinal(Faction faction) {
        String factionName = faction.getName().toLowerCase();
        
        if (factionName.equals("wilderness")) {
            return 1; // WILDERNESS
        } else if (factionName.equals("safezone")) {
            return 2; // SAFEZONE
        } else if (factionName.equals("warzone")) {
            return 3; // WARZONE
        } else {
            return 0; // PLAYER (default)
        }
    }

    /**
     * Extract color from faction
     */
    private static Color getFactionColor(Faction faction) {
        try {
            // Check if faction has specific colors based on type
            String factionName = faction.getName().toLowerCase();
            if (factionName.equals("wilderness")) {
                return new Color(100, 100, 100); // Gray
            } else if (factionName.equals("safezone")) {
                return new Color(0, 255, 0); // Green
            } else if (factionName.equals("warzone")) {
                return new Color(255, 0, 0); // Red
            }
            
            // For player factions, get color from faction.getColor()
            if (faction.getColor() != null) {
                return parseMinecraftFormattingColor(faction.getColor());
            }
            
            // Fallback: generate color from faction name hash
            int hash = faction.getName().hashCode();
            float hue = Math.abs(hash % 360) / 360.0f;
            return Color.getHSBColor(hue, 0.7f, 0.9f);
            
        } catch (Exception e) {
            FactionsMod.LOGGER.debug("Could not extract color for faction {}, using fallback", faction.getName());
            // Fallback color
            int hash = faction.getName().hashCode();
            float hue = Math.abs(hash % 360) / 360.0f;
            return Color.getHSBColor(hue, 0.7f, 0.9f);
        }
    }

    /**
     * Parse Minecraft Formatting enum to Java Color
     */
    private static Color parseMinecraftFormattingColor(net.minecraft.util.Formatting formatting) {
        switch (formatting) {
            case BLACK: return new Color(0, 0, 0);
            case DARK_BLUE: return new Color(0, 0, 170);
            case DARK_GREEN: return new Color(0, 170, 0);
            case DARK_AQUA: return new Color(0, 170, 170);
            case DARK_RED: return new Color(170, 0, 0);
            case DARK_PURPLE: return new Color(170, 0, 170);
            case GOLD: return new Color(255, 170, 0);
            case GRAY: return new Color(170, 170, 170);
            case DARK_GRAY: return new Color(85, 85, 85);
            case BLUE: return new Color(85, 85, 255);
            case GREEN: return new Color(85, 255, 85);
            case AQUA: return new Color(85, 255, 255);
            case RED: return new Color(255, 85, 85);
            case LIGHT_PURPLE: return new Color(255, 85, 255);
            case YELLOW: return new Color(255, 255, 85);
            case WHITE: return new Color(255, 255, 255);
            default:
                // Fallback for non-color formatting codes
                return new Color(255, 255, 255);
        }
    }
}