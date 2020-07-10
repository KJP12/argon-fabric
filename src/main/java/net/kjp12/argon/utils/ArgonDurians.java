package net.kjp12.argon.utils;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class ArgonDurians {
    private ArgonDurians() {
    }

    public static <T> Supplier<T> rethrowSupplier(Callable<T> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        };
    }
}
