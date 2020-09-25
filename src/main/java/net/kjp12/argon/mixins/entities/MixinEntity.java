package net.kjp12.argon.mixins.entities;

import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow public World world;

    @Shadow public boolean removed;

    @Shadow public abstract void detach();

    @Shadow public abstract double getX();

    @Shadow public abstract double getY();

    @Shadow public abstract double getZ();

    @Shadow public float pitch;

    @Shadow public float yaw;

    @Shadow public abstract void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch);

    @Shadow public abstract void refreshPositionAfterTeleport(double x, double y, double z);

    @Shadow public abstract void setVelocity(Vec3d velocity);

    @Shadow public abstract void setWorld(World world);

    @Shadow public abstract Vec3d getLastNetherPortalDirectionVector();

    @Shadow protected abstract void method_30076();

    /**
     * @reason This is not able to be concurrently run due to blocking methods from Portal Forcer and such.
     * @author KJP12
     */
    @Nullable
    @Overwrite
    public Entity changeDimension(ServerWorld newWorld) {
        if (!(world instanceof ServerWorld) || removed) return null;
        var oldWorld = (ServerWorld) world;
        var self = (Entity) (Object) this;
        if (oldWorld == newWorld) {
            // There's literally no need to execute this code.
            return self;
        }
        detach();
        method_30076();
        var oldWorldKey = oldWorld.getRegistryKey();
        var newWorldKey = newWorld.getRegistryKey();
        ((SubServerWorld) newWorld).getSubServer().execute(() -> {
            float p = pitch, w = yaw;
            if (oldWorldKey == World.END && newWorldKey == World.OVERWORLD) {
                var pos = newWorld.getSpawnPos();
                int x = pos.getX(), z = pos.getZ();
                refreshPositionAfterTeleport(x, newWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            } else if (newWorldKey == World.END) {
                var pos = ServerWorld.END_SPAWN_POS;
                double x = pos.getX(), y = pos.getY(), z = pos.getZ();
                ServerWorld.createEndSpawnPlatform(newWorld);
                // For some reason, having refresh pos after teleport works while just refresh position and angles alone doesn't. Why?
                // We aren't dealing with the player, we might be able to get away with this.
                // refreshPositionAfterTeleport(x, y, z);
                refreshPositionAndAngles(x, y, z, 90F, 0F);
                setVelocity(Vec3d.ZERO);
            } else {
                double x = getX(), y = getY(), z = getZ();
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
                var worldBorder = newWorld.getWorldBorder();
                x = MathHelper.clamp(x,
                        Math.min(-2.9999872E7D, worldBorder.getBoundWest() + 16D),
                        Math.min(2.9999872E7D, worldBorder.getBoundEast() - 16D));
                z = MathHelper.clamp(z,
                        Math.min(-2.9999872E7D, worldBorder.getBoundNorth() + 16D),
                        Math.min(2.9999872E7D, worldBorder.getBoundSouth() - 16D));
                var vec = getLastNetherPortalDirectionVector();

                refreshPositionAndAngles(x, y, z, w, p);
                var portalForcer = newWorld.getPortalForcer();
                if (!portalForcer.usePortal(self, yaw)) {
                    // This deviates from Vanilla behaviour, but it's too late to cancel at this point.
                    portalForcer.createPortal(self);
                    portalForcer.usePortal(self, yaw);
                }
            }
            // This is required as the remove variable doesn't get properly reset with jumping threads.
            removed = false;
            // Reset the AI, we're reusing the entity object, so, this is needed.
            if(self instanceof LivingEntity)
                ((LivingEntity) self).getBrain().resetPossibleActivities();
            if(self instanceof MobEntity) {
                var me = (AccessorMobEntity) self;
                me.getGoalSelector().getRunningGoals().forEach(Goal::stop);
                me.getTargetSelector().getRunningGoals().forEach(Goal::stop);
            }
            setWorld(newWorld);
            newWorld.onDimensionChanged(self);
        });
        return self;
    }
}
