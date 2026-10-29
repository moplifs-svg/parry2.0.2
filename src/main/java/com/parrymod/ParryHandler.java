package com.parrymod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side parry logic. */
@Mod.EventBusSubscriber(modid = ParryMod.MODID)
public class ParryHandler {
    /** Cooldown when nobody hits you during the parry window: 100 ticks = 5 seconds (fixed, not in config). */
    private static final int MISS_COOLDOWN_TICKS = 100;
    /** Weakness I applied to whoever got parried: 20 ticks = 1 second. */
    private static final int WEAKNESS_TICKS = 20;
    private static final Map<UUID, Long> PARRY_END = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_END = new HashMap<>();

    /** Called when a player presses the parry key. */
    public static void startParry(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        // No permission: nothing happens at all (no swing, no cooldown, no message).
        if (!ParryPermissions.get(player.server).isAllowed(player.getUUID())) return;
        long now = player.level().getGameTime();
        UUID id = player.getUUID();
        if (now < COOLDOWN_END.getOrDefault(id, 0L)) return;

        PARRY_END.put(id, now + ParryConfig.WINDOW_TICKS.get());
        COOLDOWN_END.put(id, now + MISS_COOLDOWN_TICKS);
        // Everyone sees the player's arm in a parry (blocking) pose while the window is open.
        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new ParryPosePacket(player.getId(), ParryConfig.WINDOW_TICKS.get()));
        sendCooldown(player, MISS_COOLDOWN_TICKS);
    }

    private static void sendCooldown(ServerPlayer player, int ticks) {
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CooldownPacket(ticks));
    }

    /** Forget a player's parry state (used when an admin removes their permission). */
    public static void clearState(ServerPlayer player) {
        PARRY_END.remove(player.getUUID());
    }

    private static boolean isParrying(ServerPlayer player) {
        Long end = PARRY_END.get(player.getUUID());
        return end != null && player.level().getGameTime() <= end;
    }

    /** Melee (and other entity) attacks. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isParrying(player)) return;

        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_EXPLOSION)) return;
        if (source.getDirectEntity() instanceof Projectile) return; // handled in onProjectileImpact

        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity living)) return;

        event.setCanceled(true);
        parrySuccess(player);

        double dx = player.getX() - living.getX();
        double dz = player.getZ() - living.getZ();
        living.knockback(ParryConfig.KNOCKBACK.get(), dx, dz);
        living.hurtMarked = true;
        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0));
    }

    /** Arrows, tridents, snowballs, etc. are deflected back along the player's look direction (no weakness). */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof ServerPlayer player)) return;
        if (!isParrying(player)) return;

        Projectile projectile = event.getProjectile();
        if (projectile.getOwner() == player) return;

        event.setCanceled(true);

        double speed = Math.max(projectile.getDeltaMovement().length(), 0.8D);
        Vec3 look = player.getLookAngle();
        projectile.setPos(player.getEyePosition().add(look.scale(1.0D)));
        projectile.setDeltaMovement(look.scale(speed));
        projectile.setOwner(player);
        projectile.hurtMarked = true;

        parrySuccess(player);
    }

    private static void parrySuccess(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();

        // A successful parry has NO cooldown: you can parry again right away.
        PARRY_END.remove(id);
        COOLDOWN_END.remove(id);
        sendCooldown(player, 0);

        Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(0.8D));

        // Sound effect (parry.ogg)
        level.playSound(null, pos.x, pos.y, pos.z, ParryMod.PARRY_SOUND.get(),
                SoundSource.PLAYERS, 1.0F, 0.95F + level.random.nextFloat() * 0.1F);

        // Lantern-like light (level 15) for a split second
        ParryLight.spawn(level, pos);

        // Parry effect image: drawn in the world facing every camera, seen by everyone within 64 blocks
        Network.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, 64.0D, level.dimension())),
                new ParryEffectPacket(pos.x, pos.y, pos.z));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PARRY_END.remove(id);
        COOLDOWN_END.remove(id);
    }
}
