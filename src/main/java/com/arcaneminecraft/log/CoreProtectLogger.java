package com.arcaneminecraft.log;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;

class CoreProtectLogger {
    private final ArcaneLog plugin;
    private CoreProtectAPI coreprotect;

    CoreProtectLogger(ArcaneLog plugin) {
        this.plugin = plugin;
    }

    private CoreProtectAPI getCoreProtect() {
        if (coreprotect != null)
            return coreprotect;

        Plugin coplugin = plugin.getServer().getPluginManager().getPlugin("CoreProtect");

        if (!(coplugin instanceof CoreProtect)) {
            plugin.getLogger().warning("CoreProtect is not an instance of CoreProtect! Instead: " + plugin.getName());
            return null;
        }

        CoreProtectAPI co = ((CoreProtect)coplugin).getAPI();
        if (!co.isEnabled()){
            plugin.getLogger().warning("CoreProtect API is not enabled!");
            return null;
        }

        if (co.APIVersion() < 4){
            plugin.getLogger().warning("CoreProtect version is too low! Version: " + co.APIVersion());
            return null;
        }

        coreprotect = co;
        return coreprotect;
    }

    void writeCore(DummyPlayer dummyPlayer, String msg) {
        if (getCoreProtect() == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (msg.startsWith("/")) coreprotect.logCommand(dummyPlayer, msg);
            else coreprotect.logChat(dummyPlayer, msg);
        });
    }
}
