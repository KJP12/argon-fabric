package net.kjp12.argon.mixins.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kjp12.argon.helpers.IMinecraftServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.AffineTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.MetricsData;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.profiler.EmptyProfileResult;
import net.minecraft.util.profiler.ProfileResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static com.mojang.blaze3d.systems.RenderSystem.*;
import static net.kjp12.argon.Argon.*;
import static net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC;
import static net.minecraft.client.gui.DrawableHelper.fill;

@Mixin(GameRenderer.class)
@Environment(EnvType.CLIENT)
public class ArgonDebugHud {
    private static final Quaternion QY75 = Vector3f.POSITIVE_Z.getDegreesQuaternion(-75F);
    @Shadow
    @Final
    private MinecraftClient client;
    private int b;

    private static void blend(float[] o, float a, int c) {
        var div = (a /= 255F) + (o[0] /= 255F);
        o[1] = (((c >> 16 & 255) * a) + (o[1] * o[0])) / div;
        o[2] = (((c >> 8 & 255) * a) + (o[2] * o[0])) / div;
        o[3] = (((c & 255) * a) + (o[3] * o[0])) / div;
        o[0] = 255 * (a + (o[0] * (1 - a)));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void argon$renderDebugHud(CallbackInfo cbi) {
        // We shouldn't override Minecraft's debug screen.
        // May have the odd side effect of occasionally flashing up on top of everything else as this is rendered after literally everything on the client.
        if (client.options.debugEnabled) return;
        var stack = new MatrixStack(); // We're going to force a render even if it crashes.
        var strings = new String[]{"Argon Debugger", client.fpsDebugString, "Server: " + client.getServer()};
        var buffer = Tessellator.getInstance().getBuffer();
        var matrix4f = AffineTransformation.identity().getMatrix();
        int sw = client.getWindow().getScaledWidth(), sh = client.getWindow().getScaledHeight();
        for (int i = 0; i < strings.length; i++) {
            int w = client.textRenderer.getWidth(strings[i]), n = 10 * i;
            fill(stack, 2, 2 + n, 2 + w, 2 + n + 10, 0x90101010);
            client.textRenderer.draw(stack, strings[i], 2F, 2 + n + 1, 0x00C564FF);
        }
        argon$drawMetricsData(buffer, matrix4f, stack, client.metricsData, 0, sh, client.options.maxFps, "Frame Rate", "FPS");
        var server = client.getServer();
        if (server != null) {
            int i = 0;
            //var aBlock = Blocks.DIRT.getDefaultState();
            // var grassModel = client.getBlockRenderManager().getModel(aBlock);
            if (serverToProfile != null) {
                argon$drawMetricsData(buffer, matrix4f, stack, serverToProfile.metricsData, sw - 240, sh, 20, serverToProfile.toString(), "TPS");
                if(profileResult != null) argon$drawProfilerResults(stack, profileResult);
            } else {
                for (var sub : ((IMinecraftServer) server).getSubServers()) {
                    argon$drawMetricsData(buffer, matrix4f, stack, sub.metricsData, sw - 240, sh - (i++ * 60), 20, sub.toString(), "TPS");
                    /*
                    long ntr = Util.getMeasuringTimeMs(), ltr = sub.getLastTimeReference(), tr = sub.getTimeReference();
                    pushMatrix();
                    translatef(sw - 252F, sh - (i - 1 * 60F) + 2F, 1050F);
                    scalef(1F, 1F, -1F);
                    stack.push();
                    stack.translate(sw - 12D, 10D + (i * 10D), 1000D);
                    stack.scale(10F, 10F, 10F);
                    int ttr = (int) (tr - ntr);
                    var q = Vector3f.POSITIVE_Y.getDegreesQuaternion(/*(MathHelper.lerpAngleDegrees(Math.max(0, ttr) / 50F, .15F * ltr, .5F * tr))*//* b++ % 360F);
                    q.hamiltonProduct(QY75);
                    stack.multiply(q);
                    var immediate = VertexConsumerProvider.immediate(buffer);
                    var buf = immediate.getBuffer(RenderLayers.method_29359(aBlock));
                    client.getBlockRenderManager().getModelRenderer().render(stack.peek(), ttr < 0 ? buf.color(255, 0, 0, 255) : buffer, aBlock, grassModel, 0, 0, 0, 15728880, OverlayTexture.DEFAULT_UV);
                    immediate.draw();
                    stack.pop();
                    popMatrix();
                    */
                }
            }
        }
    }

    private void argon$drawMetricsData(BufferBuilder buffer, Matrix4f matrix4f, MatrixStack stack, MetricsData data, int x, int y, int expected, String to, String unit) {
        long[] samples = data.getSamples();
        int old = data.getStartIndex();
        long max = Integer.MIN_VALUE, min = Integer.MAX_VALUE, total = 0L;
        for (long sample : samples) {
            long s = sample / 1000000L;
            max = Math.max(max, s);
            min = Math.min(min, s);
            total += s;
        }
        fill(stack, x, y - 50, x + 240, y, 0x90101010);
        enableBlend();
        disableTexture();
        defaultBlendFunc();
        float divid = max / 50F, exp = 1000F / expected;
        buffer.begin(7, VertexFormats.POSITION_COLOR);
        var c = new float[4];
        var il = 0;
        for (int i = 0; i < samples.length; i++) {
            var s = samples[i] / 1000000L;
            if (il != s) {
                int warn = (int) (exp / 2F);
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
                    blend(c, (s - warn) / (float) warn, 0xFA5803);
                }
            }
            int l = (int) (s / divid);
            int r = MathHelper.floor(c[1] * 255F), g = MathHelper.floor(c[2] * 255F), b = MathHelper.floor(c[3] * 255F), a = 255 - ((240 - i + old) % 240);
            buffer.vertex(matrix4f, x + i + 1, y, 0).color(r, g, b, a).next();
            buffer.vertex(matrix4f, x + i + 1, y - l + 1, 0).color(r, g, b, a).next();
            buffer.vertex(matrix4f, x + i, y - l + 1, 0).color(r, g, b, a).next();
            buffer.vertex(matrix4f, x + i, y, 0).color(r, g, b, a).next();
        }
        buffer.end();
        BufferRenderer.draw(buffer);
        enableTexture();
        disableBlend();
        String e = expected + " " + unit;
        int l = Math.min((int) (exp / divid), 50),
                tw = client.textRenderer.getWidth(to),
                ew = client.textRenderer.getWidth(e);
        fill(stack, x, y - l, x + 240, y - l + 1, 0xFFFFFFFF);
        fill(stack, x, y - 60, x + tw, y - 50, 0x90505050);
        fill(stack, x, y - l + 1, x + ew, y - l + 10, 0x90505050);

        client.textRenderer.draw(stack, to, x, y - 60, 0x7983FC);
        client.textRenderer.draw(stack, e, x, y - l + 2, 0x7983FC);
    }

