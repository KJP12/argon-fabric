package net.kjp12.argon.mixins;

import com.mojang.authlib.GameProfile;
import net.kjp12.argon.helpers.IClientConnection;
import net.kjp12.argon.helpers.IMinecraftServer;
import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;setWorld(Lnet/minecraft/world/World;)V"),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void argon$onPlayerConnect$setConnectionWorld(ClientConnection connection, ServerPlayerEntity player, CallbackInfo cbi,
                                                          GameProfile $0, UserCache $1, String $3, CompoundTag $4, RegistryKey<World> worldKey) {
        var subServer = ((IMinecraftServer) server).getSubServer(worldKey);
        ((IClientConnection) connection).setOwner(subServer);
        subServer.queueConnection(connection);
    }

    @Redirect(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;onSpawn()V"))
    private void argon$onPlayerConnect$onSpawn(ServerPlayerEntity self) {
        ((SubServerWorld) self.world).getSubServer().execute(self::onSpawn);
    }
}
