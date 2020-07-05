package net.kjp12.argon.mixins;

import com.mojang.authlib.GameProfile;
import net.kjp12.argon.helpers.IClientConnection;
import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinPlayerEntity extends PlayerEntity {
    @Shadow
    @Final
    public MinecraftServer server;

    @Shadow
    public ServerPlayNetworkHandler networkHandler;

    @Shadow
    public boolean notInAnyWorld;
    @Shadow
    @Final
    public ServerPlayerInteractionManager interactionManager;
    @Shadow
    private boolean inTeleportationState;
    @Shadow
    private boolean seenCredits;
    @Shadow
    @Nullable
    private Vec3d enteredNetherPos;
    @Shadow
    private int syncedExperience;
    @Shadow
    private float syncedHealth;
    @Shadow
    private int syncedFoodLevel;
    @Shadow
    private ChunkSectionPos cameraPosition;

    public MixinPlayerEntity(World world, BlockPos blockPos, GameProfile gameProfile) {
        super(world, blockPos, gameProfile);
    }

    @Shadow
    public abstract ServerWorld getServerWorld();

    @Shadow
    public abstract void refreshPositionAfterTeleport(double x, double y, double z);

    @Shadow
    protected abstract void dimensionChanged(ServerWorld targetWorld);
// TODO: Fix this up, the code breaks under all conditions./

    /**
     * @reason This is not able to be concurrently run due to locking methods from Portal Forcer and such.
     * @author KJP12
     */
    @Nullable
//    @Overwrite
    public Entity $$changeDimension(ServerWorld newWorld) {
        var oldWorld = getServerWorld();
        if (oldWorld == newWorld)
            // There's literally no need to execute this code.
            return this;
        inTeleportationState = true;
        var oldWorldKey = oldWorld.getRegistryKey();
        var newWorldKey = newWorld.getRegistryKey();
        var self = (ServerPlayerEntity) (Object) this;
        oldWorld.removePlayer(self);
        if (oldWorldKey == World.END && newWorldKey == World.OVERWORLD) {
            detach();
            if (!notInAnyWorld) {
                notInAnyWorld = true;
                networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_WON, seenCredits ? 0 : 1));
                seenCredits = true;
            }
            // The end doesn't own this player any more.
            // The player is not in the overworld either, so it isn't the owner.
            // The primary server thread will tick the connection instead.
            ((IClientConnection) networkHandler.connection).setOwner(null);
            return this;
        } else {
            var worldProperties = newWorld.getLevelProperties();
            networkHandler.sendPacket(new PlayerRespawnS2CPacket(newWorld.getDimensionRegistryKey(), newWorldKey, BiomeAccess.hashSeed(newWorld.getSeed()), interactionManager.getGameMode(), interactionManager.method_30119(), newWorld.isDebugWorld(), newWorld.isFlat(), true));
            networkHandler.sendPacket(new DifficultyS2CPacket(worldProperties.getDifficulty(), worldProperties.isDifficultyLocked()));
            var playerManager = server.getPlayerManager();
            playerManager.sendCommandTree(self);
            removed = true;
            double x = getX(), y = getY(), z = getZ();
            float p = pitch, w = yaw;
            if (newWorldKey == World.END) {
                var pos = ServerWorld.END_SPAWN_POS;
                x = pos.getX();
                y = pos.getY();
                z = pos.getZ();
                p = 0F;
                w = 90F;
            } else {
                if (newWorldKey == World.NETHER) {
                    enteredNetherPos = getPos();
                }

                DimensionType oldDim = oldWorld.getDimension(), newDim = newWorld.getDimension();
                if (oldDim.isShrunk() ^ newDim.isShrunk()) {
                    if (newDim.isShrunk()) {
                        x /= 8;
                        z /= 8;
                    } else {
                        x *= 8;
                        z *= 8;
                    }
                }
            }
            var worldBorder = newWorld.getWorldBorder();
            x = MathHelper.clamp(x,
                    Math.min(-2.9999872E7D, worldBorder.getBoundWest() + 16D),
                    Math.min(2.9999872E7D, worldBorder.getBoundEast() - 16D));
            z = MathHelper.clamp(z,
                    Math.min(-2.9999872E7D, worldBorder.getBoundNorth() + 16D),
                    Math.min(2.9999872E7D, worldBorder.getBoundSouth() - 16D));
            refreshPositionAndAngles(x, y, z, w, p);
            cameraPosition = ChunkSectionPos.from(MathHelper.floor(x) >> 4, MathHelper.floor(y) >> 4, MathHelper.floor(z) >> 4);
            var subServer = ((SubServerWorld) newWorld).getSubServer();
            ((IClientConnection) networkHandler.connection).setOwner(subServer);
            subServer.execute(() -> {
                if (newWorldKey == World.END) {
                    ServerWorld.createEndSpawnPlatform(newWorld);
                    refreshPositionAndAngles(Math.floor(getX()), Math.floor(getY()), Math.floor(getZ()), 90F, 0F);
                    setVelocity(Vec3d.ZERO);
                } else {
                    subServer.execute(() -> {
                        var portalForcer = newWorld.getPortalForcer();
                        if (!portalForcer.usePortal(this, yaw)) {
                            portalForcer.createPortal(this);
                            portalForcer.usePortal(this, yaw);
                        }
                    });
                }
                setWorld(newWorld);
                newWorld.onPlayerChangeDimension(self);
                dimensionChanged(newWorld);
                networkHandler.requestTeleport(getX(), getY(), getZ(), yaw, pitch);
                interactionManager.setWorld(newWorld);
                networkHandler.sendPacket(new PlayerAbilitiesS2CPacket(abilities));
                playerManager.sendWorldInfo(self, newWorld);
                playerManager.sendPlayerStatus(self);
                for (var status : getStatusEffects()) {
                    networkHandler.sendPacket(new EntityStatusEffectS2CPacket(getEntityId(), status));
                }
                networkHandler.sendPacket(new WorldEventS2CPacket(1032, BlockPos.ORIGIN, 0, false));
            });
            syncedExperience = -1;
            syncedHealth = -1;
            syncedFoodLevel = -1;
            return this;
        }
    }
}
