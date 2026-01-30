package com.example.fastcart;

import com.example.fastcart.net.FastCartNet;
import com.example.fastcart.server.CartSpeedController;
import net.fabricmc.api.ModInitializer;

public class FastCartMod implements ModInitializer {
    public static final String MODID = "fastcart";

    @Override
    public void onInitialize() {
        FastCartNet.initCommon();
        CartSpeedController.init();
    }
}
