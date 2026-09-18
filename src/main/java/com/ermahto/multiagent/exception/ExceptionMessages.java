package com.ermahto.multiagent.exception;

import io.reactivex.rxjava3.exceptions.CompositeException;
import java.util.stream.Collectors;

public final class ExceptionMessages {

    private ExceptionMessages() {
    }

    public static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        if (throwable instanceof CompositeException composite) {
            return composite.getExceptions().stream()
                    .map(ExceptionMessages::rootMessage)
                    .collect(Collectors.joining("; "));
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }
}
