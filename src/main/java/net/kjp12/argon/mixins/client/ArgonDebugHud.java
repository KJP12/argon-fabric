package net.kjp12.argon.mixins.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kjp12.argon.helpers.IMinecraftServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.AffineTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.MetricsData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mojang.blaze3d.systems.RenderSystem.*;
import static net.minecraft.client.gui.DrawableHelper.fill;

@Mixin(GameRenderer.class)
@Environment(EnvType.CLIENT)
public class ArgonDebugHud {
    @Shadow
    @Final
    private MinecraftClient client;

    private static void blend(float[] o, float a, int c) {
        var div = (a /= 255F) + (o[0] /= 255F);
        o[1] = (((c >> 16 & 255) * a) + (o[1] * o[0])) / div;
        o[2] = (((c >> 8 & 255) * a) + (o[2] * o[0])) / div;
        o[3] = (((c & 255) * a) + (o[3] * o[0])) / div;
        o[0] = 255 * (a + (o[0] * (1 - a)));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void argon$renderDebugHud(CallbackInfo cbi) {
        if (client.options.debugEnabled) return; // We shouldn't override Minecraft's debugger.
        var stack = new MatrixStack(); // we're going to force a render even if it crashes.
        var strings = new String[]{"Argon Debugger", client.fpsDebugString, String.format("Server: %s", client.getServer())};
        for (int i = 0; i < strings.length; i++) {
            int k = client.textRenderer.getWidth(strings[i]);
            int m = 2 + 9 * i;
            fill(stack, 1, m - 1, 3 + k, m + 8, 0x90101010);
            client.textRenderer.draw(stack, strings[i], 2.0F, m, 0x00C564FF);
        }
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        argon$drawMetricsData(stack, client.metricsData, 0, sh, client.options.maxFps, "Frame Rate", "FPS");
        var server = client.getServer();
        if (server != null) {
            int i = 0;
            for (var sub : ((IMinecraftServer) server).getSubServers())
                argon$drawMetricsData(stack, sub.metricsData, sw - 240, sh - (i++ * 60), 20, sub.toString(), "TPS");
        }
    }

    private void argon$drawMetricsData(MatrixStack stack, MetricsData data, int x, int y, int expected, String to, String unit) {
        long[] samples = data.getSamples();
        int old = data.getStartIndex();
        long max = Integer.MIN_VALUE, min = Integer.MAX_VALUE, total = 0L;
        for (long sample : samples) {
            long s = sample / 1000000L;
            max = Math.max(max, s);
            min = Math.min(min, s);
            total += s;
        }
        int divider = Math.max((int) Math.max(max, 1000 / expected) / 60, 1);
        fill(stack, x, y - 60, x + 240, y, 0x90101010);
        int len = client.textRenderer.getWidth(to);
        fill(stack, x, y - 60, x + len, y - 50, 0x90505050);
        client.textRenderer.draw(stack, to, x + 1, y - 59, 0x007983FC);
        var buffer = Tessellator.getInstance().getBuffer();
        enableBlend();
        disableTexture();
        defaultBlendFunc();
        buffer.begin(7, VertexFormats.POSITION_COLOR);
        var matrix4f = AffineTransformation.identity().getMatrix();
        var c = new float[4];
        var il = 0;
        for (int i = 0; i < samples.length; i++) {
            var s = samples[i] / 1000000L;
            if (il != s) {
                int warn = expected / 2;
                c[0] = 0xFF;
                if (s < warn) {
                    c[1] = 0xC9;
                    c[2] = 0xB5;
                    c[3] = 0xB4;
                    blend(c, s / (float) warn, 0xC564FF);
                } else {
                    c[1] = 0xC5;
                    c[2] = 0x64;
                    c[3] = 0xFF;
                    blend(c, (s - warn) / (float) (s - warn), 0xFA5803);
                }
            }
            int l = (int) s / divider;
            float r = c[1], g = c[2], b = c[3], a = (240 - i + old) % 240;
            buffer.vertex(matrix4f, x + i + 1, y, 0).color(r, g, b, a).next();
            buffer.vertex(matrix4f, x + i + 1, y - l + 1, 0).color(r, g, b, a).next();
            buffer.vertex(matrix4f, x + i, y - l + 1, 0).color(r, g, b, a).next();
            buffer.vertex(matrix4f, x + i, y, 0).color(r, g, b, a).next();
        }
        buffer.end();
        BufferRenderer.draw(buffer);
        enableTexture();
        disableBlend();
        var str = expected + " " + unit;
        int l = expected / divider;
        len = client.textRenderer.getWidth(str);
        fill(stack, x + 1, y - l + 1, x + len, y - l + 10, 0x90505050);
        client.textRenderer.draw(stack, str, x + 2, y - l + 2, 0x007983FC);
        fill(stack, x, y - l, x + 240, y - l + 1, 0xFFFFFFFF);
    }

}
