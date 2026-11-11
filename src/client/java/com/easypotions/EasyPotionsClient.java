package com.easypotions;

// Authors: NotHeartYT, Praveen_god_67, Nebrion_Verse

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class EasyPotionsClient implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;
    public static volatile String status = "";
    public static volatile int statusColor = 0xFFAAAAAA;
    public static volatile long statusTime = 0;
    private static long pendingUntil = 0;

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.easypotions.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O,
                KeyBinding.Category.create(Identifier.of("easypotions", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null && mc.world != null
                        && mc.getNetworkHandler() != null) {
                    mc.setScreen(new EasyScreen());
                }
            }
        });

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!overlay) onGameMessage(msg);
        });
    }

    public static void setStatus(String s, int color) {
        status = s;
        statusColor = color;
        statusTime = System.currentTimeMillis();
    }

    /** Sends a command through the normal server command system. */
    public static void run(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) {
            setStatus("Not connected to a server.", 0xFFFF5555);
            return;
        }
        mc.getNetworkHandler().sendChatCommand(command);
        pendingUntil = System.currentTimeMillis() + 4000;
        setStatus("Sent. Waiting for server...", 0xFFAAAAAA);
    }

    private static void onGameMessage(Text msg) {
        if (System.currentTimeMillis() > pendingUntil) return;
        try {
            if (msg.getContent() instanceof TranslatableTextContent t
                    && t.getKey().startsWith("commands.effect.")) {
                setStatus("Server confirmed: " + msg.getString(), 0xFF55FF55);
                return;
            }
            var color = msg.getStyle().getColor();
            if (color != null && color.getRgb() == 0xFF5555) {
                setStatus("Server: " + msg.getString(), 0xFFFF5555);
            }
        } catch (Throwable ignored) {
        }
    }
}
