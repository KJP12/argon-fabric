package net.kjp12.argon.helpers;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.kjp12.argon.Argon;
import net.kjp12.argon.concurrent.ArgonTask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.MetricsData;
import net.minecraft.util.TickDurationMonitor;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.TickTimeTracker;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

import static net.kjp12.argon.Argon.logger;

public class SubServer extends ReentrantThreadExecutor<ServerTask> implements Runnable {
    private final MinecraftServer server;
    private final Thread thread;
    public MetricsData metricsData = new MetricsData();
    private Queue<ClientConnection> awaitingConnections = new ConcurrentLinkedQueue<>();
    private List<ClientConnection> activeConnections = new ArrayList<>();
    private TickTimeTracker tickTimeTracker = new TickTimeTracker(Util.nanoTimeSupplier, this::getTicks);
    public Profiler profiler = DummyProfiler.INSTANCE;
    public ArgonTask<ServerWorld> awaitWorld;
    private ServerWorld world;
    private int ticks;
    private long timeReference, lastTimeReference, maxTimeReference;
    private String worldName;
    private boolean waitingForNextTick;
    private volatile boolean profilerStartQueued, profilerEndQueued;

    public SubServer(MinecraftServer server, RegistryKey<World> worldKey, ArgonTask<ServerWorld> worldTask) {
        super(worldKey.getValue().toString());
        this.server = server;
        awaitWorld = worldTask;
        worldName = worldKey.getValue().toString();
        thread = new Thread(this, "[Argon] " + worldName);
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
    public Thread getThread() {
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
        try /*(var w = world)*/ {
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
                var durationMonitor = TickDurationMonitor.create(thread.getName());
                startMonitor(durationMonitor);
                profiler.startTick();
                profiler.push("tick");
                tick(this::shouldKeepTicking);
                profiler.swap("nextTickWait");
                waitingForNextTick = true;
                maxTimeReference = Math.max(Util.getMeasuringTimeMs() + 50L, this.timeReference);
                runServerTasks();
                profiler.pop();
                profiler.endTick();
                endMonitor(durationMonitor);
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

    public int getTicks() {
        return ticks;
    }

    public long getTimeReference() {
        return timeReference;
    }

    public long getLastTimeReference() {
        return lastTimeReference;
    }

    protected void tick(BooleanSupplier shouldKeepTicking) {
        long l = Util.getMeasuringTimeNano();
        ticks++;
        profiler.push(worldName);
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
        profiler.swap("connection");
        tickConnections();
        profiler.pop();
        long n = Util.getMeasuringTimeNano();
        metricsData.pushSample(n - l);
        profiler.pop();
    }

    protected void tickConnections() {
        if (!awaitingConnections.isEmpty()) {
            ClientConnection connection;
            while ((connection = awaitingConnections.poll()) != null) {
                if (activeConnections.contains(connection)) {
                    logger.warn("Duplicate connection {} enqueued.", connection);
                } else activeConnections.add(connection);
            }
        }
        var iterator = activeConnections.iterator();
        while (iterator.hasNext()) {
            var clientConnection = iterator.next();
            if (clientConnection.hasChannel()) continue;
            var iConnection = (IClientConnection) clientConnection;
            var subServer = iConnection.getOwner();
            if (subServer != this) {
                logger.warn("Connection {} @ {} is active in {}?! Enqueuing into correct SubServer.",
                        clientConnection, subServer, this);
                if (subServer != null) {
                    // This isn't owned by anyone, the main server will tick it.
                    subServer.queueConnection(clientConnection);
                }
                iterator.remove();
                continue;
            }

            if (clientConnection.isOpen()) {
                try {
                    clientConnection.tick();
                } catch (Exception var7) {
                    if (clientConnection.isLocal())
                        throw new CrashException(CrashReport.create(var7, "Ticking memory connection"));

                    logger.warn("Failed to handle packet for {}", clientConnection.getAddress(), var7);
                    Text text = new LiteralText("Internal server error");
                    clientConnection.send(new DisconnectS2CPacket(text), (future) -> clientConnection.disconnect(text));
                    clientConnection.disableAutoRead();
                }
            } else {
                iterator.remove();
                clientConnection.handleDisconnection();
            }
        }
    }

    protected void executeTask(ServerTask serverTask) {
        profiler.visit("runTask");
        super.executeTask(serverTask);
    }

    private void startMonitor(TickDurationMonitor monitor) {
        if(profilerStartQueued) {
            profilerStartQueued = false;
            tickTimeTracker.enable();
        }
        profiler = TickDurationMonitor.tickProfiler(tickTimeTracker.getProfiler(), monitor);
    }

    private void endMonitor(TickDurationMonitor monitor) {
        if (monitor != null) monitor.endTick();
        if (profilerEndQueued) {
            profilerEndQueued = false;
            tickTimeTracker.disable();
        }
        if(Argon.serverToProfile == this) {
            Argon.profileResult = tickTimeTracker.getResult();
        }
        profiler = tickTimeTracker.getProfiler();
    }

    public void queueConnection(ClientConnection connection) {
        awaitingConnections.offer(connection);
    }

    public boolean runTask() {
        return waitingForNextTick = super.runTask() || (shouldKeepTicking() && world.getChunkManager().executeQueuedTasks());
    }

    public void enableProfiler(){
        profilerStartQueued = true;
    }

    public void disableProfiler(){
        profilerEndQueued = true;
    }

    private boolean shouldKeepTicking() {
        return hasRunningTasks() || Util.getMeasuringTimeMs() < (waitingForNextTick ? maxTimeReference : timeReference);
    }

    public String toString() {
        return "SubServer " + worldName;
    }
}
