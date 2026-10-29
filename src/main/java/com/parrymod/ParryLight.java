package com.parrymod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Makes a parry light up its surroundings like a lantern (light level 15) for a moment,
 * using a temporary invisible light block that is removed again right after.
 */
@Mod.EventBusSubscriber(modid = ParryMod.MODID)
public class ParryLight {
    /** Light level of a lantern. */
    private static final int LIGHT_LEVEL = 15;
    /** How long the light stays: 7 ticks, a bit longer than the effect image. */
    private static final int LIGHT_TICKS = 7;

    private static class Entry {
        final ServerLevel level;
        final BlockPos pos;
        int ticks = LIGHT_TICKS;

        Entry(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void spawn(ServerLevel level, Vec3 position) {
        BlockPos base = BlockPos.containing(position);
        BlockPos[] candidates = {base, base.above(), base.below()};
        for (BlockPos pos : candidates) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), 3);
                ENTRIES.add(new Entry(level, pos));
                return;
            }
        }
    }

    private static void remove(Entry entry) {
        // Only remove it if it is still our light block
        if (entry.level.getBlockState(entry.pos).is(Blocks.LIGHT)) {
            entry.level.setBlock(entry.pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ENTRIES.isEmpty()) return;
        Iterator<Entry> it = ENTRIES.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (--entry.ticks <= 0) {
                remove(entry);
                it.remove();
            }
        }
    }

    /** Never leave a light block behind when the server shuts down. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Entry entry : ENTRIES) {
            remove(entry);
        }
        ENTRIES.clear();
    }
}
