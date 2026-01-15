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
package com.patch.foliaphantom.gui;

import com.patch.foliaphantom.core.progress.PatchProgressListener;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GuiPatchProgressListener implements PatchProgressListener {

    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final TextArea statusArea;

    public GuiPatchProgressListener(ProgressBar progressBar, Label progressLabel, TextArea statusArea) {
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
        this.statusArea = statusArea;
    }

    @Override
    public void onPatchStart(File originalJar, File outputJar) {
        Platform.runLater(() -> {
            progressBar.setProgress(0);
            progressLabel.setText("Starting patch for: " + originalJar.getName());
            appendLog("[INFO] Patching: " + originalJar.getName());
        });
    }

    @Override
    public void onClassTransform(String className, int classIndex, int totalClasses) {
        double percentage = (double) classIndex / totalClasses;
        Platform.runLater(() -> {
            progressBar.setProgress(percentage);
            progressLabel.setText("Transforming: " + className);
        });
    }

    @Override
    public void onProgressUpdate(int percentage, String message) {
        double progress = percentage / 100.0;
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            progressLabel.setText(message);
        });
    }

    @Override
    public void onComplete(long durationMillis, int[] statistics, Throwable error) {
        Platform.runLater(() -> {
            if (error != null) {
                progressBar.setProgress(1);
                progressLabel.setText("Patching failed.");
                appendLog("[ERROR] Patching failed after " + durationMillis + "ms: " + error.getMessage());
            } else {
                progressBar.setProgress(1);
                progressLabel.setText("Patching complete.");
                appendLog("[SUCCESS] Completed in " + durationMillis + "ms");
                appendLog("[STATS] Scanned: " + statistics[0] + ", Transformed: " + statistics[1] + ", Skipped: " + statistics[2]);
            }
        });
    }

    private void appendLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        statusArea.appendText("[" + timestamp + "] " + message + "\n");
    }
}
