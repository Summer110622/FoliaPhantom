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
package com.patch.foliaphantom.core.progress;

import java.io.File;

/**
 * Listener interface for monitoring the plugin patching process.
 * <p>
 * Implementations of this interface can be used to provide real-time
 * feedback to users in various user interfaces (CLI, GUI, etc.).
 * </p>
 */
public interface PatchProgressListener {

    /**
     * Called when the patching process is about to begin.
     *
     * @param originalJar The source JAR file being patched.
     * @param outputJar   The destination JAR file for the patched output.
     */
    void onPatchStart(File originalJar, File outputJar);

    /**
     * Called before a class is transformed.
     *
     * @param className The name of the class being transformed.
     * @param classIndex The index of the current class being processed.
     * @param totalClasses The total number of classes to be processed.
     */
    void onClassTransform(String className, int classIndex, int totalClasses);

    /**
     * Called to provide a general progress update.
     *
     * @param percentage The overall completion percentage (0-100).
     * @param message    A message describing the current stage of the process.
     */
    void onProgressUpdate(int percentage, String message);

    /**
     * Called when the patching process has completed.
     *
     * @param durationMillis The total time taken for the patching process in milliseconds.
     * @param statistics     An array containing statistics: [classesScanned, classesTransformed, classesSkipped].
     * @param error          An exception if the process failed, or null on success.
     */
    void onComplete(long durationMillis, int[] statistics, Throwable error);

}
