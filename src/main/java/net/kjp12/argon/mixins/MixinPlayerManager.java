package net.kjp12.argon.mixins;

import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {
    @Redirect(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;onSpawn()V"))
    private void argon$onPlayerConnect$onSpawn(ServerPlayerEntity self) {
        ((SubServerWorld) self.world).getSubServer().execute(self::onSpawn);
    }
}
