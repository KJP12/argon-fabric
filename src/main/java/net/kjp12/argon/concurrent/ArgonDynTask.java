package net.kjp12.argon.concurrent;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

public class ArgonDynTask<T> extends ArgonTask<T> {
    public ArgonDynTask(Executor asyncPool, Callable<T> task) {
        super(asyncPool, task);
    }

    @Override
    public T get() throws ExecutionException {
        if (isDone()) return getRaw();
        if (isCancelled()) throw new CancellationException();
        return invoke();
    }

    @Override
    public T get(long timeout, @Nonnull TimeUnit unit) throws ExecutionException {
        if (isDone()) return getRaw();
        if (isCancelled()) throw new CancellationException();
        return invoke();
    }
}
