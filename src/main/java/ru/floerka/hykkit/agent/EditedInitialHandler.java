package ru.floerka.hykkit.agent;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SetupPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.login.PasswordPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.login.PasswordPacketHandler.SetupHandlerSupplier;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import ru.floerka.hykkit.agent.utils.PlayerFinderUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

public class EditedInitialHandler {

    private InitialPacketHandler target;

    public void setTarget(InitialPacketHandler target) {
        this.target = target;
    }

    public void handle(Connect packet) {
        try {
            if (target == null) {
                throw new IllegalStateException("Target not set");
            }

            Field receivedConnectField = InitialPacketHandler.class.getDeclaredField("receivedConnect");
            receivedConnectField.setAccessible(true);
            receivedConnectField.set(target, true);

            Class<?> superClass = target.getClass().getSuperclass();
            Method clearTimeoutMethod = superClass.getDeclaredMethod("clearTimeout");
            clearTimeoutMethod.setAccessible(true);
            clearTimeoutMethod.invoke(target);
            //target.clearTimeout();

            Channel channel = target.getChannel();

            InitialPacketHandler.logConnectionTimings(channel, "Connect", Level.FINE);

            int protocolCrc = packet.protocolCrc;

/*            if (protocolCrc != -1356075132) {
                int clientBuild = packet.protocolBuildNumber;
                int errorCode = clientBuild < 20 ? 5 : 6;

                String serverVersion = ManifestUtil.getImplementationVersion();

                ProtocolUtil.closeApplicationConnection(channel, errorCode,
                        serverVersion != null ? serverVersion : "unknown");
                return;
            }*/

            HytaleServer server = HytaleServer.get();

            if (server.isShuttingDown()) {
                target.disconnect("Server is shutting down!");
                return;
            }

            if (!server.isBooted()) {
                PluginManager pluginManager = PluginManager.get();
                Object state = pluginManager.getState();

                target.disconnect("Server is booting up! Please try again in a moment. [" + state + "]");
                return;
            }

            ProtocolVersion protocolVersion = new ProtocolVersion(protocolCrc);

            String language = packet.language;

            boolean isTcpConnection = !(channel instanceof QuicStreamChannel);
            if (isTcpConnection) {
                HytaleLogger.getLogger().at(Level.INFO)
                        .log("TCP connection from %s - only insecure auth supported",
                                NettyUtil.formatRemoteAddress(channel));
            }

            java.util.UUID uuid = packet.uuid;
            if (uuid == null) {
                target.disconnect("Missing UUID");
                return;
            }

            String username = packet.username;
            if (username == null || username.isEmpty()) {
                target.disconnect("Missing username");
                return;
            }

            UUID find = PlayerFinderUtil.findUUIDByName(username);
            if(find != null) {
                uuid = find;
            }

            // Получаем referral data
            byte[] referralData = packet.referralData;
            HostAddress referralSource = packet.referralSource;

            // Проверяем размер referral data
            if (referralData != null && referralData.length > 4096) {
                HytaleLogger.getLogger().at(Level.WARNING)
                        .log("Rejecting connection from %s - referral data too large: %d bytes (max: %d)",
                                username, referralData.length, 4096);
                target.disconnect("Referral data exceeds maximum size of 4096 bytes");
                return;
            }

            if (referralData != null && referralSource == null) {
                HytaleLogger.getLogger().at(Level.WARNING)
                        .log("Rejecting connection from %s - referral data provided without source address",
                                username);
                target.disconnect("Referral connections must include source server address");
                return;
            }

            Method generatePassword = target.getClass().getDeclaredMethod("generatePasswordChallengeIfNeeded", UUID.class);
            generatePassword.setAccessible(true);

            byte[] passwordChallenge = (byte[]) generatePassword.invoke(target, uuid);

            ClientType clientType = packet.clientType;
            boolean isEditorClient = clientType == ClientType.Editor;

            Object editorPacketHandlerSupplier = InitialPacketHandler.EDITOR_PACKET_HANDLER_SUPPLIER;

            SetupHandlerSupplier setupHandlerSupplier;

            if (isEditorClient && editorPacketHandlerSupplier != null) {

                Method createMethod = editorPacketHandlerSupplier.getClass().getMethod("create",
                        Channel.class, ProtocolVersion.class, String.class, boolean.class);

                setupHandlerSupplier = (channel1, protocolVersion1, language1, auth) -> {
                    try {
                        return (PacketHandler) createMethod.invoke(editorPacketHandlerSupplier,
                                channel1, protocolVersion1, language1, true);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
            } else {
                setupHandlerSupplier = (channel1, protocolVersion1, language1, auth) ->
                        new SetupPacketHandler(channel1, protocolVersion1, language1,
                                auth.getUuid(), auth.getUsername(),
                                auth.getReferralData(), auth.getReferralSource());
            }

            PasswordPacketHandler passwordPacketHandler = new PasswordPacketHandler(
                    channel, protocolVersion, language, uuid, username,
                    referralData, referralSource, passwordChallenge, setupHandlerSupplier);

            NettyUtil.setChannelHandler(channel, passwordPacketHandler);

            System.out.println("[HykkitAgent] Successfully handled connection for: " + username);

        } catch (Exception e) {
            System.err.println("[HykkitAgent] Error in simplified handler: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}