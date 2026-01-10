/*
 * Folia Phantom - Timeout Exception
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.exception;

/**
 * Thrown when a proxied FoliaPatcher operation times out.
 *
 * <p>This runtime exception is part of a fail-fast strategy. Instead of
 * silently failing or returning null, Folia Phantom will throw this exception
 * to make timeouts visible and traceable, helping developers diagnose issues
* where an async operation on the server did not complete in the expected
 * time frame.</p>
 */
public class FoliaPatcherTimeoutException extends RuntimeException {

    /**
     * Constructs a new FoliaPatcherTimeoutException with the specified
     * detail message and cause.
     *
     * @param message The detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     * @param cause   The cause (which is saved for later retrieval by the
     *                {@link #getCause()} method). A {@code null} value is
     *                permitted, and indicates that the cause is nonexistent
     *                or unknown.
     */
    public FoliaPatcherTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
