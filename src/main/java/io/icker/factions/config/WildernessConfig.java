package io.icker.factions.config;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class WildernessConfig {
    @SerializedName("breakWhitelist")
    public List<String> BREAK_WHITELIST = List.of();

    @SerializedName("placeWhitelist")
    public List<String> PLACE_WHITELIST = List.of();

    @SerializedName("allowInteractions")
    public boolean ALLOW_INTERACTIONS = true;

    @SerializedName("allowWaterPlacement")
    public boolean ALLOW_WATER_PLACEMENT = true;

    @SerializedName("allowLavaPlacement")
    public boolean ALLOW_LAVA_PLACEMENT = false;
}
