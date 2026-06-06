package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.IntSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import net.minecraft.util.ActionResult;
import java.util.ArrayList;
import java.util.List;

public class StashMover extends Module {
    public enum State {
        IDLE,
        PATHING_INPUT,
        LOOTING_INPUT,
        PATHING_OUTPUT,
        DEPOSITING_OUTPUT
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between moving items.")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .build()
    );

    public final Setting<Integer> foodSlot = sgGeneral.add(new IntSetting.Builder()
        .name("food-slot")
        .description("Hotbar slot (1-9) to ignore when dumping items.")
        .defaultValue(9)
        .min(1)
        .max(9)
        .sliderMax(9)
        .build()
    );

    private State state = State.IDLE;
    private int stateTicks = 0;
    private int openScreenTicks = 0;
    private int currentSlot = 0;

    public BlockPos pos1 = null;
    public BlockPos pos2 = null;

    public final List<BlockPos> inputChests = new ArrayList<>();
    public final List<BlockPos> outputChests = new ArrayList<>();

    private final List<BlockPos> emptyInputChests = new ArrayList<>();
    private final List<BlockPos> fullOutputChests = new ArrayList<>();

    private BlockPos currentTarget = null;

    public StashMover() {
        super(AddonTemplate.CATEGORY, "stash-mover", "Moves items from input chests to output chests.");
    }

    @Override
    public void onActivate() {
        state = State.IDLE;
        emptyInputChests.clear();
        fullOutputChests.clear();
        currentTarget = null;
        if (!inputChests.isEmpty() && !outputChests.isEmpty()) {
            startCycle();
        } else {
            info("Please add input and output chests using .stashmover add in/out");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.networkHandler.sendChatMessage("#stop");
        }
    }

    public void startCycle() {
        if (!isActive()) return;
        state = State.PATHING_INPUT;
        stateTicks = 0;
        pathNextInput();
    }

    private void pathNextInput() {
        currentTarget = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : inputChests) {
            if (emptyInputChests.contains(pos)) continue;
            double dist = mc.player.getBlockPos().getSquaredDistance(pos);
            if (dist < closestDist) {
                closestDist = dist;
                currentTarget = pos;
            }
        }

        if (currentTarget == null) {
            info("All input chests are empty! Stopping Stash Mover.");
            toggle();
            return;
        }

