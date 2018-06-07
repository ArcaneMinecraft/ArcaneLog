package com.arcaneminecraft.log;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class ArcaneLog extends JavaPlugin {
    @Override
    public void onEnable() {
        DummyPlayer.plugin = this;
        try {
            new CoreProtectLogger(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
