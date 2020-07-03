package net.kjp12.argon.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static net.kjp12.argon.Argon.logger;

public interface ArgonFuture<T> extends Future<T> {
    T getObject();

    Throwable getError();

    default void whenComplete(Consumer<? super T> onComplete, Consumer<? super Throwable> onError) {
        whenComplete(onComplete, onError, ForkJoinPool.commonPool());
    }

    void whenComplete(Consumer<? super T> onComplete, Consumer<? super Throwable> onError, Executor asyncPool);

    final class ErrorLogger implements Consumer<Throwable> {
        public static final ErrorLogger INSTANCE = new ErrorLogger();

        private ErrorLogger() {
        }

        @Override
        public void accept(Throwable throwable) {
            logger.error("Failed to run task", throwable);
        }
    }
}