        info("Pathing to input chest at " + currentTarget.toShortString());
        mc.player.networkHandler.sendChatMessage("#goto " + currentTarget.getX() + " " + currentTarget.getY() + " " + currentTarget.getZ());
    }

    private void pathNextOutput() {
        currentTarget = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : outputChests) {
            if (fullOutputChests.contains(pos)) continue;
            double dist = mc.player.getBlockPos().getSquaredDistance(pos);
            if (dist < closestDist) {
                closestDist = dist;
                currentTarget = pos;
            }
        }

        if (currentTarget == null) {
            info("All output chests are full! Stopping Stash Mover.");
            toggle();
            return;
        }

        info("Pathing to output chest at " + currentTarget.toShortString());
        mc.player.networkHandler.sendChatMessage("#goto " + currentTarget.getX() + " " + currentTarget.getY() + " " + currentTarget.getZ());
    }

    // Remove onInteractBlock
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        stateTicks++;

        switch (state) {
            case IDLE:
                break;

            case PATHING_INPUT:
                if (currentTarget != null) {
                    if (mc.player.getBlockPos().isWithinDistance(currentTarget, 4.0)) {
                        mc.player.networkHandler.sendChatMessage("#stop");
                        state = State.LOOTING_INPUT;
                        stateTicks = 0;
                        openScreenTicks = 0;
                        currentSlot = 0;
                        interactChest(currentTarget);
                    } else if (stateTicks > 600) { // 30s timeout
                        info("Timeout reaching input chest.");
                        emptyInputChests.add(currentTarget);
                        pathNextInput();
                        stateTicks = 0;
                    }
                }
                break;

            case LOOTING_INPUT:
                if (mc.currentScreen instanceof HandledScreen) {
                    HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                    ScreenHandler handler = screen.getScreenHandler();

                    if (handler instanceof net.minecraft.screen.PlayerScreenHandler) return;

                    openScreenTicks++;
                    if (openScreenTicks < 10) return; // Wait for sync

                    if (stateTicks < delay.get()) return;
                    stateTicks = 0;

                    int containerSize = handler.slots.size() - 36;
                    if (containerSize < 0) containerSize = 0;

                    boolean movedItem = false;
                    while (currentSlot < containerSize) {
                        Slot slot = handler.slots.get(currentSlot);
                        if (!slot.getStack().isEmpty()) {
                            if (!isInventoryFull()) {
                                mc.interactionManager.clickSlot(handler.syncId, currentSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                                movedItem = true;
                                currentSlot++;
                                break;
                            } else {
                                // Inventory full, stop looting
                                break;
                            }
                        }
                        currentSlot++;
                    }

                    if (!movedItem || isInventoryFull()) {
                        boolean chestEmpty = true;
                        for (int i = 0; i < containerSize; i++) {
                            if (!handler.slots.get(i).getStack().isEmpty()) {
                                chestEmpty = false;
                                break;
                            }
                        }
                        if (chestEmpty) {
                            info("Input chest empty.");
                            emptyInputChests.add(currentTarget);
                        }

                        mc.player.closeHandledScreen();
                        state = State.PATHING_OUTPUT;
                        stateTicks = 0;
                        pathNextOutput();
                    }
                } else if (stateTicks > 60) {
                    info("Failed to open input chest.");
                    emptyInputChests.add(currentTarget);
                    state = State.PATHING_INPUT;
                    stateTicks = 0;
                    pathNextInput();
                }
                break;

            case PATHING_OUTPUT:
                if (currentTarget != null) {
                    if (mc.player.getBlockPos().isWithinDistance(currentTarget, 4.0)) {
                        mc.player.networkHandler.sendChatMessage("#stop");
                        state = State.DEPOSITING_OUTPUT;
                        stateTicks = 0;
                        openScreenTicks = 0;
                        currentSlot = -1; // We'll compute it dynamically in the state
                        interactChest(currentTarget);
                    } else if (stateTicks > 600) {
                        info("Timeout reaching output chest.");
                        fullOutputChests.add(currentTarget);
                        pathNextOutput();
                        stateTicks = 0;
                    }
                }
                break;

            case DEPOSITING_OUTPUT:
                if (mc.currentScreen instanceof HandledScreen) {
                    HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                    ScreenHandler handler = screen.getScreenHandler();

                    if (handler instanceof net.minecraft.screen.PlayerScreenHandler) return;

                    openScreenTicks++;
                    if (openScreenTicks < 10) return;

                    if (stateTicks < delay.get()) return;
                    stateTicks = 0;

                    int containerSize = handler.slots.size() - 36;
                    if (containerSize < 0) containerSize = 0;
                    
                    if (currentSlot == -1) currentSlot = containerSize;

                    int protectedFoodSlot = containerSize + 27 + (foodSlot.get() - 1);
                    boolean depositedAnything = false;
                    
                    while (currentSlot < handler.slots.size()) {
                        if (currentSlot == protectedFoodSlot) {
                            currentSlot++;
                            continue;
                        }
                        
                        Slot slot = handler.slots.get(currentSlot);
                        if (!slot.getStack().isEmpty()) {
                            boolean chestFull = true;
                            for (int i = 0; i < containerSize; i++) {
                                if (handler.slots.get(i).getStack().isEmpty()) {
                                    chestFull = false;
                                    break;
                                }
                            }
                            
                            if (!chestFull) {
                                mc.interactionManager.clickSlot(handler.syncId, currentSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                                depositedAnything = true;
                                currentSlot++;
                                break;
                            } else {
                                break; // Chest is full
                            }
                        }
                        currentSlot++;
                    }

                    if (!depositedAnything) {
                        boolean chestFull = true;
                        for (int i = 0; i < containerSize; i++) {
                            if (handler.slots.get(i).getStack().isEmpty()) {
                                chestFull = false;
                                break;
                            }
                        }

                        if (chestFull) {
                            info("Output chest full.");
                            fullOutputChests.add(currentTarget);
                        }

                        mc.player.closeHandledScreen();
                        
                        if (hasItemsToDeposit()) {
                            state = State.PATHING_OUTPUT;
                            stateTicks = 0;
                            pathNextOutput();
                        } else {
                            state = State.PATHING_INPUT;
                            stateTicks = 0;
                            pathNextInput();
                        }
                    }
                } else if (stateTicks > 60) {
                    info("Failed to open output chest.");
                    fullOutputChests.add(currentTarget);
                    state = State.PATHING_OUTPUT;
                    stateTicks = 0;
                    pathNextOutput();
                }
                break;
        }
    }

    private void interactChest(BlockPos pos) {
        if (mc.interactionManager != null) {
            BlockHitResult hitResult = new BlockHitResult(
                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                Direction.UP,
                pos,
                false
            );
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isInventoryEmpty() {
        for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasItemsToDeposit() {
        int protectedSlot = foodSlot.get() - 1; // getInventory() uses 0-8 for hotbar
        for (int i = 0; i < 36; i++) {
            if (i == protectedSlot) continue;
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
