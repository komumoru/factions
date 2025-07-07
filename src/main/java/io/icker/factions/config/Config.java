package io.icker.factions.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.compat.compatRelationshipCheckLevel;
import io.icker.factions.api.compat.compatRelationshipCheckType;
import io.icker.factions.api.persistents.Relationship;
import net.fabricmc.loader.api.FabricLoader;

public class Config {
    private static final int REQUIRED_VERSION = 3;
    private static final File file = FabricLoader.getInstance().getGameDir().resolve("config")
            .resolve("factions.json").toFile();

    public static Config load() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
                .registerTypeAdapter(HomeConfig.class, new Deserializer<>(HomeConfig.class))
                .registerTypeAdapter(PowerConfig.class, new Deserializer<>(PowerConfig.class))
                .registerTypeAdapter(SafeConfig.class, new Deserializer<>(SafeConfig.class))
                .create();

        try {
            if (!file.exists()) {
                file.getParentFile().mkdir();

                Config defaults = new Config();

                FileWriter writer = new FileWriter(file);
                gson.toJson(defaults, writer);
                writer.close();

                return defaults;
            }

            Config config = gson.fromJson(new FileReader(file), Config.class);
            Config defaults = new Config();

            if (config.RELATIONSHIPS == null) {
                config.RELATIONSHIPS = defaults.RELATIONSHIPS;
            } else {
                if (config.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_LEVEL == null) {
                    config.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_LEVEL = defaults.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_LEVEL;
                }
                if (config.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_TYPE == null) {
                    config.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_TYPE = defaults.RELATIONSHIPS.COMPAT_RELATIONSHIP_CHECK_TYPE;
                }
            }

            if (config.DISPLAY == null) {
                config.DISPLAY = defaults.DISPLAY;
            }

            if (config.SAFE == null) {
                config.SAFE = defaults.SAFE;
            }

            if (config.HOME == null) {
                config.HOME = defaults.HOME;
            }

            if (config.VERSION != REQUIRED_VERSION) {
                FactionsMod.LOGGER.error(String.format(
                        "Config file incompatible (requires version %d)", REQUIRED_VERSION));
            }

            FileWriter writer = new FileWriter(file);
            gson.toJson(config, writer);
            writer.close();

            return config;

        } catch (Exception e) {
            FactionsMod.LOGGER.error("An error occurred reading the factions config file", e);
            return new Config();
        }
    }

    public static void save(Config config) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
                    .registerTypeAdapter(HomeConfig.class, new Deserializer<>(HomeConfig.class))
                    .registerTypeAdapter(PowerConfig.class, new Deserializer<>(PowerConfig.class))
                    .registerTypeAdapter(SafeConfig.class, new Deserializer<>(SafeConfig.class))
                    .create();

            FileWriter writer = new FileWriter(file);
            gson.toJson(config, writer);
            writer.close();

        } catch (Exception e) {
            FactionsMod.LOGGER.error("An error occurred saving the factions config file", e);
        }
    }

    public void save() {
        save(this);
    }

    @SerializedName("version")
    public int VERSION = REQUIRED_VERSION;

    @SerializedName("blockTNT")
    public boolean BLOCK_TNT = false;

    @SerializedName("power")
    public PowerConfig POWER = new PowerConfig();

    @SerializedName("safe")
    @Nullable
    public SafeConfig SAFE = new SafeConfig();

    @SerializedName("home")
    @Nullable
    public HomeConfig HOME = new HomeConfig();

    @SerializedName("display")
    public DisplayConfig DISPLAY = new DisplayConfig();

    @SerializedName("relationships")
    public RelationshipConfig RELATIONSHIPS = new RelationshipConfig();

    @SerializedName("maxFactionSize")
    public int MAX_FACTION_SIZE = -1;

    @SerializedName("friendlyFire")
    public boolean FRIENDLY_FIRE = false;

    @SerializedName("requiredBypassLevel")
    public int REQUIRED_BYPASS_LEVEL = 2;

    @SerializedName("claimProtections")
    public boolean CLAIM_PROTECTION = true;

    public static class DisplayConfig {
        @SerializedName("factionNameMaxLength")
        public int NAME_MAX_LENGTH = -1;

        @SerializedName("changeChat")
        public boolean MODIFY_CHAT = true;

        @SerializedName("tabMenu")
        public boolean TAB_MENU = true;

        @SerializedName("nameBlackList")
        public List<String> NAME_BLACKLIST = List.of("wilderness", "factionless");

        @SerializedName("powerMessage")
        public boolean POWER_MESSAGE = true;
    }

    public static class RelationshipConfig {
        @SerializedName("allyOverridesPermissions")
        public boolean ALLY_OVERRIDES_PERMISSIONS = true;

        @SerializedName("defaultGuestPermissions")
        public List<Relationship.Permissions> DEFAULT_GUEST_PERMISSIONS =
                List.of(Relationship.Permissions.USE_BLOCKS, Relationship.Permissions.USE_ENTITIES);

        @SerializedName("compatRelationshipCheckLevel")
        public compatRelationshipCheckLevel COMPAT_RELATIONSHIP_CHECK_LEVEL = compatRelationshipCheckLevel.ENEMY;

        @SerializedName("compatRelationshipCheckType")
        public compatRelationshipCheckType COMPAT_RELATIONSHIP_CHECK_TYPE = compatRelationshipCheckType.ONE_SIDED;
    }

    public static class Deserializer<T> implements JsonDeserializer<T> {
        final Class<T> clazz;

        public Deserializer(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject() && !json.getAsBoolean()) {
                return null;
            }

            return new Gson().fromJson(json, clazz);
        }
    }
}
