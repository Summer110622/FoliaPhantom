/*
 * Folia Phantom - Patcher Timeout Exception
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.patcher;

/**
 * Thrown when a patched, asynchronous operation fails to complete within the
 * expected timeout.
 * <p>
 * This exception signals a potential server deadlock or severe performance
 * issue that prevented a thread-safe operation (like getting a block's state
 * from another thread) from executing in time.
 * <p>
 * Catching this exception is generally not recommended unless you have a
 * specific fallback mechanism. Its primary purpose is to provide a clear
 * and immediate failure signal to server administrators, indicating that
 * a patched plugin is unable to function correctly under the current server
 * conditions.
 */
public class FoliaPatcherTimeoutException extends RuntimeException {

    /**
     * Constructs a new FoliaPatcherTimeoutException with the specified detail
     * message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method).
     */
    public FoliaPatcherTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
