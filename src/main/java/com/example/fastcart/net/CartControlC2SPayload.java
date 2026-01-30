package com.example.fastcart.net;

import com.example.fastcart.FastCartMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CartControlC2SPayload(int entityId, int gear, boolean throttle) implements CustomPayload {

    public static final CustomPayload.Id<CartControlC2SPayload> ID =
            new CustomPayload.Id<>(Identifier.of(FastCartMod.MODID, "cart_control"));

    public static final PacketCodec<RegistryByteBuf, CartControlC2SPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.INTEGER, CartControlC2SPayload::entityId,
                    PacketCodecs.INTEGER, CartControlC2SPayload::gear,
                    PacketCodecs.BOOL, CartControlC2SPayload::throttle,
                    CartControlC2SPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
