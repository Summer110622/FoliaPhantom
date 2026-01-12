package com.patch.foliaphantom.cli;

import com.patch.foliaphantom.core.PluginPatcher;
import com.patch.foliaphantom.core.progress.PatchProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class CLI {

    private static final Logger LOGGER = Logger.getLogger("FoliaPhantom-CLI");

    public static void main(String[] args) {
        setupLogger();
        printBanner();

        boolean failFast = false;
        boolean aggressiveEventOptimization = false;
        boolean fireAndForget = false;
        long apiTimeoutMs = 100L;
        String inputPath = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--fail-fast".equalsIgnoreCase(arg)) {
                failFast = true;
            } else if ("--aggressive-event-optimization".equalsIgnoreCase(arg)) {
                aggressiveEventOptimization = true;
            } else if ("--fire-and-forget".equalsIgnoreCase(arg)) {
                fireAndForget = true;
            } else if ("--timeout".equalsIgnoreCase(arg)) {
                if (i + 1 < args.length) {
                    try {
                        apiTimeoutMs = Long.parseLong(args[++i]);
                    } catch (NumberFormatException e) {
                        LOGGER.severe("Error: Invalid timeout value provided. Please use a number.");
                        return;
                    }
                } else {
                    LOGGER.severe("Error: --timeout flag requires a value in milliseconds.");
                    return;
                }
            } else if (inputPath == null) {
                inputPath = arg;
            } else {
                LOGGER.warning("Ignoring additional argument: " + arg);
            }
        }

        File inputFile = getInputFile(inputPath);
        if (inputFile == null) {
            return;
        }

        File outputDir = new File("patched-plugins");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            LOGGER.severe("Error: Failed to create the output directory at " + outputDir.getAbsolutePath());
            return;
        }

        LOGGER.info("Output directory set to: " + outputDir.getAbsolutePath());
        if (failFast) {
            LOGGER.info("Fail-fast mode is enabled. Timeouts will throw exceptions.");
        }
        if (aggressiveEventOptimization) {
            LOGGER.info("Aggressive event optimization is enabled. Async event calls will not be synchronized.");
        }
        if (fireAndForget) {
            LOGGER.info("Fire-and-forget mode is enabled. API calls will not block, potentially increasing performance but may cause issues.");
        }
        LOGGER.info("API call timeout is set to: " + apiTimeoutMs + "ms.");

        PatchProgressListener listener = new ConsolePatchProgressListener();
        PluginPatcher patcher = new PluginPatcher(LOGGER, listener, failFast, aggressiveEventOptimization, fireAndForget, apiTimeoutMs);

        if (inputFile.isDirectory()) {
            patchDirectory(patcher, inputFile, outputDir);
        } else if (inputFile.getName().toLowerCase().endsWith(".jar")) {
            patchJar(patcher, inputFile, outputDir);
        } else {
            LOGGER.severe("Error: The provided file is not a JAR file.");
        }

        LOGGER.info("Patching process completed.");
    }

    private static void setupLogger() {
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(java.util.logging.LogRecord lr) {
                return String.format("[%s] %s%n", lr.getLevel().getLocalizedName(), lr.getMessage());
            }
        });
        LOGGER.addHandler(handler);
    }

    private static void printBanner() {
        System.out.println("======================================================================");
        System.out.println(" Folia Phantom CLI");
        System.out.println(" A utility for patching Bukkit plugins for Folia compatibility.");
        System.out.println("======================================================================");
    }

    private static File getInputFile(String pathArg) {
        File inputFile;
        if (pathArg == null) {
            try (Scanner scanner = new Scanner(System.in)) {
                System.out.print(">> Enter the path to a JAR file or a directory of JARs: ");
                String path = scanner.nextLine();
                if (path.isBlank()) {
                    LOGGER.severe("Error: No path provided.");
                    return null;
                }
                inputFile = new File(path);
            }
        } else {
            inputFile = new File(pathArg);
        }

        if (!inputFile.exists()) {
            LOGGER.severe("Error: The specified file or directory does not exist: " + inputFile.getAbsolutePath());
            return null;
        }
        return inputFile;
    }

    private static void patchDirectory(PluginPatcher patcher, File inputDir, File outputDir) {
        File[] jarsToPatch = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        if (jarsToPatch == null) {
            LOGGER.severe("Error: Could not list files in the directory. Please check permissions.");
            return;
        }

        if (jarsToPatch.length == 0) {
            LOGGER.warning("No JAR files were found in the specified directory.");
            return;
        }

        LOGGER.info("Found " + jarsToPatch.length + " JAR file(s) to patch in the directory.");
        int successCount = 0;
        for (File inputJar : jarsToPatch) {
            if (patchJar(patcher, inputJar, outputDir)) {
                successCount++;
            }
        }
        LOGGER.info("Successfully patched " + successCount + " out of " + jarsToPatch.length + " JAR file(s).");
    }

    private static boolean patchJar(PluginPatcher patcher, File inputJar, File outputDir) {
        try {
            String pluginName = PluginPatcher.getPluginNameFromJar(inputJar);
            if (pluginName == null) {
                pluginName = inputJar.getName();
            }

            if (PluginPatcher.isFoliaSupported(inputJar)) {
                LOGGER.warning("This plugin already appears to be Folia-supported. Patching anyway.");
            }

            File outputJar = new File(outputDir, "patched-" + inputJar.getName());
            patcher.patchPlugin(inputJar, outputJar);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "An error occurred while patching " + inputJar.getName() + ":", e);
            return false;
        }
    }
}
