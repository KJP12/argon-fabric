package net.kjp12.argon.mixins.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.profiler.ProfileResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface AccessorClient {
    @Invoker
    void invokeDrawProfilerResults(MatrixStack matrixStack, ProfileResult profileResult);
}
