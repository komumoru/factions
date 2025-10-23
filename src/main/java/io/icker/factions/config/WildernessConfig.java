package io.icker.factions.config;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class WildernessConfig {
    @SerializedName("breakBlacklist")
    public List<String> BREAK_BLACKLIST = List.of();

    @SerializedName("breakModBlacklist")
    public List<String> BREAK_MOD_BLACKLIST = List.of();

    @SerializedName("placeBlacklist")
    public List<String> PLACE_BLACKLIST = List.of();

    @SerializedName("placeModBlacklist")
    public List<String> PLACE_MOD_BLACKLIST = List.of();

    @SerializedName("allowInteractions")
    public boolean ALLOW_INTERACTIONS = true;

    @SerializedName("allowWaterPlacement")
    public boolean ALLOW_WATER_PLACEMENT = true;

    @SerializedName("allowLavaPlacement")
    public boolean ALLOW_LAVA_PLACEMENT = false;
}
