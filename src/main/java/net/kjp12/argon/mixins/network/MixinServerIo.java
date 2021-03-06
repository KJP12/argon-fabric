package net.kjp12.argon.mixins.network;

import net.kjp12.argon.helpers.IClientConnection;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.ServerNetworkIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerNetworkIo.class)
public class MixinServerIo {
    @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;tick()V"))
    private void argon$tick$clientTickRedirect(ClientConnection self) {
        if (((IClientConnection) self).isOwnedByWorld()) return;
        assert ((IClientConnection) self).getOwner() == null : "Method lied?";
        self.tick();
    }
}
