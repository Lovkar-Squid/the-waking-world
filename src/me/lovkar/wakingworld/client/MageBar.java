package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.UUID;
import me.lovkar.wakingworld.mage.MageEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress;

public final class MageBar {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("wakingworld", "textures/gui/mage_bar.png");
    private static final int TEX_W = 256;
    private static final int TEX_H = 128;
    private static final int FRAME_W = 220;
    private static final int FRAME_H = 28;
    private static final int FILL_X = 6;
    private static final int FILL_Y = 8;
    private static final int FILL_W = 208;
    private static final int FILL_H = 12;
    private static final int PIP = 12;
    private static final int PIP_STEP = 16;
    private static final int GLOW = 16;
    private static final int STAGES = 4;
    private static UUID cachedId;
    private static int cachedEntity = -1;

    private MageBar() {
    }

    private static MageEntity mageFor(UUID var0) {
        Minecraft var1 = Minecraft.getInstance();
        if (var1.level == null) {
            return null;
        } else if (var0.equals(cachedId) && var1.level.getEntity(cachedEntity) instanceof MageEntity var3 && var3.barId().map(var0::equals).orElse(false)) {
            return var3;
        } else {
            for (Entity var6 : var1.level.entitiesForRendering()) {
                if (var6 instanceof MageEntity var4 && var4.barId().map(var0::equals).orElse(false)) {
                    cachedId = var0;
                    cachedEntity = var4.getId();
                    return var4;
                }
            }

            return null;
        }
    }

    public static void onBossBar(BossEventProgress var0) {
        MageEntity var1 = mageFor(var0.getBossEvent().getId());
        if (var1 != null) {
            var0.setCanceled(true);
            var0.setIncrement(54);
            GuiGraphics var2 = var0.getGuiGraphics();
            Minecraft var3 = Minecraft.getInstance();
            int var4 = var0.getWindow().getGuiScaledWidth();
            long var5 = var3.level == null ? 0L : var3.level.getGameTime();
            float var7 = (float)var5 + var0.getPartialTick().getGameTimeDeltaPartialTick(false);
            float var8 = 0.5F + 0.5F * Mth.sin(var7 * 0.12F);
            int var9 = Mth.clamp(var1.stage(), 1, 4);
            float var10 = var1.channelProgress();
            int var11 = var10 > 0.0F ? 14704698 : MageEntity.stageColour(var9);
            int var12 = var0.getY() + Cinematic.letterbox();
            int var13 = (var4 - 220) / 2;
            Component var15 = var1.getDisplayName();
            int var16 = var3.font.width(var15);
            var2.drawString(var3.font, var15, (var4 - var16) / 2, var12 - 10, lighten(var11, 0.4F), true);
            var2.blit(TEX, var13, var12, 0.0F, 0.0F, 220, 28, 256, 128);
            var2.blit(TEX, var13 + 6, var12 + 8, 0.0F, 48.0F, 208, 12, 256, 128);
            float var17 = var0.getBossEvent().getProgress();
            int var18 = Mth.clamp((int)(208.0F * var17), 0, 208);
            if (var18 > 0) {
                RenderSystem.enableBlend();
                setColour(var11, 1.0F);
                var2.blit(TEX, var13 + 6, var12 + 8, 0.0F, 32.0F, var18, 12, 256, 128);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.6F + 0.4F * var8);
                if (var18 < 208) {
                    var2.blit(TEX, var13 + 6 + var18 - 1, var12 + 8, 28.0F, 64.0F, Math.min(4, 208 - var18 + 1), 12, 256, 128);
                }

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                glow(var2, var13 + 6 + var18, var12 + 8 + 6, lighten(var11, 0.5F), 0.55F + 0.35F * var8, 1);
            }

            if (var10 > 0.0F) {
                int var19 = Mth.clamp((int)(208.0F * var10), 0, 208);
                int var20 = (int)(150.0F + 80.0F * var8);
                var2.fill(var13 + 6, var12 + 8 + 12 - 4, var13 + 6 + var19, var12 + 8 + 12 - 1, var20 << 24 | 16724000);
                MutableComponent var21 = Component.translatable("entity.wakingworld.dark_mage.bar.lastword").withStyle(ChatFormatting.RED);
                var2.drawString(var3.font, var21, (var4 - var3.font.width(var21)) / 2, var12 + 28 + 1, -40896, true);
            }

            byte var27 = 60;
            int var28 = (var4 - var27) / 2;
            int var29 = var12 + 28 - 2;

            for (int var22 = 0; var22 < 4; var22++) {
                int var23 = var28 + var22 * 16;
                boolean var24 = var22 < var9;
                if (var24) {
                    int var25 = MageEntity.stageColour(var22 + 1);
                    boolean var26 = var22 == var9 - 1;
                    glow(var2, var23 + 6, var29 + 6, var25, var26 ? 0.55F + 0.35F * var8 : 0.3F, 1);
                    setColour(var25, 1.0F);
                    var2.blit(TEX, var23, var29, 0.0F, 64.0F, 12, 12, 256, 128);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                } else {
                    var2.blit(TEX, var23, var29, 14.0F, 64.0F, 12, 12, 256, 128);
                }
            }
        }
    }

    private static void setColour(int var0, float var1) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor((float)(var0 >> 16 & 0xFF) / 255.0F, (float)(var0 >> 8 & 0xFF) / 255.0F, (float)(var0 & 0xFF) / 255.0F, var1);
    }

    private static void glow(GuiGraphics var0, int var1, int var2, int var3, float var4, int var5) {
        int var6 = 16 * var5;
        setColour(var3, var4);
        var0.blit(TEX, var1 - var6 / 2, var2 - var6 / 2, var6, var6, 36.0F, 64.0F, 16, 16, 256, 128);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int lighten(int var0, float var1) {
        int var2 = var0 >> 16 & 0xFF;
        int var3 = var0 >> 8 & 0xFF;
        int var4 = var0 & 0xFF;
        var2 += (int)((float)(255 - var2) * var1);
        var3 += (int)((float)(255 - var3) * var1);
        var4 += (int)((float)(255 - var4) * var1);
        return 0xFF000000 | var2 << 16 | var3 << 8 | var4;
    }
}
