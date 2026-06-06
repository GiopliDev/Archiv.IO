package com.example.addon.commands;

import com.example.addon.modules.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.command.CommandSource;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class StashMoverCommand extends Command {
    public StashMoverCommand() {
        super("stashmover", "Manages input and output chests for Stash Mover.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add")
            .then(literal("in").executes(context -> {
                StashMover module = Modules.get().get(StashMover.class);
                BlockPos pos = getTargetChest();
                if (pos != null) {
                    if (!module.inputChests.contains(pos)) {
                        module.inputChests.add(pos);
                        info("Added input chest at " + pos.toShortString());
                    } else {
                        error("Chest is already an input chest.");
                    }
                }
                return SINGLE_SUCCESS;
            }))
            .then(literal("out").executes(context -> {
                StashMover module = Modules.get().get(StashMover.class);
                BlockPos pos = getTargetChest();
                if (pos != null) {
                    if (!module.outputChests.contains(pos)) {
                        module.outputChests.add(pos);
                        info("Added output chest at " + pos.toShortString());
                    } else {
                        error("Chest is already an output chest.");
                    }
                }
                return SINGLE_SUCCESS;
            }))
        );

        builder.then(literal("pos1").executes(context -> {
            StashMover module = Modules.get().get(StashMover.class);
            BlockPos pos = getTargetChest();
            if (pos != null) {
                module.pos1 = pos;
                info("Pos1 set to " + pos.toShortString());
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("pos2").executes(context -> {
            StashMover module = Modules.get().get(StashMover.class);
            BlockPos pos = getTargetChest();
            if (pos != null) {
                module.pos2 = pos;
                info("Pos2 set to " + pos.toShortString());
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("set")
            .then(literal("in").executes(context -> {
                StashMover module = Modules.get().get(StashMover.class);
                if (module.pos1 == null || module.pos2 == null) {
                    error("You must set both pos1 and pos2 first!");
                    return SINGLE_SUCCESS;
                }
                int count = scanAndAddChests(module.pos1, module.pos2, module.inputChests);
                info("Added " + count + " chests to input list.");
                return SINGLE_SUCCESS;
            }))
            .then(literal("out").executes(context -> {
                StashMover module = Modules.get().get(StashMover.class);
                if (module.pos1 == null || module.pos2 == null) {
                    error("You must set both pos1 and pos2 first!");
                    return SINGLE_SUCCESS;
                }
                int count = scanAndAddChests(module.pos1, module.pos2, module.outputChests);
                info("Added " + count + " chests to output list.");
                return SINGLE_SUCCESS;
            }))
        );

        builder.then(literal("clear")
            .then(literal("in").executes(context -> {
                StashMover module = Modules.get().get(StashMover.class);
                module.inputChests.clear();
                info("Cleared input chests.");
                return SINGLE_SUCCESS;
            }))
            .then(literal("out").executes(context -> {
                StashMover module = Modules.get().get(StashMover.class);
                module.outputChests.clear();
                info("Cleared output chests.");
                return SINGLE_SUCCESS;
            }))
            .then(literal("all").executes(context -> {
                StashMover module = Modules.get().get(StashMover.class);
                module.inputChests.clear();
                module.outputChests.clear();
                info("Cleared all chests.");
                return SINGLE_SUCCESS;
            }))
        );

        builder.then(literal("start").executes(context -> {
            StashMover module = Modules.get().get(StashMover.class);
            if (!module.isActive()) {
                module.toggle();
            } else {
                module.startCycle();
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("stop").executes(context -> {
            StashMover module = Modules.get().get(StashMover.class);
            if (module.isActive()) module.toggle();
            return SINGLE_SUCCESS;
        }));
    }

    private BlockPos getTargetChest() {
        if (mc.world == null || mc.player == null) return null;

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos rawPos = blockHit.getBlockPos();
            BlockPos pos = new BlockPos(rawPos.getX(), rawPos.getY(), rawPos.getZ());
            Block block = mc.world.getBlockState(pos).getBlock();

            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block instanceof ShulkerBoxBlock) {
                return pos;
            } else {
                error("You must look at a Chest, Trapped Chest, or Shulker Box!");
                return null;
            }
        } else {
            error("You are not looking at any block!");
            return null;
        }
    }

    private int scanAndAddChests(BlockPos p1, BlockPos p2, java.util.List<BlockPos> list) {
        if (mc.world == null) return 0;
        int count = 0;
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    Block block = mc.world.getBlockState(checkPos).getBlock();
                    if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block instanceof ShulkerBoxBlock) {
                        if (!list.contains(checkPos)) {
                            list.add(checkPos);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }
}
