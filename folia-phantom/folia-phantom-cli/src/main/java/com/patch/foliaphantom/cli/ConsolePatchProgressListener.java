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
package com.patch.foliaphantom.cli;

import com.patch.foliaphantom.core.progress.PatchProgressListener;

import java.io.File;

public class ConsolePatchProgressListener implements PatchProgressListener {

    private long startTime;

    @Override
    public void onPatchStart(File originalJar, File outputJar) {
        this.startTime = System.currentTimeMillis();
        System.out.println("─────────────────────────────────────────");
        System.out.println(" Patching: " + originalJar.getName());
        System.out.println(" Output:   " + outputJar.getName());
        System.out.println("─────────────────────────────────────────");
    }

    @Override
    public void onClassTransform(String className, int classIndex, int totalClasses) {
        int percentage = (int) (((double) classIndex / totalClasses) * 100);
        printProgressBar(percentage, "Transforming: " + className);
    }

    @Override
    public void onProgressUpdate(int percentage, String message) {
        printProgressBar(percentage, message);
    }

    @Override
    public void onComplete(long durationMillis, int[] statistics, Throwable error) {
        System.out.println("\n─────────────────────────────────────────");
        if (error != null) {
            System.err.println(" Patching failed after " + durationMillis + "ms");
            error.printStackTrace();
        } else {
            System.out.println(" Completed in " + durationMillis + "ms");
            System.out.println("   Classes scanned:     " + statistics[0]);
            System.out.println("   Classes transformed: " + statistics[1]);
            System.out.println("   Classes skipped:     " + statistics[2]);
        }
        System.out.println("─────────────────────────────────────────");
    }

    private void printProgressBar(int percentage, String message) {
        int barLength = 50;
        int progress = (int) (barLength * (percentage / 100.0));
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < progress) {
                bar.append("=");
            } else if (i == progress) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("] ").append(percentage).append("%");
        System.out.print("\r" + bar + " - " + message);
    }
}
