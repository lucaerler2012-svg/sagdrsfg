package de.obsicrystal2;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vollautomatisch: Setzt du Obsidian und hast einen End-Kristall in der Hotbar (Slots 1-9),
 * wechselt die Mod auf den Kristall und setzt ihn oben auf das gerade gesetzte Obsidian.
 * Danach geht es zurueck auf den vorherigen Slot.
 *
 * Hast du keinen Kristall, passiert nichts, du setzt nur den Block. Die Kamera wird nicht bewegt.
 * Der Kristall wird nur gesetzt, wenn ueber dem Obsidian zwei Bloecke frei sind.
 */
public class ObsiCrystal2Client implements ClientModInitializer {

    /** Nach dem Setzen zurueck auf den vorherigen Slot wechseln? */
    private static final boolean RESTORE_SLOT = true;
    /** So viele Ticks wird auf die Bestaetigung des Obsidians gewartet. */
    private static final int WAIT_TICKS = 10;

    private enum Stage { IDLE, WAIT_PLACED, PLACE, RESTORE }

    private Stage stage = Stage.IDLE;
    private BlockPos[] candidates;
    private BlockPos obsidianPos;
    private int timeout;
    private int previousSlot = -1;

    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()
                    && stage == Stage.IDLE
                    && hand == InteractionHand.MAIN_HAND
                    && player.getItemInHand(hand).is(Items.OBSIDIAN)) {
                BlockPos clicked = hit.getBlockPos();
                // Je nach Zielblock landet das Obsidian auf dem Block selbst oder daneben.
                candidates = new BlockPos[] { clicked, clicked.relative(hit.getDirection()) };
                previousSlot = player.getInventory().getSelectedSlot();
                timeout = WAIT_TICKS;
                stage = Stage.WAIT_PLACED;
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        if (stage == Stage.IDLE) {
            return;
        }
        if (client.player == null || client.level == null || client.gameMode == null) {
            reset();
            return;
        }

        Inventory inv = client.player.getInventory();

        switch (stage) {
            case WAIT_PLACED -> {
                if (timeout-- <= 0) {
                    reset();
                    return;
                }
                for (BlockPos pos : candidates) {
                    if (client.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                        obsidianPos = pos;
                        break;
                    }
                }
                if (obsidianPos == null) {
                    return; // noch keine Bestaetigung
                }
                // Platz fuer den Kristall pruefen: zwei freie Bloecke ueber dem Obsidian.
                if (!client.level.getBlockState(obsidianPos.above()).isAir()
                        || !client.level.getBlockState(obsidianPos.above(2)).isAir()) {
                    reset();
                    return;
                }
                int slot = findCrystal(inv);
                if (slot < 0) {
                    reset();
                    return; // kein Kristall: nur der Block bleibt
                }
                inv.setSelectedSlot(slot);
                stage = Stage.PLACE;
            }
            case PLACE -> {
                // Ein Tick Abstand, damit der Slot-Wechsel vor dem Setzen beim Server ankommt.
                if (inv.getItem(inv.getSelectedSlot()).is(Items.END_CRYSTAL) && obsidianPos != null) {
                    Vec3 top = new Vec3(obsidianPos.getX() + 0.5, obsidianPos.getY() + 1.0, obsidianPos.getZ() + 0.5);
                    client.gameMode.useItemOn(
                            client.player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(top, Direction.UP, obsidianPos, false));
                }
                if (RESTORE_SLOT) {
                    stage = Stage.RESTORE;
                } else {
                    reset();
                }
            }
            case RESTORE -> {
                if (previousSlot >= 0) {
                    inv.setSelectedSlot(previousSlot);
                }
                reset();
            }
            default -> reset();
        }
    }

    private static int findCrystal(Inventory inv) {
        for (int slot = 0; slot < 9; slot++) {
            if (inv.getItem(slot).is(Items.END_CRYSTAL)) {
                return slot;
            }
        }
        return -1;
    }

    private void reset() {
        stage = Stage.IDLE;
        candidates = null;
        obsidianPos = null;
        previousSlot = -1;
        timeout = 0;
    }
}
