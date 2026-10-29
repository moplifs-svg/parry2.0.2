package com.parrymod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Which players an admin has allowed to parry. Saved with the world. */
public class ParryPermissions extends SavedData {
    private static final String NAME = "parry_permissions";

    private final Set<UUID> allowed = new HashSet<>();

    public static ParryPermissions get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ParryPermissions::load, ParryPermissions::new, NAME);
    }

    public boolean isAllowed(UUID id) {
        return allowed.contains(id);
    }

    public void setAllowed(UUID id, boolean value) {
        boolean changed = value ? allowed.add(id) : allowed.remove(id);
        if (changed) setDirty();
    }

    public static ParryPermissions load(CompoundTag tag) {
        ParryPermissions p = new ParryPermissions();
        ListTag list = tag.getList("allowed", Tag.TAG_INT_ARRAY);
        for (Tag t : list) {
            p.allowed.add(NbtUtils.loadUUID(t));
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : allowed) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("allowed", list);
        return tag;
    }
}
