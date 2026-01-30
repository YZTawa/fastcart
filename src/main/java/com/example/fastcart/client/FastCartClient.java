package com.example.fastcart.client;

import com.example.fastcart.net.FastCartNet;
import net.fabricmc.api.ClientModInitializer;

public class FastCartClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FastCartNet.initClient();
        CartControlClient.init();
    }
}
