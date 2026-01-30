package com.example.fastcart.client;

package com.example.fastcart.server;

import com.example.fastcart.net.CartControlC2SPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
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


public final class CartControlClient {
    private CartControlClient() {}

    private static final int MIN_GEAR = 1;
    private static final int MAX_GEAR = 10;

    private static int gear = 5;
    private static boolean lastThrottle = false;
    private static int lastCartId = -1;

    private static KeyBinding gearUp;
    private static KeyBinding gearDown;

    // 用 tick 检测是否刚进世界，避免依赖 ClientPlayConnectionEvents（不同版本可能不存在）
    private static boolean joinMessageSent = false;

    public static void init() {
        gearUp = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fastcart.gear_up",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP,
                "category.fastcart"
        ));

        gearDown = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fastcart.gear_down",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                "category.fastcart"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(CartControlClient::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            // 不在世界里时重置，下一次进世界还能提示
            joinMessageSent = false;
            lastThrottle = false;
            lastCartId = -1;
            return;
        }

        // 进世界提示（只发一次）
        if (!joinMessageSent) {
            client.player.sendMessage(Text.literal("感谢您使用高速矿车模组\n作者:Ye-Nolta"), false);
            joinMessageSent = true;
        }

        // 只在玩家正在骑矿车时生效
        if (!(client.player.getVehicle() instanceof AbstractMinecartEntity cart)) {
            lastThrottle = false;
            lastCartId = -1;
            return;
        }

        // PgUp / PgDn：调档（按一次加/减一档）
        boolean changed = false;
        while (gearUp.wasPressed()) {
            int old = gear;
            gear = Math.min(MAX_GEAR, gear + 1);
            changed |= (old != gear);
        }
        while (gearDown.wasPressed()) {
            int old = gear;
            gear = Math.max(MIN_GEAR, gear - 1);
            changed |= (old != gear);
        }

        // W：油门（按住才跑）
        boolean throttle = client.options.forwardKey.isPressed();

        // 换车时强制发一次
        boolean cartChanged = (lastCartId != cart.getId());
        if (cartChanged) {
            lastCartId = cart.getId();
            lastThrottle = !throttle;
        }

        // 只有变化才发包
        if (changed || throttle != lastThrottle) {
            ClientPlayNetworking.send(new CartControlC2SPayload(cart.getId(), gear, throttle));
            lastThrottle = throttle;
        }
    }
}
