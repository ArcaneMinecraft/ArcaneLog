package com.arcaneminecraft.log;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.UUID;

public final class ArcaneLog extends JavaPlugin {
    private CoreProtectLogger cpl;
    private ServerSocket sock; // port 30000

    @Override
    public void onEnable() {
        String logIP = "127.0.0.1";
        int logPort = 25555;

        DummyPlayer.plugin = this;
        cpl = new CoreProtectLogger(this);

        try {
            sock = new ServerSocket(logPort, 0, Inet4Address.getByName(logIP));

            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                while (true){
                    //init the client
                    try (Socket incoming = sock.accept()) {
                        socketParser(incoming);
                    } catch (SocketException e) {
                        getLogger().warning( e.getMessage() + " (Plugin disabled?)");
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void socketParser(Socket socket) throws IOException {
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        // First: number of UTF
        // Second...: UTF Data Return Strings

        String type = dis.readUTF();

        if (type.equals("LogCoreProtect")) {
            String pName = dis.readUTF();
            String pDisplayName = dis.readUTF();
            String pUUID = dis.readUTF();
            String msg = dis.readUTF();

            cpl.writeCore(new DummyPlayer(pName, pDisplayName, pUUID), msg);
            return;
        }

        // Preferably returns the old name to detect name change on login
        if (type.equals("GetPlayerName")) {
            UUID u = UUID.fromString(dis.readUTF());

            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            String name = getServer().getOfflinePlayer(u).getName();

            dos.writeUTF(name == null ? "" : name);
            //return;
        }
    }

    public void onDisable() {
        try {
            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
