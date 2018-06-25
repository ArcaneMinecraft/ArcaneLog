package com.arcaneminecraft.log;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class ArcaneLog extends JavaPlugin {
    private CoreProtectLogger cpl;
    @Override
    public void onEnable() {
        DummyPlayer.plugin = this;
        try {
            cpl = new CoreProtectLogger(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onDisable() {
        cpl.onDisable();
    }

}
