package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.KitManager;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;

import java.util.ArrayList;
import java.util.List;

public class KitBot extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSetup = this.settings.createGroup("Setup");

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Prefix for bot commands ($help, $stats, $kit <name>).")
        .defaultValue("$")
        .build()
    );

    private final Setting<String> homeCommand = sgGeneral.add(new StringSetting.Builder()
        .name("home-command")
        .description("Command to return home.")
        .defaultValue("/home pippo")
        .build()
    );

    private final Setting<Integer> tpaTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("tpa-timeout-seconds")
        .description("How long to wait for TPA acceptance.")
        .defaultValue(60)
        .min(1)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-seconds")
        .description("Cooldown after delivery/failure.")
        .defaultValue(10)
        .min(0)
        .build()
    );

    private final Setting<String> whitelist = sgGeneral.add(new StringSetting.Builder()
        .name("whitelist")
        .description("Comma-separated players allowed to request. Use * for all.")
        .defaultValue("*")
        .build()
    );

    private final Setting<String> activeKit = sgSetup.add(new StringSetting.Builder()
        .name("active-kit-name")
        .description("The name of the kit to map (e.g. pvp, archer) for setup.")
        .defaultValue("pvp")
        .build()
    );

    private final Setting<Boolean> setupMode = sgSetup.add(new BoolSetting.Builder()
        .name("setup-mode")
        .description("Right click a chest to assign it to the active kit.")
        .defaultValue(false)
        .build()
    );

    private enum State {
        IDLE,
        PATHING_TO_CHEST,
        OPENING_CHEST,
        RETRIEVING_KIT,
        SENDING_TPA,
        WAITING_FOR_TELEPORT,
        DELIVERING,
        RETURNING,
        WAITING_FOR_RETURN,
        COOLDOWN
    }

    private State state = State.IDLE;
    private int stateTicks = 0;
    private int timeoutTicks = 0;
    private int cooldownTicks = 0;
    private int openScreenTicks = 0;

    private String currentRequester = null;
    private String currentKit = null;
    private KitManager.KitChest targetChest = null;
    private Vec3d basePos = null;

    public KitBot() {
        super(AddonTemplate.CATEGORY, "kit-bot", "Automatically delivers kits to players on demand.");
    }

    @Override
    public void onActivate() {
        state = State.IDLE;
        currentRequester = null;
        currentKit = null;
        targetChest = null;
        basePos = null;
        openScreenTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        stateTicks++;

        switch (state) {
            case IDLE:
                break;

            case PATHING_TO_CHEST:
                // Check if we reached the chest (within 3 blocks)
                if (targetChest != null) {
                    BlockPos chestPos = targetChest.toBlockPos();
                    if (mc.player.getBlockPos().isWithinDistance(chestPos, 4.0)) {
                        // Stop baritone pathing (send #stop)
                        mc.player.networkHandler.sendChatMessage("#stop");
                        state = State.OPENING_CHEST;
                        stateTicks = 0;
                    } else if (stateTicks > 400) { // 20 seconds timeout for pathing
                        info("Pathing to chest timed out. Aborting.");
                        startCooldown();
                    }
                }
                break;

            case OPENING_CHEST:
                if (targetChest != null) {
                    BlockPos chestPos = targetChest.toBlockPos();
                    // Right click the chest
                    if (mc.interactionManager != null) {
                        // Open container
                        net.minecraft.util.hit.BlockHitResult hitResult = new net.minecraft.util.hit.BlockHitResult(
                            new Vec3d(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5),
                            net.minecraft.util.math.Direction.UP,
                            chestPos,
                            false
                        );
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        state = State.RETRIEVING_KIT;
                        stateTicks = 0;
                        openScreenTicks = 0;
                    }
                }
                break;

            case RETRIEVING_KIT:
                // Wait for container screen to open
                if (mc.currentScreen instanceof HandledScreen) {
                    HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                    ScreenHandler handler = screen.getScreenHandler();
                    
                    // Skip if it's just the player's survival inventory screen
                    if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
                        if (stateTicks > 100) {
                            info("Failed to open chest. Aborting.");
                            startCooldown();
                        }
                        return;
                    }
                    
                    openScreenTicks++;
                    // Wait at least 10 ticks (0.5 seconds) for slot content sync from server
                    if (openScreenTicks < 10) {
                        return;
                    }
                    
                    // Look for a shulker box
                    int shulkerSlot = -1;
                    for (int i = 0; i < handler.slots.size(); i++) {
                        Slot slot = handler.slots.get(i);
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty()) {
                            Block block = Block.getBlockFromItem(stack.getItem());
                            if (block instanceof ShulkerBoxBlock) {
                                shulkerSlot = i;
                                break;
                            }
                        }
                    }

                    if (shulkerSlot != -1) {
                        // Take the shulker (quick move or click to inventory)
                        if (mc.interactionManager != null) {
                            mc.interactionManager.clickSlot(handler.syncId, shulkerSlot, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
                            info("Took shulker box from slot " + shulkerSlot);
                            mc.player.closeHandledScreen();
                            state = State.SENDING_TPA;
                            stateTicks = 0;
                        }
                    } else {
                        info("No shulker box found in chest. Aborting.");
                        mc.player.closeHandledScreen();
                        startCooldown();
                    }
                } else if (stateTicks > 100) { // 5 seconds timeout to open chest
                    info("Failed to open chest. Aborting.");
                    startCooldown();
                }
                break;

            case SENDING_TPA:
                if (currentRequester != null) {
                    mc.player.networkHandler.sendChatCommand("tpa " + currentRequester);
                    basePos = mc.player.getPos();
                    state = State.WAITING_FOR_TELEPORT;
                    stateTicks = 0;
                    timeoutTicks = tpaTimeout.get() * 20;
                }
                break;

            case WAITING_FOR_TELEPORT:
                // Check coordinate change
                if (basePos != null && mc.player.getPos().squaredDistanceTo(basePos) > 100.0) {
                    info("Teleported successfully. Dropping kit.");
                    // Log teleport position to history JSON
                    if (mc.world != null) {
                        String dim = mc.world.getRegistryKey().getValue().toString();
                        KitManager.logHistory(currentRequester, currentKit,
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim);
                    }
                    state = State.DELIVERING;
                    stateTicks = 0;
                } else if (stateTicks > timeoutTicks) {
                    info("TPA timed out. Returning.");
                    if (currentRequester != null) {
                        mc.player.networkHandler.sendChatCommand("tpacancel");
                        mc.player.networkHandler.sendChatCommand("msg " + currentRequester + " order expired - try again");
                    }
                    startCooldown();
                }
                break;

            case DELIVERING:
                // Drop shulker box
                if (stateTicks > 20) { // Wait 1 second after teleporting
                    // Find shulker in player inventory
                    int shulkerSlot = -1;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (!stack.isEmpty()) {
                            Block block = Block.getBlockFromItem(stack.getItem());
                            if (block instanceof ShulkerBoxBlock) {
                                shulkerSlot = i;
                                break;
                            }
                        }
                    }

                    if (shulkerSlot != -1) {
                        // Select slot and sync with server
                        mc.player.getInventory().selectedSlot = shulkerSlot;
                        if (mc.player.networkHandler != null) {
                            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(shulkerSlot));
                        }

                        // Drop item
                        mc.player.dropSelectedItem(true);
                        info("Dropped kit to " + currentRequester);
                        // Increment stats counter
                        KitManager.addDelivery(currentRequester, currentKit);
                        state = State.RETURNING;
                        stateTicks = 0;
                    } else {
                        // Check main inventory if not in hotbar
                        info("Shulker box not in hotbar. Checking main inventory...");
                        // Quick drop logic if possible, or just abort
                        startCooldown();
                    }
                }
                break;

            case RETURNING:
                // Wait a moment for the item to appear on the ground, then /kill to return via auto-respawn
                if (stateTicks > 20) {
                    mc.player.networkHandler.sendChatCommand("kill");
                    info("Sent /kill to return to base via auto-respawn.");
                    startCooldown();
                }
                break;

            case WAITING_FOR_RETURN:
                // Wait to return to base coordinates or just timeout
                if (basePos != null && mc.player.getPos().squaredDistanceTo(basePos) < 25.0) {
                    info("Returned to base.");
                    startCooldown();
                } else if (stateTicks > 200) { // 10 seconds timeout to return
                    info("Return timed out or base position changed.");
                    startCooldown();
                }
                break;

            case COOLDOWN:
                if (stateTicks > cooldownTicks) {
                    info("Cooldown finished. Ready for requests.");
                    state = State.IDLE;
                    currentRequester = null;
                    currentKit = null;
                    targetChest = null;
                }
                break;
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;

        String message = event.getMessage().getString();
        String pfx = prefix.get(); // e.g. "$"

        String triggerHelp  = pfx + "help";
        String triggerStats = pfx + "stats";
        String triggerKit   = pfx + "kit ";

        // Determine which command (if any) is in the message
        int idx = -1;
        String cmd = "";
        if (message.contains(triggerHelp)) {
            idx = message.indexOf(triggerHelp);
            cmd = "help";
        } else if (message.contains(triggerStats)) {
            idx = message.indexOf(triggerStats);
            cmd = "stats";
        } else if (message.contains(triggerKit)) {
            idx = message.indexOf(triggerKit);
            cmd = "kit";
        }

        if (idx == -1) return;

        String sender = extractSender(message, idx);
        if (sender == null) return;

        // Ignore messages sent by the local player themselves
        if (sender.equalsIgnoreCase(mc.player.getGameProfile().getName())) return;

        // $help and $stats are available to everyone (no whitelist needed)
        // $kit requires whitelist
        if (cmd.equals("help")) {
            List<String> kitNames = new ArrayList<>(KitManager.KITS.keySet());
            String kitsList = kitNames.isEmpty() ? "none" : String.join(", ", kitNames);
            String helpMsg = "Commands: " + pfx + "stats | " + pfx + "help | " + pfx + "kit [" + kitsList + "]";
            mc.player.networkHandler.sendChatCommand("msg " + sender + " " + helpMsg);
            return;
        }

        if (cmd.equals("stats")) {
            // Line 1: global counters
            String line1 = "[KitBot Stats] Kits delivered: " + KitManager.totalKitsDelivered
                + " | Unique buyers: " + KitManager.uniqueUsers.size();
            mc.player.networkHandler.sendChatCommand("msg " + sender + " " + line1);

            // Line 2: top-5 kit tier list
            if (!KitManager.kitDeliveryCounts.isEmpty()) {
                List<java.util.Map.Entry<String, Integer>> sorted = new ArrayList<>(KitManager.kitDeliveryCounts.entrySet());
                sorted.sort((a, b) -> b.getValue() - a.getValue());
                StringBuilder tier = new StringBuilder("Top kits: ");
                int rank = 1;
                for (java.util.Map.Entry<String, Integer> entry : sorted) {
                    if (rank > 5) break;
                    tier.append("#").append(rank).append(" ").append(entry.getKey())
                        .append("(").append(entry.getValue()).append(")");
                    if (rank < Math.min(5, sorted.size())) tier.append(" > ");
                    rank++;
                }
                mc.player.networkHandler.sendChatCommand("msg " + sender + " " + tier);
            }
            return;
        }

        // $kit requires IDLE state and whitelist
        if (!cmd.equals("kit")) return;
        if (state != State.IDLE) return;
        if (!isWhitelisted(sender)) return;

        String sub = message.substring(idx + triggerKit.length()).trim();
        String kitName = sub.split(" ")[0].toLowerCase();
        KitManager.KitChest chest = KitManager.getKit(kitName);
        if (chest == null) {
            mc.player.networkHandler.sendChatCommand("msg " + sender + " Kit '" + kitName + "' not found. Use " + pfx + "help to see available kits.");
            return;
        }

        currentRequester = sender;
        currentKit = kitName;
        targetChest = chest;

        mc.player.networkHandler.sendChatCommand("msg " + sender + " The package is arriving! type /tpaccept to get your kit");
        mc.player.networkHandler.sendChatMessage("#goto " + chest.x + " " + chest.y + " " + chest.z);

        state = State.PATHING_TO_CHEST;
        stateTicks = 0;
        info("Starting delivery of kit '" + kitName + "' to " + sender);
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (!setupMode.get() || mc.player == null) return;

        // Verify setup click
        if (event.result != null) {
            BlockPos rawPos = event.result.getBlockPos();
            BlockPos pos = new BlockPos(rawPos.getX(), rawPos.getY(), rawPos.getZ());
            Block block = mc.world.getBlockState(pos).getBlock();
            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block instanceof ShulkerBoxBlock) {
                String kit = activeKit.get().toLowerCase();
                String dim = mc.world.getRegistryKey().getValue().toString();
                KitManager.setKit(kit, pos, dim);
                info("Assigned kit '" + kit + "' to chest at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] in " + dim);
                event.setCancelled(true); // Prevent opening chest when setting it up
            }
        }
    }

    private void startCooldown() {
        state = State.COOLDOWN;
        stateTicks = 0;
        cooldownTicks = cooldown.get() * 20;
    }

    private boolean isWhitelisted(String player) {
        String wl = whitelist.get().trim();
        if (wl.equals("*")) return true;
        for (String p : wl.split(",")) {
            if (p.trim().equalsIgnoreCase(player)) return true;
        }
        return false;
    }

    private String extractSender(String message, int commandIndex) {
        // The part of the message BEFORE the command trigger
        String prefix = message.substring(0, commandIndex).trim();

        // Format: <PlayerName> $kit pvp
        if (prefix.startsWith("<") && prefix.endsWith(">")) {
            return prefix.substring(1, prefix.length() - 1);
        }

        // Format: [PlayerName -> me] $kit pvp  (whisper received)
        if (prefix.startsWith("[") && prefix.endsWith("]")) {
            String inner = prefix.substring(1, prefix.length() - 1);
            if (inner.contains("->")) {
                return inner.substring(0, inner.indexOf("->")).trim();
            }
            return inner.trim();
        }

        // Format: PlayerName whispers: $kit pvp
        if (prefix.toLowerCase().contains("whispers")) {
            String[] parts = prefix.split(" ");
            if (parts.length > 0) return parts[0].replaceAll("[<>\\[\\]:]", "");
        }

        // Format: PlayerName: $kit pvp  (some server formats)
        if (prefix.endsWith(":")) {
            String name = prefix.substring(0, prefix.length() - 1).trim();
            // Reject if name itself contains spaces (it's not a plain name)
            if (!name.contains(" ")) return name;
        }

        // No recognized format → trigger is buried in arbitrary text, ignore it
        return null;
    }
}