    private void argon$drawProfilerResults(MatrixStack matrixStack, ProfileResult profileResult) {
        if(profileResult instanceof EmptyProfileResult) return;
        var list = profileResult.getTimings(profileSection);
        var profilerTiming = list.remove(0);
        int fbw = client.getWindow().getFramebufferWidth(), fbh = client.getWindow().getFramebufferHeight();
        clear(256, IS_SYSTEM_MAC);
        matrixMode(5889);
        loadIdentity();
        ortho(0.0D, fbw, fbh, 0.0D, 1000.0D, 3000.0D);
        matrixMode(5888);
        loadIdentity();
        translatef(0.0F, 0.0F, -2000.0F);
        lineWidth(1.0F);
        disableTexture();
        var tessellator = Tessellator.getInstance();
        var buffer = tessellator.getBuffer();
        int j = fbw - 170, k = fbh - 320;
        enableBlend();
        buffer.begin(7, VertexFormats.POSITION_COLOR);
        buffer.vertex(j - 176, k - 96F - 16F, 0.0D).color(200, 0, 0, 0).next();
        buffer.vertex(j - 176, k + 320, 0.0D).color(200, 0, 0, 0).next();
        buffer.vertex(j + 176, k + 320, 0.0D).color(200, 0, 0, 0).next();
        buffer.vertex(j + 176, k - 96F - 16F, 0.0D).color(200, 0, 0, 0).next();
        tessellator.draw();
        disableBlend();
        double d = 0.0D;

        int v;
        for (var profilerTiming2 : list) {
            int l = MathHelper.floor(profilerTiming2.parentSectionUsagePercentage / 4.0D) + 1;
            buffer.begin(6, VertexFormats.POSITION_COLOR);
            v = profilerTiming2.getColor();
            int n = v >> 16 & 255,  o = v >> 8 & 255,  p = v & 255;
            buffer.vertex(j, k, 0.0D).color(n, o, p, 255).next();

            int r;
            float s, t, u;
            for (r = l; r >= 0; --r) {
                s = (float) ((d + profilerTiming2.parentSectionUsagePercentage * (double) r / (double) l) * Math.PI * 2D / 100.0D);
                t = MathHelper.sin(s) * 160.0F;
                u = MathHelper.cos(s) * 160.0F * 0.5F;
                buffer.vertex(j + t, k - u, 0.0D).color(n, o, p, 255).next();
            }

            tessellator.draw();
            buffer.begin(5, VertexFormats.POSITION_COLOR);

            for (r = l; r >= 0; --r) {
                s = (float) ((d + profilerTiming2.parentSectionUsagePercentage * (double) r / (double) l) * Math.PI * 2D / 100.0D);
                t = MathHelper.sin(s) * 160.0F;
                u = MathHelper.cos(s) * 160.0F * 0.5F;
                if (u <= 0.0F) {
                    buffer.vertex(j + t, k - u, 0.0D).color(n >> 1, o >> 1, p >> 1, 255).next();
                    buffer.vertex(j + t, k - u + 10.0F, 0.0D).color(n >> 1, o >> 1, p >> 1, 255).next();
                }
            }

            tessellator.draw();
            d += profilerTiming2.parentSectionUsagePercentage;
        }

        DecimalFormat decimalFormat = new DecimalFormat("##0.00");
        decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
        enableTexture();
        String string = ProfileResult.getHumanReadableName(profilerTiming.name);
        String string2 = "";
        if (!"unspecified".equals(string)) {
            string2 = string2 + "[0] ";
        }

        if (string.isEmpty()) {
            string2 = string2 + "ROOT ";
        } else {
            string2 = string2 + string + ' ';
        }

        client.textRenderer.drawWithShadow(matrixStack, string2, (float)(j - 160), (float)(k - 80 - 16), 16777215);
        string2 = decimalFormat.format(profilerTiming.totalUsagePercentage) + "%";
        client.textRenderer.drawWithShadow(matrixStack, string2, (float)(j + 160 - client.textRenderer.getWidth(string2)), (float)(k - 80 - 16), 16777215);

        for(int w = 0; w < list.size(); ++w) {
            var profilerTiming3 = list.get(w);
            var stringBuilder = new StringBuilder();
            if ("unspecified".equals(profilerTiming3.name)) {
                stringBuilder.append("[?] ");
            } else {
                stringBuilder.append("[").append(w + 1).append("] ");
            }

            String string3 = stringBuilder.append(profilerTiming3.name).toString();
            client.textRenderer.drawWithShadow(matrixStack, string3, (float)(j - 160), (float)(k + 80 + w * 8 + 20), profilerTiming3.getColor());
            string3 = decimalFormat.format(profilerTiming3.parentSectionUsagePercentage) + "%";
            client.textRenderer.drawWithShadow(matrixStack, string3, (float)(j + 160 - 50 - client.textRenderer.getWidth(string3)), (float)(k + 80 + w * 8 + 20), profilerTiming3.getColor());
            string3 = decimalFormat.format(profilerTiming3.totalUsagePercentage) + "%";
            client.textRenderer.drawWithShadow(matrixStack, string3, (float)(j + 160 - client.textRenderer.getWidth(string3)), (float)(k + 80 + w * 8 + 20), profilerTiming3.getColor());
        }

    }
}
