package net.kjp12.argon.mixins;

import net.kjp12.argon.helpers.IClientConnection;
import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinPlayerEntity extends Entity {
    @Shadow
    @Final
    public MinecraftServer server;

    @Shadow
    public ServerPlayNetworkHandler networkHandler;

    public MixinPlayerEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow
    public abstract Entity changeDimension(ServerWorld destination);

    @Inject(method = "changeDimension", at = @At("RETURN"), cancellable = true)
    private void argon$changeDimension$migrateIo(ServerWorld world, CallbackInfoReturnable<Entity> cbir) {
        // This is the least brittle method that hands off the connection properly.
        // TODO: Create a queue in SubServer using Player.
        var subServer = ((SubServerWorld) world).getSubServer();
        var connection = (IClientConnection) networkHandler.connection;
        connection.setOwner(subServer);
    }
}
