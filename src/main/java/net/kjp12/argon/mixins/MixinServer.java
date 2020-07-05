package net.kjp12.argon.mixins;

import net.kjp12.argon.concurrent.ArgonCompletable;
import net.kjp12.argon.concurrent.ArgonTask;
import net.kjp12.argon.helpers.IMinecraftServer;
import net.kjp12.argon.helpers.SubServer;
import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.SharedConstants;
import net.minecraft.command.DataCommandStorage;
import net.minecraft.entity.boss.BossBarManager;
import net.minecraft.server.*;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.function.CommandFunctionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestManager;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.util.snooper.SnooperListener;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.village.ZombieSiegeManager;
import net.minecraft.world.*;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.CatSpawner;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.PhantomSpawner;
import net.minecraft.world.gen.PillagerSpawner;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import static net.kjp12.argon.Argon.logger;

@Mixin(MinecraftServer.class)
public abstract class MixinServer extends ReentrantThreadExecutor<ServerTask> implements SnooperListener, CommandOutput, AutoCloseable, IMinecraftServer, Runnable {
    protected final Map<RegistryKey<World>, SubServer> worldThreads = new ConcurrentHashMap<>();
    @Shadow
    @Final
    protected SaveProperties saveProperties;
    @Shadow
    @Final
    protected RegistryTracker.Modifiable dimensionTracker;
    @Shadow
    @Final
    protected LevelStorage.Session session;
    @Shadow
    @Final
    private Executor workerExecutor;
    @Shadow
    @Final
    @Mutable
    private Map<RegistryKey<World>, ServerWorld> worlds;
    @Shadow
    private DataCommandStorage dataCommandStorage;
    @Shadow
    private PlayerManager playerManager;
    @Shadow
    @Final
    private BossBarManager bossBarManager;
    @Shadow
    private long timeReference;
    @Shadow
    private boolean forceGameMode;
    @Shadow
    private Profiler profiler;
    @Shadow
    @Final
    private CommandFunctionManager commandFunctionManager;
    @Shadow
    @Final
    private ServerNetworkIo networkIo;
    @Shadow
    @Final
    private List<Runnable> serverGuiTickables;

    @Shadow
    private static native void setupSpawn(ServerWorld serverWorld, ServerWorldProperties serverWorldProperties, boolean bl, boolean bl2, boolean bl3);

    @Shadow
    public abstract CrashReport populateCrashReport(CrashReport report);

    @Shadow
    protected abstract void initScoreboard(PersistentStateManager persistentStateManager);

    @Shadow
    protected abstract void setToDebugWorldProperties(SaveProperties properties);

    @Shadow
    public abstract ServerWorld getOverworld();

    @Shadow
    protected abstract void method_16208();

    @Shadow
    protected abstract void updateMobSpawnOptions();

    @Shadow
    private volatile boolean running;

    @Shadow
    private long lastTimeReference;

    @Shadow
    private boolean stopped;

    public MixinServer(String string) {
        super(string);
    }

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void argon$init$concurrentWorldsMap(CallbackInfo cbi) {
        worlds = new ConcurrentHashMap<>();
    }

