package net.kjp12.argon.mixins;

import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
    @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;playerTick()V"))
    private void argon$tick$playerTick(ServerPlayerEntity self) {
        ((SubServerWorld) self.getServerWorld()).getSubServer().execute(self::playerTick);
    }
}
