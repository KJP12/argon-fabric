package net.kjp12.argon.concurrent;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

class ArgonConsumer<T> {
    private ArgonFuture<T> parent;
    private Executor executor;
    private Consumer<? super T> onComplete;
    private Consumer<? super Throwable> onError;

    ArgonConsumer(ArgonFuture<T> parent, Executor executor, Consumer<? super T> onComplete, Consumer<? super Throwable> onError) {
        this.parent = parent;
        this.executor = executor;
        this.onComplete = onComplete;
        this.onError = onError;
    }

    void onComplete() {
        var t = parent.getError();
        executor.execute(t == null ? () -> onComplete.accept(parent.getObject()) : () -> onError.accept(t));
    }
}
