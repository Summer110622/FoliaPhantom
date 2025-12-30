/*
 * Folia Phantom - GUI Progress Listener
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
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
