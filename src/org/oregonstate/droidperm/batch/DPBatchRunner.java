package org.oregonstate.droidperm.batch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.*;
import org.oregonstate.droidperm.jaxb.JaxbCallbackList;
import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermTargetKind;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.sens.SensitiveCollectorJaxbData;
import org.oregonstate.droidperm.sens.SensitiveCollectorService;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PrintUtil;
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
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/5/2016.
 */
public class DPBatchRunner {

    private static Logger logger = LoggerFactory.getLogger(DPBatchRunner.class);

    @Parameter(names = "--apps-dir", description = "Directory with apk files, each in a separate dir", required = true)
    private Path appsDir;

    @Parameter(names = "--droid-perm-home", description = "Path to DroidPerm standalone installation dir",
            required = true)
    private Path droidPermHomeDir;

    @Parameter(names = "--log-dir", description = "Directory where log files will be stored", required = true)
    private Path logDir;

    @Parameter(names = "--cg-algo", description = "The call graph algorithm")
    private InfoflowConfiguration.CallgraphAlgorithm cgAlgo = InfoflowConfiguration.CallgraphAlgorithm.GEOM;

    @Parameter(names = "--perm-def-files", description = "Permission definition files, if custom")
    private String permDefFiles = "config/checker-param-sens-def.xml;config/perm-def-API-23.xml;"
            + "config/perm-def-play-services.xml;config/javadoc-perm-def-API-23.xml;config/perm-def-manual.xml";

    @Parameter(names = "--vm-args", description = "Additional VM arguments, separated by space. "
            + "If more than one, they should be included into quotes (\"\").")
    private String vmArgs;

    @Parameter(names = "--mode", description = "DroidPerm execution mode")
    private Mode mode = Mode.DROID_PERM;

    private enum Mode {
        DROID_PERM, TAINT_ANALYSIS, COLLECT_ANNO, COLLECT_SENSITIVES
    }

    @Parameter(names = "--fast-run",
            description = "If specified, analysis process won't be executed. "
                    + "Instead, batch log will be re-computed based on existing xml files. Useful for debugging.")
    private boolean fastRun;

    @Parameter(names = "--collect-method-sens-only-apps",
            description = "Collect apps that only use method-based dangerous permissions. "
                    + "Used in conjunction with COLLECT_SENSITIVES mode.")
    private boolean collectMethodSensOnlyApps;

    @Parameter(names = "--collect-method-or-field-sens-only-apps",
            description = "Collect apps that only use method-based or field-based dangerous permissions. "
                    + "No storage permissions. Used in conjunction with COLLECT_SENSITIVES mode.")
    private boolean collectMethodOrFieldSensOnlyApps;

    @Parameter(names = "--help", help = true)
    private boolean help = false;

    /**
     * Essentially the set of all collected permission definitions. Map from definitions to themselves.
     */
    private Map<PermissionDef, PermissionDef> permissionDefs = new LinkedHashMap<>();

    private List<String> appsWithSafeMethodSensOnly = new ArrayList<>();
    private List<String> appsWithSafeMethodOrFieldSensOnly = new ArrayList<>();
    private List<String> appsDeclaringNonStoragePermOnly = new ArrayList<>();
    private List<String> appsForEvaluation = new ArrayList<>();

    private enum PermUsage {
        MANIFEST, CODE, SENSITIVE
    }