    /**
     * Overhaul the createWorlds method to be concurrent.
     *
     * @reason Concurrency model doesn't fit inside the serial model of the overwritten method.
     * Injectors and Redirects would be hell for this, as there's no way to null route all methods properly.
     * @author KJP12
     */
    @Overwrite
    public void createWorlds(WorldGenerationProgressListener progressListener) {
        var worldProperties = saveProperties.getMainWorldProperties();
        var generatorOpts = saveProperties.getGeneratorOptions();
        boolean debug = generatorOpts.isDebugWorld();
        long seed = generatorOpts.getSeed();
        long biomeSeed = BiomeAccess.hashSeed(seed);

        var dimensionMap = generatorOpts.getDimensionMap();
        var worldBorder = new ArgonCompletable<WorldBorder>();
        {
            var spawners = List.of(new PhantomSpawner(), new PillagerSpawner(), new CatSpawner(), new ZombieSiegeManager(), new WanderingTraderManager(worldProperties));
            var dimensionOpts = dimensionMap.get(DimensionOptions.OVERWORLD);
            ChunkGenerator chunkGenerator;
            DimensionType dimensionType;
            if (dimensionOpts == null) {
                dimensionType = DimensionType.getOverworldDimensionType();
                chunkGenerator = GeneratorOptions.createOverworldGenerator(new Random().nextLong());
            } else {
                dimensionType = dimensionOpts.getDimensionType();
                chunkGenerator = dimensionOpts.getChunkGenerator();
            }
            var registryKey = dimensionTracker.getDimensionTypeRegistry().getKey(dimensionType).orElseThrow(() -> new IllegalStateException("Unregistered dimension type: " + dimensionType));
            var subServer = new SubServer((MinecraftServer) (Object) this, World.OVERWORLD, new ArgonTask<>(workerExecutor, () -> new ServerWorld((MinecraftServer) (Object) this, workerExecutor, session, worldProperties, World.OVERWORLD, registryKey, dimensionType, progressListener, chunkGenerator, debug, biomeSeed, spawners, true)));
            worldThreads.put(World.OVERWORLD, subServer);
            subServer.awaitWorld.whenComplete(world -> {
                worlds.put(World.OVERWORLD, world);
                var stateManager = world.getPersistentStateManager();
                execute(() -> initScoreboard(stateManager)); // Unsafe unsynchronized
                dataCommandStorage = new DataCommandStorage(stateManager);
                var border = world.getWorldBorder();
                border.load(worldProperties.getWorldBorder());
                worldBorder.complete(border);
                if (!worldProperties.isInitialized()) {
                    try {
                        setupSpawn(world, worldProperties, generatorOpts.hasBonusChest(), debug, true);
                        worldProperties.setInitialized(true);
                        if (debug) setToDebugWorldProperties(saveProperties);
                    } catch (Throwable t) {
                        var report = CrashReport.create(t.fillInStackTrace(), "Exception initializing level");
                        try {
                            world.addDetailsToCrashReport(report);
                        } catch (Throwable t2) {
                            report.addElement("Failed to add details of Server World").add("Details", t2);
                        }
                        var exception = new CrashException(report);
                        execute(() -> {
                            throw exception;
                        });
                        throw exception;
                    }
                }
                playerManager.setMainWorld(world);
                if (saveProperties.getCustomBossEvents() != null) {
                    bossBarManager.fromTag(saveProperties.getCustomBossEvents());
                }
            }, throwable -> {
                var report = populateCrashReport(CrashReport.create(throwable.fillInStackTrace(), "[ARGON] Error fetching " + World.OVERWORLD.getValue()));
                var section = report.addElement("Properties");
                section.add("Seed", Long.toString(seed));
                section.add("Biome Seed", Long.toString(biomeSeed));
                section.add("Spawn Handlers", spawners);
                // TODO: Add worldProperties, generatorOptions, dimensionOptions
                section.add("Dimension Type", dimensionType);
                section.add("Dimension Registry Key", registryKey);
                execute(() -> {
                    throw new CrashException(report);
                });
            });
        }

        for (var entry : dimensionMap.getEntries()) {
            var dimensionKey = entry.getKey();
            if (dimensionKey == DimensionOptions.OVERWORLD) continue;
            var dimensionOpts = (DimensionOptions) entry.getValue();
            var worldKey = RegistryKey.of(Registry.DIMENSION, dimensionKey.getValue());
            var dimensionType = entry.getValue().getDimensionType();
            var registryKey = dimensionTracker.getDimensionTypeRegistry().getKey(dimensionType).orElseThrow(() -> new IllegalStateException("Unregistered dimension type: " + dimensionType));
            var chunkGenerator = dimensionOpts.getChunkGenerator();
            // TODO: Replace this with a lenient LevelProperties that can be saved independently.
            var levelProperties = new UnmodifiableLevelProperties(saveProperties, worldProperties);
            var subServer = new SubServer((MinecraftServer) (Object) this, worldKey, new ArgonTask<>(workerExecutor, () -> new ServerWorld((MinecraftServer) (Object) this, workerExecutor, session, levelProperties, worldKey, registryKey, dimensionType, progressListener, chunkGenerator, debug, biomeSeed, Collections.emptyList(),
                    // TODO: Allow hook-in on "hasTime" to allow for dimensions with independent time and weather.
                    false)));
            worldThreads.put(worldKey, subServer);
            subServer.awaitWorld.whenComplete(world -> {
                worldBorder.whenComplete(border -> new WorldBorderListener.WorldBorderSyncer(world.getWorldBorder()), it -> logger.error("Error fetching world border", it));
                worlds.put(worldKey, world);
            }, throwable -> {
                var report = populateCrashReport(CrashReport.create(throwable.fillInStackTrace(), "[ARGON] Error fetching " + World.OVERWORLD.getValue()));
                var section = report.addElement("Properties");
                section.add("Seed", Long.toString(seed));
                section.add("Biome Seed", Long.toString(biomeSeed));
                // TODO: Add worldProperties, generatorOptions, dimensionOptions
                section.add("Dimension Type", dimensionType);
                section.add("Dimension Registry Key", registryKey);
                execute(() -> {
                    throw new CrashException(report);
                });
            });
        }
    }

