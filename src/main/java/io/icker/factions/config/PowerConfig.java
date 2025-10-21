package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class PowerConfig {
    @SerializedName("playerMax")
    public int PLAYER_MAX = 20;

    @SerializedName("playerStart")
    public int PLAYER_START = 10;

    @SerializedName("deathPenalty")
    public int DEATH_PENALTY = 10;

    @SerializedName("killReward")
    public int KILL_REWARD = 2;

    @SerializedName("powerTicks")
    public PowerTicks POWER_TICKS = new PowerTicks();

    public static class PowerTicks {
        @SerializedName("ticks")
        public int TICKS = 12000;

        @SerializedName("reward")
        public int REWARD = 1;
    }
}
