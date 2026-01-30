package com.example.fastcart.client;

import com.example.fastcart.net.CartControlC2SPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientPlayConnectionEvents;
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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("感谢您使用高速矿车模组\n作者:Ye-Nolta"), false);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(CartControlClient::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        if (!(client.player.getVehicle() instanceof AbstractMinecartEntity cart)) {
            lastThrottle = false;
            lastCartId = -1;
            return;
        }

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

        boolean throttle = client.options.forwardKey.isPressed();

        boolean cartChanged = (lastCartId != cart.getId());
        if (cartChanged) {
            lastCartId = cart.getId();
            lastThrottle = !throttle; // force delta
        }

        if (changed || throttle != lastThrottle) {
            ClientPlayNetworking.send(new CartControlC2SPayload(cart.getId(), gear, throttle));
            lastThrottle = throttle;
        }
    }
}
