package net.kjp12.argon.concurrent;

import java.util.concurrent.*;

public class ArgonTask<T> extends ArgonCompletable<T> implements Runnable {
    private final Callable<T> task;
    private volatile Thread executor;

    public ArgonTask(Executor asyncPool, Callable<T> task) {
        super(asyncPool);
        this.task = task;
    }

    @Override
    public void run() {
        if (!isDone() && !isCancelled()) {
            if (executor != null) throw new IllegalStateException("Task already taken by " + executor);
            executor = Thread.currentThread();
            try {
                super.complete(task.call());
            } catch (Exception e) {
                super.fail(e);
            } finally {
                synchronized (task) { //TODO: Better lock?
                    task.notifyAll();
                }
            }
        } else {
            throw new IllegalStateException("Task was already ran or cancelled.");
        }
    }

    @Override
    public void complete(T any) {
        throw new UnsupportedOperationException("ArgonTask does not allow external setters.");
    }

    @Override
    public void fail(Throwable t) {
        var uoe = new UnsupportedOperationException("ArgonTask does not allow external setters.");
        uoe.addSuppressed(t);
        throw uoe;
    }

    public T invoke() throws ExecutionException {
        try {
            run();
        } catch (Throwable t) {
            throw new ExecutionException(t);
        }
        return getRaw();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isCancelled()) return false;
        if (!isDone()) {
            if (executor != null) {
                if (mayInterruptIfRunning) {
                    synchronized (task) {
                        executor.interrupt();
                        try {
                            task.wait();
                        } catch (InterruptedException ignore) {
                        }
                    }
                    return true;
                } else return false;
            } else {
                queue = null;
                object = null;
                executor = null;
                synchronized (task) {
                    notifyAll();
                }
            }
        }
        return false;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (isDone()) return getRaw();
        if (isCancelled()) throw new CancellationException();
        synchronized (task) { //TODO: Better lock?
            while (!isCancelled() || !isDone())
                // Polling fallback in case notify is never called for any reason.
                wait(1000L);
        }
        if (isCancelled()) throw new CancellationException();
        return getRaw();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (isDone()) return getRaw();
        if (isCancelled()) throw new CancellationException();
        synchronized (task) { //TODO: Better lock?
            long now = System.currentTimeMillis();
            long timeReference = now + unit.toMillis(timeout);
            while ((!isCancelled() || !isDone()) && now < timeReference) {
                wait(timeReference - now);
                now = System.currentTimeMillis();
            }
        }
        if (!isDone()) throw new TimeoutException();
        if (isCancelled()) throw new CancellationException();
        return getRaw();
    }
}
