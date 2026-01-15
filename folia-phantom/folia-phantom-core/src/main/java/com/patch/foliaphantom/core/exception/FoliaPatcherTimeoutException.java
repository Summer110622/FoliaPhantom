/*
 * This file is part of Folia Phantom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 Marv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
