package ru.floerka.hykkit.agent.fallback;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler;

import java.util.concurrent.Callable;

public class InitialHandlerFallback {

    public static void fallback(InitialPacketHandler handler, Connect packet, Callable<Object> superCall) throws Exception {
        try {
            handler.disconnect("Error while running HykkitAgent. Contact the server developer");
        } catch (Exception e) {
            System.err.println("[HykkitAgent] Fallback error. Send to original authentication method. " + e);
            superCall.call();
        }
    }
}
