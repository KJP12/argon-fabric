package net.kjp12.argon.mixins;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
    @Shadow
    @Final
    public ClientConnection connection;

    @Shadow
    public ServerPlayerEntity player;
/*
    @Inject(method = "tick()V", at = @At(value = "HEAD"))
    private void argon$tick$playerTick(CallbackInfo cbi) {
        if (!((IClientConnection) connection).getOwner().isOnThread())
            logger.warn("Player {} is owned by {}", player, ((IClientConnection) connection).getOwner().getThread());
    }*/
}
