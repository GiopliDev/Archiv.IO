package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class CustomAutoLogin extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> loginCommand = sgGeneral.add(new StringSetting.Builder()
        .name("login-command")
        .description("The command to send upon joining.")
        .defaultValue("/login password")
        .build()
    );

    private int ticksToLogin = -1;

    public CustomAutoLogin() {
        super(AddonTemplate.CATEGORY, "custom-auto-login", "Automatically sends a custom login command 5 seconds after joining a server.");
    }

    @Override
    public void onActivate() {
        ticksToLogin = 100; // Trigger timer if activated while already in game
        info("Custom Auto Login timer started! (5 seconds)");
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;
        ticksToLogin = 100;
        info("Joined game! Custom Auto Login timer started! (5 seconds)");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (ticksToLogin > 0) {
            ticksToLogin--;
            if (ticksToLogin % 20 == 0) {
                // info("Login in " + (ticksToLogin / 20) + " seconds...");
            }
        } else if (ticksToLogin == 0) {
            ticksToLogin = -1;
            String cmd = loginCommand.get();
            if (cmd != null && !cmd.isEmpty()) {
                if (cmd.startsWith("/")) {
                    mc.player.networkHandler.sendChatCommand(cmd.substring(1));
                } else {
                    mc.player.networkHandler.sendChatMessage(cmd);
                }
                info("Sent login command.");
            }
        }
    }
}
