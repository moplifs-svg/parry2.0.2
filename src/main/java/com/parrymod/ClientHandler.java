package com.parrymod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client-only: key binding (default F, rebindable in Options > Controls). */
public class ClientHandler {
    public static final KeyMapping PARRY_KEY = new KeyMapping(
            "key.parry.parry",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.parry");

    private static int cooldownTicks = 0;
    private static final ItemStack SWORD_ICON = new ItemStack(Items.IRON_SWORD);

    /** entity id -> ticks left of the parry pose */
    private static final Map<Integer, Integer> POSES = new HashMap<>();

    // ---- parry effect image, drawn in the world facing the camera ----
    private static final ResourceLocation EFFECT_TEXTURE =
            new ResourceLocation(ParryMod.MODID, "textures/effect/parry_effect.png");
    private static final int EFFECT_TICKS = 6;
    /** Layers drawn per effect: the image itself, then bigger and fainter copies for the bloom halo. */
    private static final float[] BLOOM_SCALES = {1.0F, 1.7F, 2.6F};
    private static final float[] BLOOM_ALPHAS = {1.0F, 0.45F, 0.2F};

    private static class WorldEffect {
        final double x;
        final double y;
        final double z;
        int ticks = 0;

        WorldEffect(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final List<WorldEffect> EFFECTS = new ArrayList<>();

    public static void addEffect(double x, double y, double z) {
        EFFECTS.add(new WorldEffect(x, y, z));
    }

    public static void setCooldown(int ticks) {
        cooldownTicks = ticks;
    }

    public static void setPose(int entityId, int ticks) {
        POSES.put(entityId, ticks);
    }

    @Mod.EventBusSubscriber(modid = ParryMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(PARRY_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = ParryMod.MODID, value = Dist.CLIENT)
    public static class ForgeEvents {
        /** Sword icon + seconds left (10, 9, 8 ...) under the crosshair. */
        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;
            GuiGraphics g = event.getGuiGraphics();

            if (cooldownTicks <= 0) return;
            int cx = event.getWindow().getGuiScaledWidth() / 2;
            int y = event.getWindow().getGuiScaledHeight() / 2 + 14;
            String seconds = String.valueOf((cooldownTicks + 19) / 20);

            int textWidth = mc.font.width(seconds);
            int total = 16 + 3 + textWidth;
            int x = cx - total / 2;
            g.renderItem(SWORD_ICON, x, y);
            g.drawString(mc.font, seconds, x + 19, y + 4, 0xFF5555, true);
        }

        /** Parry pose: the main arm is raised in a blocking position (seen in third person / by others). */
        @SubscribeEvent
        public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
            if (!(event.getEntity() instanceof Player player)) return;
            if (!POSES.containsKey(player.getId())) return;
            if (!(event.getRenderer().getModel() instanceof PlayerModel<?> model)) return;
            if (player.getMainArm() == HumanoidArm.RIGHT) {
                model.rightArmPose = HumanoidModel.ArmPose.BLOCK;
            } else {
                model.leftArmPose = HumanoidModel.ArmPose.BLOCK;
            }
        }

        /** Draws the parry image as a quad that always faces the camera, so it is visible from every angle. */
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            if (EFFECTS.isEmpty()) return;

            Camera camera = event.getCamera();
            Vec3 cam = camera.getPosition();
            PoseStack stack = event.getPoseStack();

            RenderSystem.enableBlend();
            // Additive blending: overlapping light adds up, which gives the bloom look
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, EFFECT_TEXTURE);

            for (WorldEffect e : EFFECTS) {
                float t = Mth.clamp((e.ticks + event.getPartialTick()) / EFFECT_TICKS, 0.0F, 1.0F);
                float s = 0.45F + 0.35F * t; // half size in blocks, grows a little
                float a = 1.0F - t;          // fades out

                stack.pushPose();
                stack.translate(e.x - cam.x, e.y - cam.y, e.z - cam.z);
                stack.mulPose(camera.rotation());
                Matrix4f m = stack.last().pose();

                // Bloom: the image plus two larger, fainter copies around it as a soft halo
                for (int layer = 0; layer < BLOOM_SCALES.length; layer++) {
                    float ls = s * BLOOM_SCALES[layer];
                    float la = a * BLOOM_ALPHAS[layer];
                    BufferBuilder b = Tesselator.getInstance().getBuilder();
                    b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    b.vertex(m, -ls, -ls, 0.0F).uv(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, la).endVertex();
                    b.vertex(m, -ls, ls, 0.0F).uv(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, la).endVertex();
                    b.vertex(m, ls, ls, 0.0F).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, la).endVertex();
                    b.vertex(m, ls, -ls, 0.0F).uv(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, la).endVertex();
                    BufferUploader.drawWithShader(b.end());
                }
                stack.popPose();
            }

            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            // START phase runs before vanilla handles keybinds, so we can steal the F press
            if (event.phase != TickEvent.Phase.START) return;
            if (cooldownTicks > 0) cooldownTicks--;
            EFFECTS.removeIf(e -> ++e.ticks >= EFFECT_TICKS);
            Iterator<Map.Entry<Integer, Integer>> it = POSES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Integer> e = it.next();
                if (e.getValue() <= 1) it.remove(); else e.setValue(e.getValue() - 1);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (PARRY_KEY.consumeClick()) {
                if (mc.screen != null) continue;
                Network.CHANNEL.sendToServer(new ParryPacket());
                // Vanilla "swap hands" is also F by default: don't let it fire at the same time.
                if (PARRY_KEY.getKey().equals(mc.options.keySwapOffhand.getKey())) {
                    while (mc.options.keySwapOffhand.consumeClick()) { }
                }
            }
        }
    }
}
