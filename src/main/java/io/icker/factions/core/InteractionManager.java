package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Relationship.Permissions;
import io.icker.factions.api.persistents.User;
import io.icker.factions.core.InteractionsUtil.InteractionsUtilActions;
import io.icker.factions.mixin.BucketItemAccessor;
import io.icker.factions.mixin.ItemInvoker;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
//import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
//import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;

public class InteractionManager {
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register(InteractionManager::onBreakBlock);
        // PlayerEvents.EXPLODE_BLOCK.register(InteractionManager::onExplodeBlock);
        // PlayerEvents.EXPLODE_DAMAGE.register(InteractionManager::onExplodeDamage);
        UseBlockCallback.EVENT.register(InteractionManager::onUseBlock);
        UseItemCallback.EVENT.register(InteractionManager::onUseBucket);
        AttackEntityCallback.EVENT.register(InteractionManager::onAttackEntity);
        PlayerEvents.IS_INVULNERABLE.register(InteractionManager::isInvulnerableTo);
        PlayerEvents.USE_ENTITY.register(InteractionManager::onUseEntity);
        PlayerEvents.USE_INVENTORY.register(InteractionManager::onUseInventory);
        PlayerEvents.PLACE_BLOCK.register(InteractionManager::onPlaceBlock);
    }

    private static boolean onBreakBlock(World world, PlayerEntity player, BlockPos pos,
            BlockState state, BlockEntity blockEntity) {
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        boolean result = checkPermissions(player, pos, world, Permissions.BREAK_BLOCKS, blockId)
                == ActionResult.FAIL;
        if (result) {
            InteractionsUtil.warn(player, InteractionsUtilActions.BREAK_BLOCKS);
        }
        return !result;
    }

    // private static ActionResult onExplodeBlock(Explosion explosion, BlockView world, BlockPos pos, BlockState state) {
    //     Entity causingEntity = explosion.getCausingEntity();
    //     World actualWorld = causingEntity != null ? causingEntity.getWorld() : null;
        
    //     if (explosion.getCausingEntity() != null && explosion.getCausingEntity() instanceof PlayerEntity) {
    //         ActionResult result =
    //                 checkPermissions((PlayerEntity) explosion.getCausingEntity(), pos, actualWorld, Permissions.BREAK_BLOCKS);
    //         if (result == ActionResult.FAIL) {
    //             InteractionsUtil.warn((PlayerEntity) explosion.getCausingEntity(), InteractionsUtilActions.BREAK_BLOCKS);
    //         }
    //         return result;
    //     } else {
    //         if (!FactionsMod.CONFIG.BLOCK_TNT) return ActionResult.PASS;

    //         String dimension = actualWorld.getRegistryKey().getValue().toString();
    //         ChunkPos chunkPosition = actualWorld.getChunk(pos).getPos();

    //         Claim claim = Claim.get(chunkPosition.x, chunkPosition.z, dimension);
    //         if (claim == null) return ActionResult.PASS;

    //         Faction claimFaction = claim.getFaction();

    //         if (claimFaction.getClaims().size() * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT
    //                 > claimFaction.getPower()) {
    //             return ActionResult.PASS;
    //         }

    //         if (claimFaction.guest_permissions.contains(Permissions.BREAK_BLOCKS)) {
    //             return ActionResult.PASS;
    //         }

    //         return ActionResult.FAIL;
    //     }
    // }

    // private static ActionResult onExplodeDamage(Explosion explosion, Entity entity) {
    //     Entity causingEntity = explosion.getCausingEntity();
    //     World actualWorld = causingEntity != null ? causingEntity.getWorld() : null;

    //     if (explosion.getCausingEntity() != null && explosion.getCausingEntity() instanceof PlayerEntity) {
    //         ActionResult result =
    //                 checkPermissions((PlayerEntity) explosion.getCausingEntity(), entity.getBlockPos(), actualWorld, Permissions.ATTACK_ENTITIES);
    //         if (result == ActionResult.FAIL) {
    //             InteractionsUtil.warn((PlayerEntity) explosion.getCausingEntity(), InteractionsUtilActions.BREAK_BLOCKS);
    //         }
    //         return result;
    //     } else {
    //         if (!FactionsMod.CONFIG.BLOCK_TNT) return ActionResult.PASS;

    //         String dimension = actualWorld.getRegistryKey().getValue().toString();
    //         ChunkPos chunkPosition = actualWorld.getChunk(entity.getBlockPos()).getPos();

    //         Claim claim = Claim.get(chunkPosition.x, chunkPosition.z, dimension);
    //         if (claim == null) return ActionResult.PASS;

    //         Faction claimFaction = claim.getFaction();

    //         if (claimFaction.getClaims().size() * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT
    //                 > claimFaction.getPower()) {
    //             return ActionResult.PASS;
    //         }

    //         if (claimFaction.guest_permissions.contains(Permissions.ATTACK_ENTITIES)) {
    //             return ActionResult.PASS;
    //         }

    //         return ActionResult.FAIL;
    //     }
    // }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand,
            BlockHitResult hitResult) {
        ItemStack stack = player.getStackInHand(hand);

        BlockPos hitPos = hitResult.getBlockPos();
        if (checkPermissions(player, hitPos, world, Permissions.USE_BLOCKS) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_BLOCKS);
            InteractionsUtil.sync(player, stack, hand);
            return ActionResult.FAIL;
        }

        BlockPos placePos = hitPos.add(hitResult.getSide().getVector());
        if (checkPermissions(player, placePos, world,
                Permissions.USE_BLOCKS) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_BLOCKS);
            InteractionsUtil.sync(player, stack, hand);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult onPlaceBlock(ItemUsageContext context) {
        Identifier placeId = null;
        ItemStack stack = context.getStack();
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            placeId = Registries.BLOCK.getId(blockItem.getBlock());
        }

        if (checkPermissions(context.getPlayer(), context.getBlockPos(), context.getWorld(),
                Permissions.PLACE_BLOCKS, placeId) == ActionResult.FAIL) {
            InteractionsUtil.warn(context.getPlayer(), InteractionsUtilActions.PLACE_BLOCKS);
            InteractionsUtil.sync(context.getPlayer(), stack, context.getHand());
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static TypedActionResult<ItemStack> onUseBucket(PlayerEntity player, World world,
            Hand hand) {
        Item item = player.getStackInHand(hand).getItem();

        if (item instanceof BucketItem) {
            Fluid fluid = ((BucketItemAccessor) item).getFluid();
            Identifier fluidId = Registries.FLUID.getId(fluid);

            ActionResult playerResult = checkPermissions(player, player.getBlockPos(), world,
                    Permissions.PLACE_BLOCKS, fluidId);
            if (playerResult == ActionResult.FAIL) {
                InteractionsUtil.warn(player, InteractionsUtilActions.PLACE_OR_PICKUP_LIQUIDS);
                InteractionsUtil.sync(player, player.getStackInHand(hand), hand);
                return TypedActionResult.fail(player.getStackInHand(hand));
            }

            FluidHandling handling =
                    fluid == Fluids.EMPTY ? RaycastContext.FluidHandling.SOURCE_ONLY
                            : RaycastContext.FluidHandling.NONE;

            BlockHitResult raycastResult = ItemInvoker.raycast(world, player, handling);

            if (raycastResult.getType() != BlockHitResult.Type.MISS) {
                BlockPos raycastPos = raycastResult.getBlockPos();
                if (checkPermissions(player, raycastPos, world,
                        Permissions.PLACE_BLOCKS, fluidId) == ActionResult.FAIL) {
                    InteractionsUtil.warn(player, InteractionsUtilActions.PLACE_OR_PICKUP_LIQUIDS);
                    InteractionsUtil.sync(player, player.getStackInHand(hand), hand);
                    return TypedActionResult.fail(player.getStackInHand(hand));
                }

                BlockPos placePos = raycastPos.add(raycastResult.getSide().getVector());
                if (checkPermissions(player, placePos, world,
                        Permissions.PLACE_BLOCKS, fluidId) == ActionResult.FAIL) {
                    InteractionsUtil.warn(player, InteractionsUtilActions.PLACE_OR_PICKUP_LIQUIDS);
                    InteractionsUtil.sync(player, player.getStackInHand(hand), hand);
                    return TypedActionResult.fail(player.getStackInHand(hand));
                }
            }
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private static ActionResult onAttackEntity(PlayerEntity player, World world, Hand hand,
            Entity entity, EntityHitResult hitResult) {
        if (entity != null && checkPermissions(player, entity.getBlockPos(), world,
                Permissions.ATTACK_ENTITIES) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.ATTACK_ENTITIES);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult onUseEntity(PlayerEntity player, Entity entity, World world) {
        BlockPos pos;
        if (entity == null) {
            pos = player.getBlockPos();
        } else {
            pos = entity.getBlockPos();
        }

        if (checkPermissions(player, pos, world, Permissions.USE_ENTITIES) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_ENTITIES);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult onUseInventory(PlayerEntity player, BlockPos pos, World world) {
        if (checkPermissions(player, pos, world,
                Permissions.USE_INVENTORIES) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_INVENTORY);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult isInvulnerableTo(Entity source, Entity target) {
        if (!source.isPlayer() || FactionsMod.CONFIG.FRIENDLY_FIRE)
            return ActionResult.PASS;

        User sourceUser = User.get(source.getUuid());
        User targetUser = User.get(target.getUuid());

        if (!sourceUser.isInFaction() || !targetUser.isInFaction()) {
            return ActionResult.PASS;
        }

        Faction sourceFaction = sourceUser.getFaction();
        Faction targetFaction = targetUser.getFaction();

        if (sourceFaction.getID() == targetFaction.getID()) {
            return ActionResult.SUCCESS;
        }

        if (sourceFaction.isMutualAllies(targetFaction.getID())) {
            return ActionResult.SUCCESS;
        }

        if (!sourceFaction.isMutualEnemies(targetFaction.getID())) {
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    private static ActionResult checkPermissions(PlayerEntity player, BlockPos position,
            World world, Permissions permission) {
        return checkPermissions(player, position, world, permission, null);
    }

    private static ActionResult checkPermissions(PlayerEntity player, BlockPos position,
            World world, Permissions permission, @Nullable Identifier wildernessTarget) {
        if (!FactionsMod.CONFIG.CLAIM_PROTECTION) {
            return ActionResult.PASS;
        }

        User user = User.get(player.getUuid());
        if (player.hasPermissionLevel(FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL) && user.bypass) {
            return ActionResult.PASS;
        }

        String dimension = world.getRegistryKey().getValue().toString();
        ChunkPos chunkPosition = world.getChunk(position).getPos();

        Claim claim = Claim.get(chunkPosition.x, chunkPosition.z, dimension);
        if (claim == null) {
            return evaluateWilderness(permission, wildernessTarget);
        }

        Faction claimFaction = claim.getFaction();

        if (!user.isInFaction()) {
            return claimFaction.guest_permissions.contains(permission)
                    ? ActionResult.SUCCESS
                    : ActionResult.FAIL;
        }

        Faction userFaction = user.getFaction();

        if (claimFaction == userFaction
                && (getRankLevel(claim.accessLevel) <= getRankLevel(user.rank)
                        || (user.rank == User.Rank.GUEST
                                && claimFaction.guest_permissions.contains(permission)
                                && claim.accessLevel == User.Rank.MEMBER))) {
            return ActionResult.SUCCESS;
        }

        if (FactionsMod.CONFIG.RELATIONSHIPS.ALLY_OVERRIDES_PERMISSIONS
                && claimFaction.isMutualAllies(userFaction.getID())
                && claim.accessLevel == User.Rank.MEMBER) {
            return ActionResult.SUCCESS;
        }

        if (claimFaction.getRelationship(userFaction.getID()).permissions.contains(permission)
                && claim.accessLevel == User.Rank.MEMBER) {
            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }

    private static ActionResult evaluateWilderness(Permissions permission,
            @Nullable Identifier target) {
        if (!isWildernessRestricted(permission)) {
            return ActionResult.PASS;
        }

        if (target == null || FactionsMod.CONFIG.WILDERNESS == null) {
            return ActionResult.FAIL;
        }

        if (permission == Permissions.BREAK_BLOCKS) {
            return FactionsMod.CONFIG.WILDERNESS.BREAK_WHITELIST.contains(target.toString())
                    ? ActionResult.PASS
                    : ActionResult.FAIL;
        }

        return FactionsMod.CONFIG.WILDERNESS.PLACE_WHITELIST.contains(target.toString())
                ? ActionResult.PASS
                : ActionResult.FAIL;
    }

    private static boolean isWildernessRestricted(Permissions permission) {
        return permission == Permissions.BREAK_BLOCKS || permission == Permissions.PLACE_BLOCKS;
    }

    private static int getRankLevel(User.Rank rank) {
        switch (rank) {
            case OWNER -> {
                return 3;
            }
            case LEADER -> {
                return 2;
            }
            case COMMANDER -> {
                return 1;
            }
            case MEMBER -> {
                return 0;
            }
            case GUEST -> {
                return -1;
            }
            default -> {
                return -2;
            }
        }
    }
}
