package com.example.fastcart.server;

import com.example.fastcart.net.CartControlC2SPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;

public final class CartSpeedController {
    private CartSpeedController() {}

    private static final int MIN_GEAR = 1;
    private static final int MAX_GEAR = 10;

    // 10档直线目标速度（方块/tick）
    private static final double MAX_STRAIGHT_SPEED = 12.0;

    // 防脱轨：弯道/坡道自动限速
    private static final double SAFE_CURVE_SPEED = 3.0;
    private static final double SAFE_ASCEND_SPEED = 4.0;

    private record State(int gear, boolean throttle) {}
    private static final Int2ObjectOpenHashMap<State> CART_STATE = new Int2ObjectOpenHashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CartSpeedController::onEndServerTick);
    }

    public static void onControlPacket(ServerPlayerEntity player, CartControlC2SPayload payload) {
        Entity e = player.getWorld().getEntityById(payload.entityId());
        if (!(e instanceof AbstractMinecartEntity cart)) return;

        if (player.getVehicle() != cart) return;

        int gear = MathHelper.clamp(payload.gear(), MIN_GEAR, MAX_GEAR);
        CART_STATE.put(cart.getId(), new State(gear, payload.throttle()));
    }

    private static void onEndServerTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            tickWorld(world);
        }
    }

    private static void tickWorld(ServerWorld world) {
        if (CART_STATE.isEmpty()) return;

        Iterator<Int2ObjectOpenHashMap.Entry<State>> it = CART_STATE.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            int entityId = entry.getIntKey();
            State state = entry.getValue();

            Entity e = world.getEntityById(entityId);
            if (!(e instanceof AbstractMinecartEntity cart)) {
                it.remove();
                continue;
            }

            if (!(cart.getFirstPassenger() instanceof PlayerEntity)) {
                it.remove();
                continue;
            }

            applyControl(world, cart, state);
        }
    }

    private static void applyControl(ServerWorld world, AbstractMinecartEntity cart, State state) {
        if (!state.throttle) {
            cart.setVelocity(Vec3d.ZERO);
            cart.velocityDirty = true;
            return;
        }

        BlockPos railPos = cart.getBlockPos();
        BlockState railState = world.getBlockState(railPos);

        if (!(railState.getBlock() instanceof AbstractRailBlock railBlock)) {
            BlockPos down = railPos.down();
            BlockState downState = world.getBlockState(down);
            if (downState.getBlock() instanceof AbstractRailBlock downRail) {
                railPos = down;
                railState = downState;
                railBlock = downRail;
            } else {
                return;
            }
        }

        RailShape shape = getRailShape(railBlock, railState);
        Vec3d dir = computeRailDirection(cart, shape);
        if (dir.lengthSquared() < 1.0E-6) return;

        double target = gearToSpeed(state.gear);
        target = applySafetyLimit(target, shape);

        cart.setVelocity(dir.normalize().multiply(target));
        snapToRailCenter(cart, railPos, shape);

        cart.velocityDirty = true;
    }

    private static double gearToSpeed(int gear) {
        double t = (double) gear / (double) MAX_GEAR;
        return Math.max(MAX_STRAIGHT_SPEED * t, 0.4);
    }

    private static double applySafetyLimit(double target, RailShape shape) {
        return switch (shape) {
            case NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST -> Math.min(target, SAFE_CURVE_SPEED);
            case ASCENDING_EAST, ASCENDING_WEST, ASCENDING_NORTH, ASCENDING_SOUTH -> Math.min(target, SAFE_ASCEND_SPEED);
            default -> target;
        };
    }

    private static RailShape getRailShape(AbstractRailBlock railBlock, BlockState state) {
        Property<RailShape> prop = railBlock.getShapeProperty();
        return state.get(prop);
    }

    private static Vec3d computeRailDirection(AbstractMinecartEntity cart, RailShape shape) {
        Vec3d facing = Vec3d.fromPolar(0.0F, cart.getYaw());

        return switch (shape) {
            case EAST_WEST -> chooseByFacing(facing, new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0));
            case NORTH_SOUTH -> chooseByFacing(facing, new Vec3d(0, 0, 1), new Vec3d(0, 0, -1));

            case ASCENDING_EAST -> chooseByFacing(facing, new Vec3d(1, 0.5, 0), new Vec3d(-1, -0.5, 0));
            case ASCENDING_WEST -> chooseByFacing(facing, new Vec3d(-1, 0.5, 0), new Vec3d(1, -0.5, 0));
            case ASCENDING_NORTH -> chooseByFacing(facing, new Vec3d(0, 0.5, -1), new Vec3d(0, -0.5, 1));
            case ASCENDING_SOUTH -> chooseByFacing(facing, new Vec3d(0, 0.5, 1), new Vec3d(0, -0.5, -1));

            case NORTH_EAST -> chooseByFacing(facing, new Vec3d(1, 0, 0), new Vec3d(0, 0, -1));
            case NORTH_WEST -> chooseByFacing(facing, new Vec3d(-1, 0, 0), new Vec3d(0, 0, -1));
            case SOUTH_EAST -> chooseByFacing(facing, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1));
            case SOUTH_WEST -> chooseByFacing(facing, new Vec3d(-1, 0, 0), new Vec3d(0, 0, 1));

            default -> Vec3d.ZERO;
        };
    }

    private static Vec3d chooseByFacing(Vec3d facing, Vec3d a, Vec3d b) {
        double da = facing.dotProduct(a.normalize());
        double db = facing.dotProduct(b.normalize());
        return (da >= db) ? a : b;
    }

    private static void snapToRailCenter(AbstractMinecartEntity cart, BlockPos railPos, RailShape shape) {
        double cx = railPos.getX() + 0.5;
        double cz = railPos.getZ() + 0.5;

        if (shape == RailShape.EAST_WEST) {
            cart.setPos(cart.getX(), cart.getY(), MathHelper.lerp(0.35, cart.getZ(), cz));
        } else if (shape == RailShape.NORTH_SOUTH) {
            cart.setPos(MathHelper.lerp(0.35, cart.getX(), cx), cart.getY(), cart.getZ());
        } else {
            cart.setPos(
                    MathHelper.lerp(0.20, cart.getX(), cx),
                    cart.getY(),
                    MathHelper.lerp(0.20, cart.getZ(), cz)
            );
        }
    }
}
