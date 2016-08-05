package org.oregonstate.droidperm.batch;

import org.oregonstate.droidperm.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/5/2016.
 */
public class DPBatchRunner {

    private static Logger logger = LoggerFactory.getLogger(DPBatchRunner.class);
    private static final String[] EXTRA_OPTS =
            new String[]{"--pathalgo", "CONTEXTSENSITIVE", "--cgalgo", "GEOM"};
    private static final String[] TAINT_ENABLED_OPTS =
            new String[]{"--taint-analysis-enabled", "true", "--logsourcesandsinks"};
    private static final String[] TAINT_DISABLED_OPTS = new String[]{"--taint-analysis-enabled", "false"};

    /**
     * Run droidPerm on all the apps in the given directory.
     * <p>
     * Command line arguments:
     * <p>
     * args[0] = path to root directory. Every directory directly inside this path is assumed to be an app repository.
     * Should containt the files: droid-perm.jar, android-23-cr+util_io.zip
     * <p>
     * args[1] = path to DroidPerm home dir, with content similar to droid-perm-plugin/dp-lib
     * <p>
     * args[2] = path to output location, where log files will be stored.
     * <p>
     * args[3] = --taint-analysis-enabled or --taint-analysis-disabled. Pick first for overnight run. Leads to longer
     * execution time, important for storage permissions.
     * <p>
     * args[4] = list of VM arguments, in quotes if more than one. Could be left empty. Recommended value to increase
     * heap memory: -Xmx4g
     */
    public static void main(final String[] args) throws IOException, InterruptedException {
        String appsDir = args[0];
        String droidPermHomeDir = args[1];
        String outputLogsDir = args[2];
        boolean taintAnalysisEnabled = "--taint-analysis-enabled".equals(args[3]);
        String[] vmArgs = args.length > 4 ? args[4].split("\\s+") : new String[]{};
        logger.info("appsDir: " + appsDir);
        logger.info("droidPermHomeDir: " + droidPermHomeDir);
        logger.info("outputLogsDir: " + outputLogsDir);
        logger.info("taintAnalysisEnabled: " + taintAnalysisEnabled);
        logger.info("vmArgs: " + Arrays.toString(vmArgs) + "\n");
        long startTime = System.currentTimeMillis();

        batchRun(appsDir, droidPermHomeDir, outputLogsDir, taintAnalysisEnabled, vmArgs);

        logger.info(
                "\n\nBatch runner total execution time: " + (System.currentTimeMillis() - startTime) / 1E3 + " sec");
    }

    private static void batchRun(String appsDir, String droidPermHomeDir, String outputLogsDir,
                                 boolean taintAnalysisEnabled, String[] vmArgs) throws IOException {
        Files.createDirectories(Paths.get(outputLogsDir));
        Map<String, List<Path>> appNamesToApksMap = Files.list(Paths.get(appsDir)).sorted()
                .filter(path -> Files.isDirectory(path))
                .collect(Collectors.toMap(
                        path -> path.getFileName().toString(),
                        path -> {
                            try {
                                return Files.walk(path)
                                        .filter(possibleApk -> possibleApk.getFileName().toString()
                                                .endsWith("-debug.apk"))
                                        .collect(Collectors.toList());
                            } catch (IOException e) {
                                //Looks like Java 8 lambdas don't like to throw checked exceptions.
                                throw new RuntimeException(e);
                            }
                        },
                        StreamUtil.throwingMerger(),
                        LinkedHashMap::new
                ));

        for (String appName : appNamesToApksMap.keySet()) {
            List<Path> apks = appNamesToApksMap.get(appName);
            if (apks.isEmpty()) {
                logger.warn(appName + ": No files matching \"*-debug.apk\" found.");
                continue;
            }
            if (apks.size() > 1) {
                logger.warn(appName + ": multiple apk files found: " + apks + "\nPicking the first one.");
            }
            Path apk = apks.get(0);
            analyzeApp(appName, apk, droidPermHomeDir, outputLogsDir, taintAnalysisEnabled, vmArgs);
        }
    }

    private static void analyzeApp(String appName, Path apk, String droidPermHomeDir, String outputLogsDir,
                                   boolean taintAnalysisEnabled, String[] vmArgs)
            throws IOException {
        String droidPermClassPath = droidPermHomeDir + "/droid-perm.jar";
        String androidClassPath = droidPermHomeDir + "/android-23-cr+util_io.zip";
        Path logFile = Paths.get(outputLogsDir, appName + ".log");
        Path errorFile = Paths.get(outputLogsDir, appName + ".error.log");

        List<String> processBuilderArgs = new ArrayList<>();
        processBuilderArgs.add("java");
        processBuilderArgs.addAll(Arrays.asList(vmArgs));
        processBuilderArgs.addAll(Arrays.asList(
                "-jar", droidPermClassPath, apk.toAbsolutePath().toString(),
                androidClassPath));
        processBuilderArgs.addAll(Arrays.asList(EXTRA_OPTS));
        if (taintAnalysisEnabled) {
            processBuilderArgs.addAll(Arrays.asList(TAINT_ENABLED_OPTS));
        } else {
            processBuilderArgs.addAll(Arrays.asList(TAINT_DISABLED_OPTS));
        }

        ProcessBuilder processBuilder = new ProcessBuilder(processBuilderArgs)
                .directory(new File(droidPermHomeDir))
                .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.to(errorFile.toFile()));

        logger.info(appName + " ... ");

        long time = System.currentTimeMillis();

        Process process = processBuilder.start();
        try {
            int exitCode = process.waitFor();
            long newTime = System.currentTimeMillis();
            logger.info(appName + " analyzed: " + (newTime - time) / 1E3 + " sec");
            if (exitCode != 0) {
                logger.error(appName + " analysis returned exit code " + exitCode);
            }

        } catch (InterruptedException e) {
            process.destroy();
            throw new RuntimeException(e);
        }
    }
}
