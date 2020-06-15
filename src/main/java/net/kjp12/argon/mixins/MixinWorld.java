package net.kjp12.argon.mixins;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class MixinWorld extends World implements ServerWorldAccess {
    protected final Queue<BlockEntity>
            argon$queue$blockEntities = new ConcurrentLinkedQueue<>();
    protected final List<BlockEntity>
            argon$frozen$blockEntities = new ArrayList<>(),
            argon$sleep$blockEntities = new ArrayList<>(),
            argon$inactive$blockEntities = new ArrayList<>(),
            argon$active$blockEntities = new ArrayList<>(),
            argon$critical$blockEntities = new ArrayList<>();
    protected final Queue<Entity>
            argon$queue$entities = new ConcurrentLinkedQueue<>();
    protected final List<Entity>
            argon$frozen$entities = new ArrayList<>(),
            argon$sleep$entities = new ArrayList<>(),
            argon$inactive$entities = new ArrayList<>(),
            argon$active$entities = new ArrayList<>(),
            argon$critical$entities = new ArrayList<>();
    protected final Profiler argon$secondaryProfiler = DummyProfiler.INSTANCE, argon$criticalProfiler = DummyProfiler.INSTANCE;
    protected volatile long
            argon$secondaryTimeReference, argon$secondaryLastTimeReference, argon$secondaryTicks,
            argon$criticalTimeReference, argon$criticalLastTimeReference, argon$criticalTicks;
    @Shadow
    @Final
    private MinecraftServer server;
    protected final Thread
            argon$secondaryTicker = new Thread(() -> {
        while (server.isRunning()) {
            long l = Util.getMeasuringTimeMs() - argon$secondaryTimeReference;
            if (l > 2000L && argon$secondaryTimeReference - argon$secondaryLastTimeReference >= 15000L) {
                long ticks = l / 50L;
                LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind!", l, ticks);
                argon$secondaryTimeReference += 50L + (ticks * 50L);
                argon$secondaryLastTimeReference = argon$secondaryTimeReference;
                // TODO: Vectorized-tick logic.
            } else {
                argon$secondaryTimeReference += 50L;
            }
        }
    }, "[ARGON] " + getDimension().toString() + " Secondary Ticker"),
            argon$criticalTicker = new Thread(() -> {
            }, "[ARGON] " + getDimension().toString() + " Critical Ticker");

    protected MixinWorld(MutableWorldProperties mutableWorldProperties, RegistryKey<World> registryKey, RegistryKey<DimensionType> registryKey2, DimensionType dimensionType, Supplier<Profiler> profiler, boolean bl, boolean bl2, long l) {
        super(mutableWorldProperties, registryKey, registryKey2, dimensionType, profiler, bl, bl2, l);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    public void argon$init(CallbackInfo cbi) {
        argon$secondaryTimeReference =
                argon$criticalTimeReference = server.getServerStartTime();
        argon$secondaryTicks =
                argon$criticalTicks = server.getTicks();
    }
}
