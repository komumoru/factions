package io.icker.factions.api.compat;

public enum compatSkillDamageProtectionfor {
    NONE,      // No protection - everyone can damage everyone
    ALLY,      // Only allies are protected from damage
    FRIENDLY,  // Friendly and allies are protected from damage  
    NEUTRAL,   // Neutral, friendly, and allies are protected (only enemies can fight)
    ALL        // Everyone is protected - no PvP allowed
}