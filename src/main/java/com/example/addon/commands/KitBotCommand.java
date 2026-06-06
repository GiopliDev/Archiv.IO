package com.example.addon.commands;

import com.example.addon.KitManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.command.CommandSource;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class KitBotCommand extends Command {
    public KitBotCommand() {
        super("kitbot", "Manages the KitBot chest configurations.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // .kitbot set <name>
        builder.then(literal("set")
            .then(argument("name", StringArgumentType.word())
                .executes(context -> {
                    if (mc.world == null || mc.player == null) return SINGLE_SUCCESS;
                    
                    String name = StringArgumentType.getString(context, "name").toLowerCase();
                    
                    HitResult hit = mc.crosshairTarget;
                    if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos rawPos = blockHit.getBlockPos();
                        BlockPos pos = new BlockPos(rawPos.getX(), rawPos.getY(), rawPos.getZ());
                        Block block = mc.world.getBlockState(pos).getBlock();
                        
                        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block instanceof ShulkerBoxBlock) {
                            String dim = mc.world.getRegistryKey().getValue().toString();
                            KitManager.setKit(name, pos, dim);
                            info("Assigned kit '" + name + "' to chest at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] in " + dim);
                        } else {
                            error("You must look at a Chest, Trapped Chest, or Shulker Box!");
                        }
                    } else {
                        error("You are not looking at any block!");
                    }
                    
                    return SINGLE_SUCCESS;
                })
            )
        );

        // .kitbot remove <name>
        builder.then(literal("remove")
            .then(argument("name", StringArgumentType.word())
                .suggests((context, suggester) -> {
                    for (String kitName : KitManager.KITS.keySet()) {
                        suggester.suggest(kitName);
                    }
                    return suggester.buildFuture();
                })
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name").toLowerCase();
                    if (KitManager.KITS.remove(name) != null) {
                        KitManager.save();
                        info("Removed kit '" + name + "'.");
                    } else {
                        error("Kit '" + name + "' is not configured.");
                    }
                    return SINGLE_SUCCESS;
                })
            )
        );

        // .kitbot list
        builder.then(literal("list").executes(context -> {
            if (KitManager.KITS.isEmpty()) {
                info("No kits configured.");
            } else {
                info("--- Configured Kits ---");
                for (Map.Entry<String, KitManager.KitChest> entry : KitManager.KITS.entrySet()) {
                    KitManager.KitChest chest = entry.getValue();
                    info("- " + entry.getKey() + ": [" + chest.x + ", " + chest.y + ", " + chest.z + "] in " + chest.dimension);
                }
            }
            return SINGLE_SUCCESS;
        }));

        // .kitbot clear
        builder.then(literal("clear").executes(context -> {
            KitManager.KITS.clear();
            KitManager.save();
            info("Cleared all configured kits.");
            return SINGLE_SUCCESS;
        }));
    }
}
