package net.kjp12.argon.mixins;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface AccessorServer {
    @Accessor
    long getLastTimeReference();

    @Accessor
    long getTimeReference();
}
