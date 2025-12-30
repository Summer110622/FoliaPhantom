/*
 * Folia Phantom - Patch Progress Listener
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
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
