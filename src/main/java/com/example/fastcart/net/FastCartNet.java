package com.example.fastcart.net;

import com.example.fastcart.server.CartSpeedController;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class FastCartNet {
    private FastCartNet() {}

    public static void initCommon() {
        PayloadTypeRegistry.playC2S().register(CartControlC2SPayload.ID, CartControlC2SPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CartControlC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> CartSpeedController.onControlPacket(context.player(), payload));
        });
    }

    public static void initClient() {
        PayloadTypeRegistry.playC2S().register(CartControlC2SPayload.ID, CartControlC2SPayload.CODEC);
    }
}
