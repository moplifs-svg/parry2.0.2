package com.parrymod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this player is holding the parry pose for N ticks. */
public class ParryPosePacket {
    private final int entityId;
    private final int ticks;

    public ParryPosePacket(int entityId, int ticks) {
        this.entityId = entityId;
        this.ticks = ticks;
    }

    public static void encode(ParryPosePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.ticks);
    }

    public static ParryPosePacket decode(FriendlyByteBuf buf) {
        return new ParryPosePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ParryPosePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHandler.setPose(msg.entityId, msg.ticks)));
        context.setPacketHandled(true);
    }
}
