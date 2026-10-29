package com.parrymod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> nearby clients: show the parry effect image at a world position. */
public class ParryEffectPacket {
    private final double x;
    private final double y;
    private final double z;

    public ParryEffectPacket(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(ParryEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static ParryEffectPacket decode(FriendlyByteBuf buf) {
        return new ParryEffectPacket(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(ParryEffectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHandler.addEffect(msg.x, msg.y, msg.z)));
        context.setPacketHandled(true);
    }
}
