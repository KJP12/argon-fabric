package net.kjp12.argon.helpers;

import net.kjp12.argon.concurrent.ArgonTask;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

import static net.kjp12.argon.Argon.logger;

public class SubServer extends ReentrantThreadExecutor<ServerTask> implements Runnable {
    private final MinecraftServer server;
    private final Thread thread;
    public Profiler profiler = DummyProfiler.INSTANCE;
    public ArgonTask<ServerWorld> awaitWorld;
    private ServerWorld world;
    private int ticks;
    private long timeReference, lastTimeReference, maxTimeReference;
    private boolean waitingForNextTick;

    public SubServer(
            MinecraftServer server,
            RegistryKey<World> worldKey,
            ArgonTask<ServerWorld> worldTask) {
        super(worldKey.getValue().toString());
        this.server = server;
        awaitWorld = worldTask;
        thread = new Thread(this, "[Argon] " + worldKey.getValue().toString() + "-world-thread");
        thread.setUncaughtExceptionHandler((t, e) -> logger.error("Error handling {}:", t, e));
        thread.start();
    }

    public ServerWorld getWorld() {
        return world;
    }

    @Override
    protected ServerTask createTask(Runnable runnable) {
        return new ServerTask(ticks, runnable);
    }

    @Override
    protected boolean canExecute(ServerTask task) {
        return task.getCreationTicks() + 3 < ticks || shouldKeepTicking();
    }

    @Override
    protected Thread getThread() {
        return thread;
    }

    @Override
    public void run() {
        try {
            world = awaitWorld.invoke();
            ((SubServerWorld) world).setSubServer(this);
        } catch (ExecutionException ee) {
            var report = CrashReport.create(ee, "Error fetching or creating world.");
            server.populateCrashReport(report);
            throw new CrashException(report);
        }
        try {
            timeReference = Util.getMeasuringTimeMs();
            while (server.isRunning()) {
                long l = Util.getMeasuringTimeMs() - timeReference;
                if (l > 2000L && this.timeReference - lastTimeReference >= 15000L) {
                    long m = l / 50L;
                    logger.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", l, m);
                    timeReference += m * 50L;
                    lastTimeReference = timeReference;
                }
                timeReference += 50L;
                // var durationMonitor = TickDurationMonitor.create(thread.getName());
                // startMonitor(durationMonitor);
                profiler.startTick();
                profiler.push("tick");
                tick(this::shouldKeepTicking);
                profiler.swap("nextTickWait");
                waitingForNextTick = true;
                maxTimeReference = Math.max(Util.getMeasuringTimeMs() + 50L, this.timeReference);
                runServerTasks();
                profiler.pop();
                profiler.endTick();
            }
        } catch (Throwable t) {
            logger.error("Encountered an unexpected exception", t);
            var report = CrashReport.create(t, "Exception in sub-server tick loop");
            report.addElement("SubServer").add("Thread", thread.getName());
            var file = Path.of(".", "crash-reports", "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date())) + "-server.txt").toFile();
            if (report.writeToFile(file)) {
                logger.error("This crash report has been saved to: {}", file.getAbsolutePath());
            } else {
                logger.error("We were unable to save this crash report to disk.\n{}", report);
            }
        }
    }

    public void runServerTasks() {
        runTasks();
        runTasks(() -> !shouldKeepTicking());
    }

    protected void tick(BooleanSupplier shouldKeepTicking) {
        ticks++;
        profiler.push(thread.getName());
        if ((ticks & 255) == 0) {
            profiler.push("timeSync");
            server.getPlayerManager().sendToDimension(new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(), world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)), world.getRegistryKey());
            profiler.pop();
        }
        profiler.push("tick");
        try {
            world.tick(shouldKeepTicking);
        } catch (Throwable t) {
            var report = CrashReport.create(t, "Exception ticking world");
            world.addDetailsToCrashReport(report);
            throw new CrashException(report);
        }
        profiler.pop();
        profiler.pop();
    }

    protected void executeTask(ServerTask serverTask) {
        profiler.visit("runTask");
        super.executeTask(serverTask);
    }

    public boolean runTask() {
        boolean bl = this.method_20415();
        this.waitingForNextTick = bl;
        return bl;
    }

    private boolean method_20415() {
        return super.runTask() || (shouldKeepTicking() && world.getChunkManager().executeQueuedTasks());
    }

    private boolean shouldKeepTicking() {
        return hasRunningTasks() || Util.getMeasuringTimeMs() < (waitingForNextTick ? maxTimeReference : timeReference);
    }
}
