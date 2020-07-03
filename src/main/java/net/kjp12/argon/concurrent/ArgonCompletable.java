package net.kjp12.argon.concurrent;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ArgonCompletable<T> implements ArgonFuture<T> {
    protected Executor asyncPool;
    protected volatile ConcurrentLinkedQueue<ArgonConsumer<T>> queue = new ConcurrentLinkedQueue<>();
    protected AtomicReference<T> object = new AtomicReference<>();
    protected volatile Throwable thrown;

    public ArgonCompletable() {
        this(ForkJoinPool.commonPool());
    }

    public ArgonCompletable(Executor asyncPool) {
        this.asyncPool = asyncPool;
    }

    public void complete(T object) {
        if (isCancelled() || isDone()) throw new IllegalStateException("Already completed or cancelled.");
        this.object.set(object);
        var q = queue;
        queue = null;
        q.forEach(ArgonConsumer::onComplete);
    }

    public void fail(Throwable error) {
        if (isCancelled() || isDone()) throw new IllegalStateException("Already completed or cancelled.");
        thrown = error;
        var q = queue;
        queue = null;
        q.forEach(ArgonConsumer::onComplete);
    }

    public void whenComplete(Consumer<? super T> onComplete, Consumer<? super Throwable> onError) {
        whenComplete(onComplete, onError, asyncPool);
    }

    public void whenComplete(Consumer<? super T> onComplete, Consumer<? super Throwable> onError, Executor asyncPool) {
        if (isCancelled()) return;
        if (!isDone()) {
            queue.add(new ArgonConsumer<>(this, asyncPool, onComplete, Objects.requireNonNullElse(onError, ErrorLogger.INSTANCE)));
        } else {
            if (thrown != null) {
                if (onError == null)
                    ErrorLogger.INSTANCE.accept(thrown);
                else
                    asyncPool.execute(() -> onError.accept(thrown));
            } else {
                asyncPool.execute(() -> onComplete.accept(object.get()));
            }
        }
    }

    @Override
    public T getObject() {
        return object == null ? null : object.get();
    }

    @Override
    public Throwable getError() {
        return thrown;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isCancelled()) return false;
        if (!isDone()) {
            queue = null;
            object = null;
            synchronized (this) {  // TODO: Better lock?
                notifyAll();
            }
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return object == null;
    }

    @Override
    public boolean isDone() {
        return !isCancelled() && queue == null;
    }

    protected T getRaw() throws ExecutionException {
        if (thrown != null) throw new ExecutionException(thrown);
        return object.get();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (isDone()) return getRaw();
        if (isCancelled()) throw new CancellationException();
        synchronized (this) { // TODO: Better lock?
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
        synchronized (this) { // TODO: Better lock?
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
