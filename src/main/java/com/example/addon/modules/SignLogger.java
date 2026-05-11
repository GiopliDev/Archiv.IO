package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.SignManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;

import java.util.HashSet;
import java.util.Set;

public class SignLogger extends Module {
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Boolean> render = sgRender.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("render")
        .description("Renders a bounding box around logged signs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("render-distance")
        .description("How far to render the sign boxes.")
        .defaultValue(256)
        .range(1, 10000)
        .sliderRange(1, 1024)
        .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> color = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("color")
        .description("The color of normal signs.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 255, 0, 255))
        .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> dateColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("date-color")
        .description("The color of signs containing a date.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(0, 255, 0, 255))
        .build()
    );
    
    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> playerColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("player-color")
        .description("The color of signs containing a known player name.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Set<BlockPos> loggedSigns = new HashSet<>();
    private int ticks = 0;

    public SignLogger() {
        super(AddonTemplate.CATEGORY, "sign-logger", "Automatically reads nearby signs and prints them to chat.");
    }

    @Override
    public void onActivate() {
        loggedSigns.clear();
        ticks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ticks++;
        if (ticks < 20) return; // Only check once per second
        ticks = 0;

        if (mc.world == null || mc.player == null) return;

        int chunkRadius = mc.options.getViewDistance().getValue();
        BlockPos playerPos = mc.player.getBlockPos();
        int playerCX = playerPos.getX() >> 4;
        int playerCZ = playerPos.getZ() >> 4;

        for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
            for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
                net.minecraft.world.chunk.WorldChunk chunk = mc.world.getChunk(cx, cz);
                if (chunk != null) {
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof SignBlockEntity) {
                            SignBlockEntity sign = (SignBlockEntity) blockEntity;
                            BlockPos pos = sign.getPos();
                            if (loggedSigns.contains(pos)) continue;
                            
                            logSign(pos, sign);
                            loggedSigns.add(pos);
                        }
                    }
                }
            }
        }
    }

    private void logSign(BlockPos pos, SignBlockEntity sign) {
        StringBuilder sb = new StringBuilder();
        
        SignText front = sign.getFrontText();
        for (Text component : front.getMessages(false)) {
            String text = component.getString().trim();
            if (!text.isEmpty()) {
                sb.append(text).append(" ");
            }
        }

        SignText back = sign.getBackText();
        for (Text component : back.getMessages(false)) {
            String text = component.getString().trim();
            if (!text.isEmpty()) {
                sb.append(text).append(" ");
            }
        }

        String signContent = sb.toString().trim();
        if (!signContent.isEmpty()) {
            boolean isNew = SignManager.processSign(signContent, pos, mc.world);
            if (isNew) {
                info("Found new Sign at [%d, %d, %d]: %s", pos.getX(), pos.getY(), pos.getZ(), signContent);
            }
        }
    }

    @EventHandler
    private void onRender3D(meteordevelopment.meteorclient.events.render.Render3DEvent event) {
        if (!render.get() || mc.world == null || mc.player == null) return;

        String currentDim = mc.world.getRegistryKey().getValue().toString();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = renderDistance.get();

        for (SignManager.SignEntry entry : SignManager.SIGN_DB.values()) {
            boolean hasDates = !entry.possibleDates.isEmpty();
            boolean hasPlayers = !entry.players.isEmpty();
            meteordevelopment.meteorclient.utils.render.color.Color boxColor = hasPlayers ? playerColor.get() : (hasDates ? dateColor.get() : color.get());

            for (SignManager.SignLocation loc : entry.locations) {
                if (loc.dimension.equals(currentDim)) {
                    BlockPos pos = new BlockPos(loc.x, loc.y, loc.z);
                    double dx = pos.getX() - mc.player.getX();
                    double dy = pos.getY() - mc.player.getY();
                    double dz = pos.getZ() - mc.player.getZ();
                    if (dx*dx + dy*dy + dz*dz < r*r) {
                        event.renderer.box(pos, boxColor, boxColor, meteordevelopment.meteorclient.renderer.ShapeMode.Lines, 0);
                    }
                }
            }
        }
    }

    @Override
    public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList list = theme.verticalList();
        
        meteordevelopment.meteorclient.gui.widgets.pressable.WButton helpBtn = list.add(theme.button("Print Commands Help")).widget();
        helpBtn.action = () -> {
            info("--- SignLogger Commands ---");
            info(".signs list - Show loaded signs count.");
            info(".signs search text <query> - Search by text.");
            info(".signs search date <query> - Search by date.");
            info(".signs search player <query> - Search by player.");
            info(".signs player add <name/*> - Add known player.");
            info(".signs player remove <name> - Remove player.");
            info(".signs update-players - Scan all signs for known players.");
            info(".signs export [query] - Export database to CSV.");
        };
        
        meteordevelopment.meteorclient.gui.widgets.WWidget settingsWidget = super.getWidget(theme);
        if (settingsWidget != null) {
            list.add(settingsWidget);
        }
        
        return list;
    }
}