    /**
     * Overhaul the prepareStartRegion method to be concurrent.
     *
     * @reason Concurrency model doesn't fit inside the serial model of the overwritten method.
     * Injectors and Redirects would be hell for this, as there's no way to null route all methods properly.
     * Along with that, this method will fail fast to CMEs, which will crash the server immediately.
     * @author KJP12
     */
    @Overwrite
    private void prepareStartRegion(WorldGenerationProgressListener progressListener) {
        var overworld = worldThreads.get(World.OVERWORLD);
        overworld.execute(() -> {
            var world = overworld.getWorld();
            logger.info("Preparing start region for dimension {} (or rather, skip them)", world.getDimensionRegistryKey().getValue());
            var pos = world.getSpawnPos();
            var cpos = new ChunkPos(pos);
            progressListener.start(cpos);
            /*var chunkManager = world.getChunkManager();
            chunkManager.getLightingProvider().setTaskBatchSize(500);
            timeReference = Util.getMeasuringTimeMs();
            chunkManager.addTicket(ChunkTicketType.START, cpos, 11, Unit.INSTANCE);
            while(chunkManager.getTotalChunksLoadedCount() < 441) {
                timeReference = Util.getMeasuringTimeMs() + 10L;
                overworld.runServerTasks();
            }
            timeReference = Util.getMeasuringTimeMs() + 10L;
            overworld.runServerTasks();
            chunkManager.getLightingProvider().setTaskBatchSize(5);*/
            progressListener.stop();
        });
        for (var world : worldThreads.values()) {
            world.execute(() -> {
                var w = world.getWorld();
                var forcedChunkState = w.getPersistentStateManager().get(ForcedChunkState::new, "chunks");
                if (forcedChunkState == null) return;
                var chunkManager = w.getChunkManager();
                var itr = forcedChunkState.getChunks().iterator();
                while (itr.hasNext()) chunkManager.setChunkForced(new ChunkPos(itr.nextLong()), true);
            });
        }
        timeReference = Util.getMeasuringTimeMs() + 10L;
        method_16208();
        updateMobSpawnOptions();
    }

    @Redirect(method = "save(ZZZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;save(Lnet/minecraft/util/ProgressListener;ZZ)V"))
    private void argon$tick$concurrentSave(ServerWorld self, ProgressListener $0, boolean $1, boolean $2) {
        if (running && !stopped) {
            ((SubServerWorld) self).getSubServer().execute(() -> self.save($0, $1, $2));
        } else {
            self.save($0, $1, $2);
        }
    }

    @Redirect(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickWorlds(Ljava/util/function/BooleanSupplier;)V"))
    private void argon$tick$tickWorldsRedirector(MinecraftServer self, BooleanSupplier shouldKeepTicking) {
        profiler.push("commandFunctions");
        commandFunctionManager.tick();
        profiler.swap("\uD83E\uDD80");
        profiler.swap("connection");
        networkIo.tick();
        profiler.swap("players");
        playerManager.updatePlayerLatency();
        if (SharedConstants.isDevelopment) TestManager.INSTANCE.tick();
        profiler.swap("server gui refresh");
        serverGuiTickables.forEach(Runnable::run);
        profiler.pop();
    }

    @Redirect(method = "method_20415()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;shouldKeepTicking()Z"))
    private boolean argon$shouldKeepTicking$no(MinecraftServer self) {
        return false;
    }

    @Inject(method = "shutdown()V", at = @At(value = "HEAD"))
    private void argon$shutdown$ensureThreadsStop(CallbackInfo cbi) {
        running = false;
        for (var sub : worldThreads.values()) {
            var t = sub.getThread();
            for (int i = 0; i < 60 && t.isAlive(); i++)
                try {
                    t.interrupt();
                    t.join(50L);
                } catch (InterruptedException ignore) {
                }
            if (t.isAlive()) {
                logger.warn("World {}/{} refused to stop after 3 seconds, forcefully stopping thread.", t, sub.getWorld());
                t.stop();
            }
        }
    }

    public long getLastTimeReference() {
        return lastTimeReference;
    }

    public long getTimeReference() {
        return timeReference;
    }

    public SubServer getSubServer(RegistryKey<World> key) {
        return worldThreads.get(key);
    }

    public Collection<SubServer> getSubServers() {
        return worldThreads.values();
    }
}
