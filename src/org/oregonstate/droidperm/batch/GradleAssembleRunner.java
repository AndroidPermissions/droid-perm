package org.oregonstate.droidperm.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/5/2016.
 */
public class GradleAssembleRunner {

    private static Logger logger = LoggerFactory.getLogger(GradleAssembleRunner.class);

    /**
     * Run droidPerm on all the apps in the given directory.
     * <p>
     * Command line arguments:
     * <p>
     * args[0] = path to root directory. Every directory directly inside this path is assumed to be an app repository.
     * Should containt the files: droid-perm.jar, android-23-cr+util_io.zip
     * <p>
     * args[1] = path gradle executable. This tool cannot find it in the PATH. absolute path is required here.
     * <p>
     * args[2] = path to output location, where log files will be stored.
     */
    public static void main(final String[] args) throws IOException, InterruptedException {
        String appsDir = args[0];
        String gradlePath = args[1];
        String outputLogsDir = args[2];
        logger.info("appsDir: " + appsDir);
        logger.info("gradlePath: " + gradlePath);
        logger.info("outputLogsDir: " + outputLogsDir);
        long startTime = System.currentTimeMillis();

        batchRun(appsDir, gradlePath, outputLogsDir);

        logger.info(
                "\n\nGradle runner total execution time: " + (System.currentTimeMillis() - startTime) / 1E3 + " sec");
    }

    private static void batchRun(String appsDir, String gradlePath, String outputLogsDir) throws IOException {
        Files.createDirectories(Paths.get(outputLogsDir));
        List<Path> appDirs = Files.list(Paths.get(appsDir)).sorted()
                .filter(path -> Files.isDirectory(path))
                .collect(Collectors.toList());

        for (Path appDir : appDirs) {
            assembleApp(appDir, gradlePath, outputLogsDir);
        }
    }

    private static void assembleApp(Path appDir, String gradlePath, String outputLogsDir)
            throws IOException {
        String appName = appDir.getFileName().toString();
        Path logFile = Paths.get(outputLogsDir, appName + ".log");
        Path errorFile = Paths.get(outputLogsDir, appName + ".error.log");

        List<String> processBuilderArgs = Arrays.asList(gradlePath, "assemble");

        ProcessBuilder processBuilder = new ProcessBuilder(processBuilderArgs)
                .directory(appDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.to(errorFile.toFile()));

        logger.info(appName + " ... ");

        long time = System.currentTimeMillis();

        Process process = processBuilder.start();
        try {
            int exitCode = process.waitFor();
            long newTime = System.currentTimeMillis();
            logger.info(appName + " built: " + (newTime - time) / 1E3 + " sec");
            if (exitCode != 0) {
                logger.error(appName + " build returned exit code " + exitCode);
            }

        } catch (InterruptedException e) {
            process.destroy();
            throw new RuntimeException(e);
        }
    }
}
