package io.icker.factions.core;

import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Message;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class InteractionsUtil {
    public static void sync(PlayerEntity player, ItemStack itemStack, Hand hand) {
        player.setStackInHand(hand, itemStack);
        itemStack.setCount(itemStack.getCount());
        if (itemStack.isDamageable()) {
            itemStack.setDamage(itemStack.getDamage());
        }

        if (!player.isUsingItem()) {
            player.playerScreenHandler.syncState();
        }
    }

    public static void warn(PlayerEntity player, InteractionsUtilActions action) {
        SoundManager.warningSound(player);
        User user = User.get(player.getUuid());
        new Message("You can't %s here.", action.getWarningText()).fail().send(player, !user.radar);
    }

    public enum InteractionsUtilActions {
        BREAK_BLOCKS("break THIS block"),
        USE_BLOCKS("use THIS block"),
        PLACE_BLOCKS("place THIS block"),
        PLACE_OR_PICKUP_LIQUIDS("place or pick up THIS liquid"),
        ATTACK_ENTITIES("attack THIS entity"),
        USE_ENTITIES("interact with THIS entity"),
        USE_INVENTORY("open THIS inventory");

        private final String warningText;

        InteractionsUtilActions(String warningText) {
            this.warningText = warningText;
        }

        public String getWarningText() {
            return warningText;
        }
    }
}
