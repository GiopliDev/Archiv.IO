package com.example.addon;

import com.example.addon.commands.SignSearchCommand;
import com.example.addon.modules.SignLogger;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Archiv.IO");
    public static final HudGroup HUD_GROUP = new HudGroup("Archiv.IO");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Archiv.IO Meteor Addon");

        // Load database
        SignManager.load();

        // Modules
        Modules.get().add(new SignLogger());

        // Commands
        Commands.add(new SignSearchCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("GiopliDev", "SignLoggerMeteorAddon");
    }
}