    private static List<Set<PermUsage>> permSpectra = ImmutableList.of(
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE, PermUsage.SENSITIVE),   //normal usage
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE),                        //unknown sensitive
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.SENSITIVE),                   //incorrect migration to A6
            ImmutableSet.of(PermUsage.MANIFEST),                                        //overprivileged in A5
            ImmutableSet.of(PermUsage.CODE, PermUsage.SENSITIVE),                       //implausible
            ImmutableSet.of(PermUsage.CODE),                                            //implausible
            ImmutableSet.of(PermUsage.SENSITIVE)                                        //unreaclable sensitive
    );

    /**
     * Newspectrum for each tier compared to previous one.
     */
    @SuppressWarnings("unchecked")
    private static List<Set<PermUsage>> appTierSpectrumToAdd = Lists.newArrayList(
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE, PermUsage.SENSITIVE),
            ImmutableSet.of(PermUsage.SENSITIVE),
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE),
            ImmutableSet.of(PermUsage.MANIFEST),
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.SENSITIVE),
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE, PermUsage.SENSITIVE), //added 2nd time
            ImmutableSet.of(PermUsage.CODE, PermUsage.SENSITIVE),
            ImmutableSet.of(PermUsage.CODE));

    /**
     * Map from spectra sets that describe a tier to their names.
     */
    @SuppressWarnings("unchecked")
    private static List<Set<PermUsage>> appTierSpectrumToRemove = Lists.newArrayList(
            null,
            null,
            null,
            null,
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE, PermUsage.SENSITIVE),
            null, null, null);

    private static List<String> appTierNames =
            ImmutableList.of(
                    "safe apps",
                    "safe apps with unreachable sensitives",
                    "apps with unknown sensitives",
                    "apps with over-declared permissions",
                    "incorrectly migrated apps",
                    "apps with some permissions [MANIFEST, SENSITIVE]",
                    "impossible apps 1",
                    "impossible apps 2");

    private static Map<Set<Set<PermUsage>>, String> appTiers;

    static {
        appTiers = new LinkedHashMap<>();
        Set<Set<PermUsage>> prevTier = new LinkedHashSet<>();
        for (int i = 0; i < appTierNames.size(); i++) {
            Set<PermUsage> spectrumToAdd = appTierSpectrumToAdd.get(i);
            Set<PermUsage> spectrumToRemove = appTierSpectrumToRemove.get(i);
            String spectrumName = appTierNames.get(i);

            Set<Set<PermUsage>> tier = new LinkedHashSet<>(prevTier);
            if (spectrumToAdd != null) {
                tier.add(spectrumToAdd);
            }
            if (spectrumToRemove != null) {
                tier.remove(spectrumToRemove);
            }
            prevTier = tier;
            appTiers.put(tier, spectrumName);
        }
    }

    /**
     * If spectra below are present, this indicates the app might be either incorrectly migrated to API23 or have
     * unknown sensitives.
     */
    private static List<Set<PermUsage>> unsafePermSpectra = ImmutableList.of(
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE),
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.SENSITIVE),
            ImmutableSet.of(PermUsage.CODE, PermUsage.SENSITIVE),
            ImmutableSet.of(PermUsage.CODE)
    );

    private static Set<Set<PermUsage>> spectraForEvaluation = ImmutableSet.of(
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE, PermUsage.SENSITIVE),
            ImmutableSet.of(PermUsage.MANIFEST)
    );

    private static Set<PermUsage> normalSpectrum =
            ImmutableSet.of(PermUsage.MANIFEST, PermUsage.CODE, PermUsage.SENSITIVE);

    private Set<String> dangerousPerm = SensitiveCollectorService.getAllDangerousPerm();

    /**
     * Table from (app name, perm usage spectrum) to set of permissions having that spectrum.
     */
    Table<String, Set<PermUsage>, Set<String>> appToPermSpectraTable = HashBasedTable.create();

    /**
     * Run DroidPerm on all the apps in the given directory.
     * <p>
     * For help on command line arguments use option --help
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
        logger.info("fastRun: " + fastRun);
        logger.info("collectMethodSensOnlyApps: " + collectMethodSensOnlyApps);
        logger.info("collectMethodOrFieldSensOnlyApps: " + collectMethodOrFieldSensOnlyApps);
        logger.info("appsDir: " + appsDir);
        logger.info("droidPermHomeDir: " + droidPermHomeDir);
        logger.info("logDir: " + logDir);
        logger.info("cgalgo: " + cgAlgo);
        logger.info("permDefFiles: " + permDefFiles);
        logger.info("vmArgs: " + vmArgs + "\n");

        Files.createDirectories(logDir);
        ListMultimap<String, Path> appNamesToApksMap = Files.list(appsDir).sorted()
                .filter(path -> Files.isDirectory(path))
                .collect(MyCollectors.toMultimap(
                        LinkedListMultimap::create,
                        path -> path.getFileName().toString(),
                        path -> {
                            try {
                                return Files.walk(path)
                                        .filter(possibleApk -> possibleApk.getFileName().toString().endsWith(".apk"));
                            } catch (IOException e) {
                                //Looks like Java 8 lambdas don't like to throw checked exceptions.
                                throw new RuntimeException(e);
                            }
                        }
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

        switch (mode) {
            case DROID_PERM:
                printDroidPermBatchStatistics();
            case COLLECT_ANNO:
                saveCollectAnnoModeDigest();
                break;
            case COLLECT_SENSITIVES:
                printSafeApps();
                printAppsPermissionProfile();
                printPermissionSpectraStatistics();
                break;
        }
    }

    private void analyzeApp(String appName, Path apk) throws IOException, JAXBException {
        String droidPermClassPath = droidPermHomeDir + "/droid-perm.jar";
        String androidClassPath = droidPermHomeDir + "/android-23-cr+util_io.zip";
        Path logFile = Paths.get(logDir.toString(), appName + ".log");
        Path errorFile = Paths.get(logDir.toString(), appName + ".error.log");
        Path xmlOut = Paths.get(logDir.toString(), appName + ".out.xml");

        List<String> processBuilderArgs = new ArrayList<>();
        processBuilderArgs.add("java");
        if (vmArgs != null) {
            processBuilderArgs.addAll(Arrays.asList(vmArgs.split("\\s+")));
        }
        processBuilderArgs.addAll(Arrays.asList(
                "-jar", droidPermClassPath, apk.toAbsolutePath().toString(),
                androidClassPath));
        processBuilderArgs.addAll(Arrays.asList("--cgalgo", cgAlgo.name()));

        processBuilderArgs.addAll(Arrays.asList("--perm-def-files", permDefFiles));
        processBuilderArgs.addAll(Arrays.asList("--xml-out", xmlOut.toString()));

        switch (mode) {
            case DROID_PERM:
                break;
            case TAINT_ANALYSIS:
                processBuilderArgs.addAll(Arrays.asList(
                        "--taint-analysis-enabled", "true", "--logsourcesandsinks", "--pathalgo", "CONTEXTSENSITIVE"));
                break;
            case COLLECT_ANNO:
                processBuilderArgs.addAll(Collections.singletonList("--COLLECT-PERM-ANNO-MODE"));
                break;
            case COLLECT_SENSITIVES:
                processBuilderArgs.addAll(Collections.singletonList("--collect-sens-mode"));
                break;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(processBuilderArgs)
                .directory(droidPermHomeDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.to(errorFile.toFile()));

        logger.info(appName + " ... ");

        long time = System.currentTimeMillis();
        int exitCode;
        if (!fastRun) {
            Process process = processBuilder.start();
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                process.destroy();
                throw new RuntimeException(e);
            }
        } else {
            exitCode = Files.exists(xmlOut) ? 0 : 1;
        }

        long newTime = System.currentTimeMillis();
        logger.info(appName + " analyzed: " + (newTime - time) / 1E3 + " sec");
        if (exitCode == 0) {
            switch (mode) {
                case DROID_PERM:
                    droidPermModeFor(xmlOut, appName);
                    break;
                case COLLECT_ANNO:
                    collectAnnotationsFor(xmlOut);
                    break;
                case COLLECT_SENSITIVES:
                    collectSensitivesFor(xmlOut, appName);
                    break;
            }
        } else {
            logger.error(appName + " analysis returned exit code " + exitCode);
        }
    }

    int totalReachedSensEdges;
    int totalUndetectedCHASensDefs;

    private void droidPermModeFor(Path xmlOut, String appName) throws JAXBException {
        JaxbCallbackList data = JaxbUtil.load(JaxbCallbackList.class, xmlOut.toFile());
        logger.info("\t reached sensitive edges: " + data.getNrReachedSensEdges());
        totalReachedSensEdges += data.getNrReachedSensEdges();
        if (!data.getUndetectedCHADangerousPermDefs().isEmpty()) {
            logger.info(
                    "\t undetected CHA-reachable sensitive defs: " + data.getUndetectedCHADangerousPermDefs().size());
            totalUndetectedCHASensDefs += data.getUndetectedCHADangerousPermDefs().size();
        }
        if (!data.isCompileApi23Plus()) {
            logger.warn(appName + " : compileSdkVersion is < 23");
        }
        if (data.getTargetSdkVersion() != 23) {
            logger.warn(appName + " : targetSdkVersion = " + data.getTargetSdkVersion());
        }
    }

    private void collectAnnotationsFor(Path annoXmlFile) throws JAXBException {
        List<PermissionDef> newAnnos = JaxbUtil.load(PermissionDefList.class, annoXmlFile.toFile()).getPermissionDefs();
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

    private void collectSensitivesFor(Path xmlOut, String appName) throws IOException, JAXBException {
        SensitiveCollectorJaxbData data = JaxbUtil.load(SensitiveCollectorJaxbData.class, xmlOut.toFile());
        if (data.getTargetSdkVersion() != 23) {
            logger.warn(appName + " : targetSdkVersion = " + data.getTargetSdkVersion());
        }

        computePermSpectra(appName, data);

        boolean noUnsafeSpectra = unsafePermSpectra.stream()
                .allMatch(spectrum -> appToPermSpectraTable.get(appName, spectrum) == null);
        boolean appForEvaluation =
                Sets.difference(appToPermSpectraTable.row(appName).keySet(), spectraForEvaluation).isEmpty();
        boolean referredPermDefsOnlyMethod = data.getReferredPermDefs().stream()
                .allMatch(permDef -> permDef.getTargetKind() == PermTargetKind.Method);
        boolean safeMethodSensOnly = !data.getReferredPermDefs().isEmpty()
                && referredPermDefsOnlyMethod
                && Collections.disjoint(data.getAllDeclaredPerms(), SensitiveCollectorService.storagePerm)
                && noUnsafeSpectra;
        boolean safeMethodOrFieldSensOnly = !data.getReferredPermDefs().isEmpty()
                && Collections.disjoint(data.getAllDeclaredPerms(), SensitiveCollectorService.storagePerm)
                && noUnsafeSpectra;
        boolean declaresNonStoragePermOnly = !data.getDeclaredDangerousPerms().isEmpty()
                && Collections.disjoint(data.getAllDeclaredPerms(), SensitiveCollectorService.storagePerm);
        if (safeMethodSensOnly) {
            appsWithSafeMethodSensOnly.add(appName);
        }
        if (safeMethodOrFieldSensOnly) {
            appsWithSafeMethodOrFieldSensOnly.add(appName);
        }
        if (declaresNonStoragePermOnly) {
            appsDeclaringNonStoragePermOnly.add(appName);
        }
        if (appForEvaluation) {
            appsForEvaluation.add(appName);
        }
    }

    private void computePermSpectra(String appName, SensitiveCollectorJaxbData data) {
        //Part 1. Compute initial table for this app.
        Map<PermUsage, Set<String>> permUsagesMap = new HashMap<>();
        permUsagesMap.put(PermUsage.MANIFEST, new LinkedHashSet<>(data.getDeclaredDangerousPerms()));
        permUsagesMap.put(PermUsage.CODE, new LinkedHashSet<>(data.getReferredDangerousPerms()));
        permUsagesMap.put(PermUsage.SENSITIVE, new LinkedHashSet<>(data.getPermsWithSensitives()));
        for (Set<PermUsage> spectrum : permSpectra) {
            Set<String> permSet = new LinkedHashSet<>(dangerousPerm);
            for (PermUsage usage : PermUsage.values()) {
                if (spectrum.contains(usage)) {
                    permSet.retainAll(permUsagesMap.get(usage));
                } else {
                    permSet.removeAll(permUsagesMap.get(usage));
                }
            }
            //First we put all spectra in the table. The next for will cleanup the empty ones.
            appToPermSpectraTable.put(appName, spectrum, permSet);
        }

        //Part 2, alter the table according to special rules for Sensitives. See DP-379 for details.
        Set<String> normalSpectrumPerm = appToPermSpectraTable.get(appName, normalSpectrum);
        Set<PermissionDef> unsatisfiedPermDefs = data.getReferredPermDefs().stream()
                .filter(permDef -> Collections.disjoint(permDef.getPermissionNames(), normalSpectrumPerm))
                .collect(Collectors.toSet());
        Set<String> unsatisfiedPerm = unsatisfiedPermDefs.stream()
                .flatMap(permDef -> permDef.getPermissionNames().stream()).collect(Collectors.toSet());
        //more exactly those are all dangerous permissions which are "not unsatisfied"
        Set<String> satisfiedOnlyPerm = Sets.difference(dangerousPerm, unsatisfiedPerm);

        //For sensitive only spectra, retain only permissions which are among unsatisfied sensitives and ignore
        // the satisfied ones.
        Set<PermUsage> spectrumSensitive = ImmutableSet.of(PermUsage.SENSITIVE);
        appToPermSpectraTable.get(appName, spectrumSensitive).removeAll(satisfiedOnlyPerm);

        //For spectrum MANIFEST+SENSITIVE, treat permissions as MANIFEST only if they are satisfied.
        //E.g. move them from MANIFEST+SENSITIVE to MANIFEST.
        //If unsatisfied, leave them unchanged.
        Set<PermUsage> spectrumManifestAndSensitive = ImmutableSet.of(PermUsage.MANIFEST, PermUsage.SENSITIVE);
        Set<PermUsage> spectrumManifest = ImmutableSet.of(PermUsage.MANIFEST);
        Set<String> manifestAndSensButSatisfied = new LinkedHashSet<>(
                Sets.intersection(appToPermSpectraTable.get(appName, spectrumManifestAndSensitive),
                        satisfiedOnlyPerm));
        appToPermSpectraTable.get(appName, spectrumManifestAndSensitive).removeAll(manifestAndSensButSatisfied);
        appToPermSpectraTable.get(appName, spectrumManifest).addAll(manifestAndSensButSatisfied);

        //Part 3. Cleanup empty elements in the table and log results for the app.
        for (Set<PermUsage> spectrum : permSpectra) {
            Set<String> permSet = appToPermSpectraTable.get(appName, spectrum);
            if (permSet.isEmpty()) {
                appToPermSpectraTable.remove(appName, spectrum);
            } else {
                String logString = appName + " : permissions with spectrum " + spectrum + " : " + permSet.size();
                if (spectrum.equals(normalSpectrum)) {
                    logger.info(logString);
                } else {
                    logger.warn(logString);
                }
            }
        }
    }

    /**
     * Save all collected annotations to a file.
     */
    private void saveCollectAnnoModeDigest() throws JAXBException, IOException {
        Path aggregateAnnoFile = Paths.get(logDir.toString(), "_collected_perm_anno.xml");
        PermissionDefList out = new PermissionDefList();
        out.setPermissionDefs(new ArrayList<>(permissionDefs.keySet()));
        JaxbUtil.save(out, PermissionDefList.class, aggregateAnnoFile.toFile());
    }

    private void printSafeApps() {
        if (collectMethodSensOnlyApps) {
            PrintUtil.printCollection(appsWithSafeMethodSensOnly, "Apps with safe method sensitives only");
        }
        if (collectMethodOrFieldSensOnlyApps) {
            PrintUtil.printCollection(appsWithSafeMethodOrFieldSensOnly,
                    "Apps with safe method or field sensitives only");
            PrintUtil.printCollection(appsDeclaringNonStoragePermOnly, "Apps declaring non-storage permissions only");
        }
        PrintUtil.printCollection(appsForEvaluation, "Apps for evaluation, with spectra " + spectraForEvaluation);
    }

    private void printPermissionSpectraStatistics() {
        System.out.println("\n\nPermissions spectra statistics\n"
                + "========================================================================");
        for (Set<PermUsage> spectrum : permSpectra) {
            Map<String, Set<String>> spectrumData = appToPermSpectraTable.column(spectrum);
            Set<String> apps = spectrumData.keySet();
            if (apps.isEmpty() || spectrum.equals(normalSpectrum)) {
                System.out.println("\nApps with permission spectrum " + spectrum + " : " + apps.size());
            } else {
                System.out.println("\n\nApps with permission spectrum " + spectrum + " : " + apps.size() + "\n"
                        + "------------------------------------------------------------------------");
                for (String app : apps) {
                    Set<String> permSet = spectrumData.get(app);
                    System.out.println(app + " : " + permSet.size());
                    for (String perm : permSet) {
                        System.out.println("\t" + perm);
                    }
                }
            }
            if (!apps.isEmpty()) {
                Map<String, Long> permFrequency = spectrumData.values().stream().flatMap(Collection::stream)
                        .collect(Collectors.groupingBy(perm -> perm, TreeMap::new, Collectors.counting()));
                long totalInstances = permFrequency.values().stream().mapToLong(l -> l).sum();
                System.out.println(
                        "\n\nTotal permissions " + spectrum + " in some apps : " + permFrequency.size());
                System.out.println(
                        "Total <app, " + spectrum + " permission> instances : " + totalInstances + "\n"
                                + "------------------------------------------------------------------------");
                for (String perm : permFrequency.keySet()) {
                    System.out.println(perm + " : " + permFrequency.get(perm));
                }
            }
        }
    }

    private void printAppsPermissionProfile() {
        SetMultimap<Set<Set<PermUsage>>, String> tiersToAppsMap = LinkedHashMultimap.create();
        Set<String> appsInPrevTierAndAbove = Collections.emptySet();
        for (Set<Set<PermUsage>> tier : appTiers.keySet()) {
            Set<String> appsInTierAndAbove = new LinkedHashSet<>(appsInPrevTierAndAbove);
            appToPermSpectraTable.rowKeySet().stream()
                    .filter(app -> tier.containsAll(appToPermSpectraTable.row(app).keySet()))
                    .forEach(appsInTierAndAbove::add);
            Set<String> appsInTier = Sets.difference(appsInTierAndAbove, appsInPrevTierAndAbove);
            tiersToAppsMap.putAll(tier, appsInTier);
            appsInPrevTierAndAbove = appsInTierAndAbove;
        }

        System.out.println("\n\nApp tiers by permission spectra. Total tiers : " + tiersToAppsMap.keySet().size()
                + "\n========================================================================");

        int tierNr = 0;
        for (Set<Set<PermUsage>> tier : tiersToAppsMap.keySet()) {
            tierNr++;
            Set<String> apps = tiersToAppsMap.get(tier);
            System.out.println("\n\nTier " + tierNr + " : " + appTiers.get(tier) + " : " + apps.size());

            //printing the spectra set. One spectrum per line.
            boolean first = true;
            for (Set<PermUsage> spectrum : tier) {
                if (first) {
                    System.out.print("[");
                    first = false;
                } else {
                    System.out.print(",\n ");
                }
                System.out.print(spectrum);
            }
            System.out.println("]"
                    + "\n------------------------------------------------------------------------");

            //printing app profiles for this spectra set
            for (String app : apps) {
                System.out.println("\n" + app + "\n+-------------------------------------");
                for (Set<PermUsage> spectrum : tier) {
                    Set<String> permSet = appToPermSpectraTable.get(app, spectrum);
                    if (permSet != null) {
                        System.out.println("\t" + spectrum);
                        permSet.forEach(perm -> System.out.println("\t\t" + perm));
                    }
                }
            }
        }
    }

    private void printDroidPermBatchStatistics() {
        System.out.println("\n\nBatch runner statistics\n"
                + "========================================================================");
        System.out.println("Total undetected CHA-reachable sensitive defs_ : " + totalUndetectedCHASensDefs);
        System.out.println("Total reached sensitive edges_ : " + totalReachedSensEdges);
    }

    /**
     * Comapre 2 permission profiles (sets of sets of permission usages).
     * <p>
     * A profile is "smaller" if it contains a "smaller" spectrum. Thus profiles will be sorted in the order of smallest
     * spectrum they contain.
     */
    private class PermProfilesComparator implements Comparator<Set<Set<PermUsage>>> {
        @Override
        public int compare(Set<Set<PermUsage>> o1, Set<Set<PermUsage>> o2) {
            for (Set<PermUsage> spectrum : permSpectra) {
                if (o1.contains(spectrum) == o2.contains(spectrum)) {
                    continue;
                }
                return o1.contains(spectrum) ? -1 : 1;
            }
            return 0;
        }
    }
}
