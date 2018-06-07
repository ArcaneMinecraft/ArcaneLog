package com.arcaneminecraft.log;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;

class CoreProtectLogger {
    private final ServerSocket sock; // port 30000
    private final ArcaneLog plugin;
    private CoreProtectAPI coreprotect;

    CoreProtectLogger(ArcaneLog plugin) throws IOException {
        String logIP = "127.0.0.1";
        int logPort = 25555;

        this.plugin = plugin;

        sock = new ServerSocket(logPort, 0, Inet4Address.getByName(logIP));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                //noinspection InfiniteLoopStatement
                while (true){
                    //init the client
                    Socket incoming = sock.accept();

                    //Read the data
                    DataInputStream dis = new DataInputStream(incoming.getInputStream());
                    String msg = dis.readUTF();
                    String pName = dis.readUTF();
                    String pDisplayName = dis.readUTF();
                    String pUUID = dis.readUTF();
                    // Log the message

                    writeCore(new DummyPlayer(pName, pDisplayName, pUUID), msg);

                    dis.close();
                    incoming.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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

    private void writeCore(DummyPlayer dummyPlayer, String msg) {
        if (getCoreProtect() == null) {
            return;
        }
        if (msg.startsWith("/")) coreprotect.logCommand(dummyPlayer, msg);
        else coreprotect.logChat(dummyPlayer, msg);
    }
}
