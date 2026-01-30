package com.example.fastcart.client;

import com.example.fastcart.net.CartControlC2SPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class CartControlClient {
    private CartControlClient() {}

    private static final int MIN_GEAR = 1;
    private static final int MAX_GEAR = 10;

    private static int gear = 5;
    private static boolean lastThrottle = false;
    private static int lastCartId = -1;

    // ✅ 进世界提示：只发一次（用 tick 检测是否刚进入世界）
    private static boolean joinMessageSent = false;

    private static KeyBinding gearUp;
    private static KeyBinding gearDown;

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

        // ✅ 不再用 ClientPlayConnectionEvents.JOIN
        // 改为在 tick 中检测 client.player/client.world 是否刚可用，并只发一次

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

        // ……后面保持你原来的“骑矿车/按键/发包”逻辑不变

        // 只在玩家正在骑矿车时生效
        if (!(client.player.getVehicle() instanceof AbstractMinecartEntity cart)) {
            // if we just left a cart, clear state
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

        // if cart changed, force send once
        boolean cartChanged = (lastCartId != cart.getId());
        if (cartChanged) {
            lastCartId = cart.getId();
            lastThrottle = !throttle; // force a delta
        }

        // 只有变化才发包（省流量，也更稳）
        if (changed || throttle != lastThrottle) {
            ClientPlayNetworking.send(new CartControlC2SPayload(cart.getId(), gear, throttle));
            lastThrottle = throttle;
        }
    }
}
