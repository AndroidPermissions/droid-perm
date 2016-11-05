package org.oregonstate.droidperm.batch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.InfoflowConfiguration;

import javax.xml.bind.JAXBException;
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
    private static final String[] COMMON_OPTS = new String[]{};
    private static final String[] DROID_PERM_MODE_OPTS = new String[]{};
    private static final String[] TAINT_ANALYSIS_MODE_OPTS =
            new String[]{"--taint-analysis-enabled", "true", "--logsourcesandsinks", "--pathalgo", "CONTEXTSENSITIVE"};

    @Parameter(names = "--apps-dir", description = "Directory with apk files, each in a separate dir", required = true)
    private Path appsDir;

    @Parameter(names = "--droid-perm-home", description = "Path to DroidPerm standalone installation dir",
            required = true)
    private Path droidPermHomeDir;

    @Parameter(names = "--log-dir", description = "Directory where log files will be stored", required = true)
    private Path logDir;

    @Parameter(names = "--cg-algo", description = "The call graph algorithm")
    private InfoflowConfiguration.CallgraphAlgorithm cgAlgo = InfoflowConfiguration.CallgraphAlgorithm.GEOM;

    @Parameter(names = "--vm-args", description = "Additional VM arguments, separated by space. "
            + "If more than one, they should be included into quotes (\"\").")
    private String vmArgs;

    @Parameter(names = "--mode", description = "DroidPerm execution mode")
    private Mode mode = Mode.DROID_PERM;

    private enum Mode {
        DROID_PERM, TAINT_ANALYSIS, COLLECT_ANNO, COLLECT_SENSITIVES
    }

    @Parameter(names = "--help", help = true)
    private boolean help = false;

    /**
     * Essentially the set of all collected permission definitions. Map from definitions to themselves.
     */
    private Map<PermissionDef, PermissionDef> permissionDefs = new LinkedHashMap<>();

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
     * args[4] = --cgalgo=GEOM or --cgalgo=SPARK. Unless otherwise noted, use --cgalgo=GEOM.
     * <p>
     * args[5] = list of VM arguments, in quotes if more than one. Could be left empty. Recommended value to increase
     * heap memory: -Xmx4g
     */
    public static void main(final String[] args) throws IOException, InterruptedException, JAXBException {
        DPBatchRunner main = new DPBatchRunner();
        JCommander jCommander = new JCommander(main);
        try {
            jCommander.parse(args);
            if (main.help) {
                jCommander.usage();
                return;
            }
            long startTime = System.currentTimeMillis();
            main.batchRun();
            long endTime = System.currentTimeMillis();
            logger.info("\n\nBatch runner total execution time: " + (endTime - startTime) / 1E3 + " sec");
        } catch (ParameterException e) {
            System.err.println(e.getLocalizedMessage());
            System.err.println();
            StringBuilder sb = new StringBuilder();
            jCommander.usage(sb);
            System.err.println(sb.toString());
            System.exit(1);
        }
    }

    private void batchRun() throws IOException, JAXBException {
        logger.info("DroidPerm Mode: " + mode);
        logger.info("appsDir: " + appsDir);
        logger.info("droidPermHomeDir: " + droidPermHomeDir);
        logger.info("logDir: " + logDir);
        logger.info("cgalgo: " + cgAlgo);
        logger.info("vmArgs: " + vmArgs + "\n");

        Files.createDirectories(logDir);
        Map<String, List<Path>> appNamesToApksMap = Files.list(appsDir).sorted()
                .filter(path -> Files.isDirectory(path))
                .collect(Collectors.toMap(
                        path -> path.getFileName().toString(),
                        path -> {
                            try {
                                return Files.walk(path)
                                        .filter(possibleApk -> possibleApk.getFileName().toString().endsWith(".apk"))
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
                logger.warn(appName + ": No files matching \"*.apk\" found.");
                continue;
            }
            if (apks.size() > 1) {
                apks = apks.stream().filter(apk -> apk.getFileName().toString().endsWith("-debug.apk"))
                        .collect(Collectors.toList());
                if (apks.size() > 1) {
                    logger.warn(appName + ": multiple -debug.apk files found: " + apks + "\nPicking the first one.");
                }
            }
            Path apk = apks.get(0);
            analyzeApp(appName, apk);
        }

        if (mode == Mode.COLLECT_ANNO) {
            finalizeCollectAnnoMode();
        }
    }

    private void analyzeApp(String appName, Path apk) throws IOException, JAXBException {
        String droidPermClassPath = droidPermHomeDir + "/droid-perm.jar";
        String androidClassPath = droidPermHomeDir + "/android-23-cr+util_io.zip";
        Path logFile = Paths.get(logDir.toString(), appName + ".log");
        Path errorFile = Paths.get(logDir.toString(), appName + ".error.log");
        Path annoXmlFile = Paths.get(logDir.toString(), appName + ".anno.xml");

        List<String> processBuilderArgs = new ArrayList<>();
        processBuilderArgs.add("java");
        if (vmArgs != null) {
            processBuilderArgs.addAll(Arrays.asList(vmArgs.split("\\s+")));
        }
        processBuilderArgs.addAll(Arrays.asList(
                "-jar", droidPermClassPath, apk.toAbsolutePath().toString(),
                androidClassPath));
        processBuilderArgs.addAll(Arrays.asList(COMMON_OPTS));
        processBuilderArgs.addAll(Arrays.asList("--cgalgo", cgAlgo.name()));
        switch (mode) {
            case DROID_PERM:
                processBuilderArgs.addAll(Arrays.asList(DROID_PERM_MODE_OPTS));
                break;
            case TAINT_ANALYSIS:
                processBuilderArgs.addAll(Arrays.asList(TAINT_ANALYSIS_MODE_OPTS));
                break;
            case COLLECT_ANNO:
                processBuilderArgs
                        .addAll(Arrays.asList("--xml-out", annoXmlFile.toString(), "--COLLECT-PERM-ANNO-MODE"));
                break;
            case COLLECT_SENSITIVES:
                //todo
                processBuilderArgs
                        .addAll(Arrays.asList("--perm-def-files",
                                "config/perm-def-custom-only.txt;config/perm-def-API-23.xml;config/perm-def-play-services.xml",
                                "--collect-sens-mode"));
                break;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(processBuilderArgs)
                .directory(droidPermHomeDir.toFile())
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
            } else {
                if (mode == Mode.COLLECT_ANNO) {
                    collectAnnotations(annoXmlFile);
                }
            }
        } catch (InterruptedException e) {
            process.destroy();
            throw new RuntimeException(e);
        }
    }

    private void collectAnnotations(Path annoXmlFile) throws JAXBException {
        List<PermissionDef> newAnnos = XmlPermDefMiner.load(annoXmlFile.toFile()).getPermissionDefs();
        Set<PermissionDef> existingAnnos = new LinkedHashSet<>(newAnnos);
        existingAnnos.retainAll(permissionDefs.keySet());
        existingAnnos.stream()
                .filter(def -> !new HashSet<>(def.getPermissions())
                        .equals(new HashSet<>(permissionDefs.get(def).getPermissions())))
                .forEach(def -> logger.warn("Different set of permissions found for " + def));

        newAnnos.removeAll(permissionDefs.keySet());
        if (!newAnnos.isEmpty()) {
            logger.info("New annotaions: " + newAnnos.size());
            logger.info("New annotaions: " + newAnnos);
            newAnnos.forEach(newAnno -> permissionDefs.put(newAnno, newAnno));
        }
    }

    /**
     * Save all collected annotations to a file.
     */
    private void finalizeCollectAnnoMode() throws JAXBException, IOException {
        Path aggregateAnnoFile = Paths.get(logDir.toString(), "_collected_perm_anno.xml");
        PermissionDefList out = new PermissionDefList();
        out.setPermissionDefs(new ArrayList<>(permissionDefs.keySet()));
        XmlPermDefMiner.save(out, aggregateAnnoFile.toFile());
    }
}
