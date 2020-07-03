package net.kjp12.argon.mixins.world;

import net.kjp12.argon.TickingState;
import net.kjp12.argon.helpers.SubServer;
import net.kjp12.argon.helpers.SubServerWorld;
import net.kjp12.argon.utils.Ticker;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class MixinWorld extends World implements ServerWorldAccess, SubServerWorld {
    protected final Queue<BlockEntity> argon$blockEntitiesQueue = new ConcurrentLinkedQueue<>();
    protected final Queue<Entity> argon$queue$entities = new ConcurrentLinkedQueue<>();
    protected final Map<TickingState, Ticker> argon$tickers = new EnumMap<>(TickingState.class);
    protected SubServer subServer;
    @Shadow
    @Final
    private MinecraftServer server;

    protected MixinWorld(MutableWorldProperties mutableWorldProperties, RegistryKey<World> registryKey, RegistryKey<DimensionType> registryKey2, DimensionType dimensionType, Supplier<Profiler> profiler, boolean bl, boolean bl2, long l) {
        super(mutableWorldProperties, registryKey, registryKey2, dimensionType, profiler, bl, bl2, l);
    }

    /*@Redirect(method = "<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorage$Session;Lnet/minecraft/world/level/ServerWorldProperties;Lnet/minecraft/util/registry/RegistryKey;Lnet/minecraft/util/registry/RegistryKey;Lnet/minecraft/world/dimension/DimensionType;Lnet/minecraft/server/WorldGenerationProgressListener;Lnet/minecraft/world/gen/chunk/ChunkGenerator;ZJLjava/util/List;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getProfiler()Lnet/minecraft/util/profiler/Profiler;"))
    private static Profiler argon$init$argonProfiler(MinecraftServer self) {
        return subServer.getProfiler();
    }*/

    @Inject(method = "<init>", at = @At("TAIL"))
    public void argon$init(CallbackInfo cbi) {
    }

    @Override
    public SubServer getSubServer() {
        return subServer;
    }

    @Override
    public void setSubServer(SubServer subServer) {
        this.subServer = subServer;
    }

    @Override
    public Profiler getProfiler() {
        return subServer.profiler;
    }
}
