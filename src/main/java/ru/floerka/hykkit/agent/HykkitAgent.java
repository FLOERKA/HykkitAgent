package ru.floerka.hykkit.agent;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import ru.floerka.hykkit.agent.fallback.InitialHandlerFallback;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Callable;

public class HykkitAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            new AgentBuilder.Default()
                    .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                    .type(ElementMatchers.named("com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler"))
                    .transform((builder, td, cl, m, pd) ->
                            builder.method(ElementMatchers.named("handle")
                                            .and(ElementMatchers.takesArgument(0,
                                                    ElementMatchers.named("com.hypixel.hytale.protocol.packets.connection.Connect"))))
                                    .intercept(MethodDelegation.to(InitialInterceptor.class))
                    ).installOn(inst);

            System.out.println("[HykkitAgent] Premain finished successfully.");
        } catch (Exception e) {
            System.err.println("[HykkitAgent] Failed to install agent: " + e.getMessage());
        }
    }

    public static class InitialInterceptor {

        @RuntimeType
        public static Object intercept(@This Object target,
                                       @Argument(0) Object packet,
                                       @SuperCall Callable<Object> superCall) {

            try {
                System.out.println("[HykkitAgent] Intercepting connection...");
                EditedInitialHandler handler = new EditedInitialHandler();

                handler.setTarget((InitialPacketHandler) target);

                handler.handle((Connect) packet);
                return null;

            } catch (Exception e) {
                System.err.println("[HykkitAgent] Interceptor error. Going to fallback. " + e);

                try {
                    InitialHandlerFallback.fallback((InitialPacketHandler) target, (Connect) packet, superCall);
                } catch (Exception ignore) {}

/*                try {
                    return superCall.call();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return null;
                }*/
                return null;
            }
        }
    }
}