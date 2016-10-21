/* ******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE. All rights reserved. This program and the
 * accompanying materials are made available under the terms of the GNU Lesser Public License v2.1 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p>
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric Bodden, and others.
 ******************************************************************************/
package org.oregonstate.droidperm;

import org.oregonstate.droidperm.anno.PermAnnotationService;
import org.oregonstate.droidperm.consumer.method.MethodPermDetector;
import org.oregonstate.droidperm.infoflow.android.DPSetupApplication;
import org.oregonstate.droidperm.perm.IPermissionDefProvider;
import org.oregonstate.droidperm.perm.TxtPermissionDefProvider;
import org.oregonstate.droidperm.perm.XMLPermissionDefParser;
import org.oregonstate.droidperm.perm.miner.AggregatePermDefProvider;
import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.DebugUtil;
import org.oregonstate.droidperm.util.UnitComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.Main;
import soot.Scene;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.source.AndroidSourceSinkManager.LayoutMatchingMode;
import soot.jimple.infoflow.data.pathBuilders.DefaultPathBuilderFactory.PathBuilder;
import soot.jimple.infoflow.ipc.IIPCManager;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.options.Options;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Adapted from FlowDroid Test class.
 *
 * @see soot.jimple.infoflow.android.TestApps.Test
 */
public class DroidPermMain {

    private static final Logger logger = LoggerFactory.getLogger(DroidPermMain.class);

    private static int repeatCount = 1;
    private static int timeout = -1;
    private static int sysTimeout = -1;

    private static boolean aggressiveTaintWrapper = false;
    private static boolean noTaintWrapper = false;
    public static CallgraphAlgorithm dummyMainGenCGAlgo = CallgraphAlgorithm.CHA;
    private static String summaryPath;

    /**
     * Xml output of FlowDroid. Option "--saveresults {File}".
     */
    private static String flowDroidXmlOut;

    private static String additionalClasspath = "";
    private static File txtPermDefFile = new File("PermissionDefs.txt");
    private static File xmlPermDefFile;
    private static boolean useAnnoPermDef;//whether to use permission annotations.
    private static File txtOut;
    private static File xmlOut;


    private static final boolean DEBUG = true;

    private static InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
    private static IIPCManager ipcManager = null;
    private static File callGraphDumpFile;
    private static boolean printAnnoPermDef;

    /**
     * If true then only permission annotations are collected. DroidPerm is not executed.
     */
    private static boolean collectPermAnnoOnly;

    static {
        // DroidPerm default config options
        //during code ellimination sometimes a new class is added which deleted the PointsToAnalysis.
        config.setCodeEliminationMode(InfoflowConfiguration.CodeEliminationMode.NoCodeElimination);
        config.setEnableCallbackSources(false);

        //Optimal settings after latest updates.
        config.setCallgraphAlgorithm(CallgraphAlgorithm.GEOM);
        config.setCallbackAnalyzer(InfoflowAndroidConfiguration.CallbackAnalyzer.Fast);
        config.setTaintAnalysisEnabled(false);
    }

    private static long initTime;

    /**
     * @param args Program arguments. args[0] = path to apk-file, args[1] = path to android-dir
     *             (path/android-platforms/)
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 2 || !parseAdditionalOptions(args) || !validateAdditionalOptions()) {
            printUsage();
            return;
        }

        File inputFileOrDir = new File(args[0]);
        String androidJarOrSdkDir = args[1];

        //run the analysis
        cleanupTempDir();

        List<File> apkFiles = gatherApkFiles(inputFileOrDir);

        for (final File apkFile : apkFiles) {
            runAnalysisForFile(androidJarOrSdkDir, apkFile);
        }
    }

    /**
     * No references to JimpleOutput in the project. Most likely not needed.
     */
    private static void cleanupTempDir() {
        File outputDir = new File("JimpleOutput");
        if (outputDir.isDirectory()) {
            boolean success = true;
            //noinspection ConstantConditions
            for (File file : outputDir.listFiles()) {
                success = success && file.delete();
            }
            if (!success) {
                System.err.println("Cleanup of output directory " + outputDir + " failed!");
            }
            if (!outputDir.delete()) {
                System.err.println("Cleanup of output directory " + outputDir + " failed!");
            }
        }
    }

    private static List<File> gatherApkFiles(File apkFileOrDir) throws IOException {
        List<File> apkFiles = new ArrayList<>();
        if (apkFileOrDir.isDirectory()) {
            String[] apkFileNames = apkFileOrDir.list((dir, name) -> (name.endsWith(".apk")));
            assert apkFileNames != null; //If it's null, there's nothing to do. Program should crash.
            for (String dirFile : apkFileNames) {
                apkFiles.add(new File(dirFile));
            }
        } else {
            //apk is a file so grab the extension
            String extension = apkFileOrDir.getName().substring(apkFileOrDir.getName().lastIndexOf("."));
            if (extension.equalsIgnoreCase(".txt")) {
                BufferedReader rdr = new BufferedReader(new FileReader(apkFileOrDir));
                String line;
                while ((line = rdr.readLine()) != null) {
                    apkFiles.add(new File(line));
                }
                rdr.close();
            } else if (extension.equalsIgnoreCase(".apk")) {
                apkFiles.add(apkFileOrDir);
            } else {
                throw new RuntimeException("Invalid input file extension: " + extension);
            }
        }
        return apkFiles;
    }

    /**
     * Parses the optional command-line arguments
     *
     * @param args The array of arguments to parse
     * @return True if all arguments are valid and could be parsed, otherwise false
     */
    private static boolean parseAdditionalOptions(String[] args) {
        int i = 2;
        while (i < args.length) {
            if (args[i].equalsIgnoreCase("--timeout")) {
                timeout = Integer.valueOf(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--systimeout")) {
                sysTimeout = Integer.valueOf(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--singleflow")) {
                config.setStopAfterFirstFlow(true);
                i++;
            } else if (args[i].equalsIgnoreCase("--implicit")) {
                config.setEnableImplicitFlows(true);
                i++;
            } else if (args[i].equalsIgnoreCase("--nostatic")) {
                config.setEnableStaticFieldTracking(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--aplength")) {
                InfoflowAndroidConfiguration.setAccessPathLength(Integer.valueOf(args[i + 1]));
                i += 2;
            } else if (args[i].equalsIgnoreCase("--cgalgo")) {
                String algo = args[i + 1];
                if (algo.equalsIgnoreCase("AUTO")) {
                    config.setCallgraphAlgorithm(CallgraphAlgorithm.AutomaticSelection);
                } else if (algo.equalsIgnoreCase("CHA")) {
                    config.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
                } else if (algo.equalsIgnoreCase("VTA")) {
                    config.setCallgraphAlgorithm(CallgraphAlgorithm.VTA);
                } else if (algo.equalsIgnoreCase("RTA")) {
                    config.setCallgraphAlgorithm(CallgraphAlgorithm.RTA);
                } else if (algo.equalsIgnoreCase("SPARK")) {
                    config.setCallgraphAlgorithm(CallgraphAlgorithm.SPARK);
                } else if (algo.equalsIgnoreCase("GEOM")) {
                    config.setCallgraphAlgorithm(CallgraphAlgorithm.GEOM);
                } else {
                    System.err.println("Invalid callgraph algorithm");
                    return false;
                }
                i += 2;
            } else if (args[i].equalsIgnoreCase("--nocallbacks")) {
                config.setEnableCallbacks(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--noexceptions")) {
                config.setEnableExceptionTracking(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--layoutmode")) {
                String algo = args[i + 1];
                if (algo.equalsIgnoreCase("NONE")) {
                    config.setLayoutMatchingMode(LayoutMatchingMode.NoMatch);
                } else if (algo.equalsIgnoreCase("PWD")) {
                    config.setLayoutMatchingMode(LayoutMatchingMode.MatchSensitiveOnly);
                } else if (algo.equalsIgnoreCase("ALL")) {
                    config.setLayoutMatchingMode(LayoutMatchingMode.MatchAll);
                } else {
                    System.err.println("Invalid layout matching mode");
                    return false;
                }
                i += 2;
            } else if (args[i].equalsIgnoreCase("--aliasflowins")) {
                config.setFlowSensitiveAliasing(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--paths")) {
                config.setComputeResultPaths(true);
                i++;
            } else if (args[i].equalsIgnoreCase("--nopaths")) {
                config.setComputeResultPaths(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--aggressivetw")) {
                aggressiveTaintWrapper = false;
                i++;
            } else if (args[i].equalsIgnoreCase("--pathalgo")) {
                String algo = args[i + 1];
                if (algo.equalsIgnoreCase("CONTEXTSENSITIVE")) {
                    config.setPathBuilder(PathBuilder.ContextSensitive);
                } else if (algo.equalsIgnoreCase("CONTEXTINSENSITIVE")) {
                    config.setPathBuilder(PathBuilder.ContextInsensitive);
                } else if (algo.equalsIgnoreCase("SOURCESONLY")) {
                    config.setPathBuilder(PathBuilder.ContextInsensitiveSourceFinder);
                } else {
                    System.err.println("Invalid path reconstruction algorithm");
                    return false;
                }
                i += 2;
            } else if (args[i].equalsIgnoreCase("--summarypath")) {
                summaryPath = args[i + 1];
                i += 2;
            } else if (args[i].equalsIgnoreCase("--saveresults")) {
                flowDroidXmlOut = args[i + 1];
                i += 2;
            } else if (args[i].equalsIgnoreCase("--sysflows")) {
                config.setIgnoreFlowsInSystemPackages(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--notaintwrapper")) {
                noTaintWrapper = true;
                i++;
            } else if (args[i].equalsIgnoreCase("--repeatcount")) {
                repeatCount = Integer.parseInt(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--noarraysize")) {
                config.setEnableArraySizeTainting(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--arraysize")) {
                config.setEnableArraySizeTainting(true);
                i++;
            } else if (args[i].equalsIgnoreCase("--notypetightening")) {
                InfoflowAndroidConfiguration.setUseTypeTightening(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--safemode")) {
                InfoflowAndroidConfiguration.setUseThisChainReduction(false);
                i++;
            } else if (args[i].equalsIgnoreCase("--logsourcesandsinks")) {
                config.setLogSourcesAndSinks(true);
                i++;
            } else if (args[i].equalsIgnoreCase("--callbackanalyzer")) {
                String algo = args[i + 1];
                if (algo.equalsIgnoreCase("DEFAULT")) {
                    config.setCallbackAnalyzer(InfoflowAndroidConfiguration.CallbackAnalyzer.Default);
                } else if (algo.equalsIgnoreCase("FAST")) {
                    config.setCallbackAnalyzer(InfoflowAndroidConfiguration.CallbackAnalyzer.Fast);
                } else {
                    System.err.println("Invalid callback analysis algorithm");
                    return false;
                }
                i += 2;
            } else if (args[i].equalsIgnoreCase("--maxthreadnum")) {
                config.setMaxThreadNum(Integer.valueOf(args[i + 1]));
                i += 2;
            } else if (args[i].equalsIgnoreCase("--arraysizetainting")) {
                config.setEnableArraySizeTainting(true);
                i++;

                //new in DroidPerm - additional classpath for analysis
            } else if (args[i].equalsIgnoreCase("--additionalCP")) {
                additionalClasspath = args[i + 1];
                i += 2;
            } else if (args[i].equalsIgnoreCase("--taint-analysis-enabled")) {
                config.setTaintAnalysisEnabled(Boolean.parseBoolean(args[i + 1]));
                i += 2;
            } else if (args[i].equalsIgnoreCase("--code-elimination-mode")) {
                config.setCodeEliminationMode(InfoflowConfiguration.CodeEliminationMode.valueOf(args[i + 1]));
                i += 2;
            } else if (args[i].equalsIgnoreCase("--PERM-DEF-FILE")) {
                txtPermDefFile = new File(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--XML-PERM-DEF-FILE")) {
                xmlPermDefFile = new File(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--USE-ANNO-PERM-DEF")) {
                useAnnoPermDef = true;
                i++;
            } else if (args[i].equalsIgnoreCase("--TXT-OUT")) {
                txtOut = new File(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--XML-OUT")) {
                xmlOut = new File(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--CALL-GRAPH-DUMP-FILE")) {
                callGraphDumpFile = new File(args[i + 1]);
                i += 2;
            } else if (args[i].equalsIgnoreCase("--PRINT-ANNO-PERM-DEF")) {
                printAnnoPermDef = true;
                i++;
            } else if (args[i].equalsIgnoreCase("--COLLECT-PERM-ANNO-ONLY")) {
                collectPermAnnoOnly = true;
                i++;
            } else {
                throw new IllegalArgumentException("Invalid option: " + args[i]);
            }
        }
        return true;
    }

    private static boolean validateAdditionalOptions() {
        if (timeout > 0 && sysTimeout > 0) {
            return false;
        }
        if (!config.getFlowSensitiveAliasing()
                && config.getCallgraphAlgorithm() != CallgraphAlgorithm.OnDemand
                && config.getCallgraphAlgorithm() != CallgraphAlgorithm.AutomaticSelection) {
            System.err.println("Flow-insensitive aliasing can only be configured for callgraph "
                    + "algorithms that support this choice.");
            return false;
        }

        if (!txtPermDefFile.exists()) {
            logger.error("FATAL: Txt permission definition file not found: " + txtPermDefFile);
            return false;
        }
        return true;
    }

    private static void printUsage() {
        System.out.println("FlowDroid (c) Secure Software Engineering Group @ EC SPRIDE");
        System.out.println();
        System.out.println("Incorrect arguments: [0] = apk-file, [1] = android-jar-directory");
        System.out.println("Optional further parameters:");
        System.out.println("\t--TIMEOUT n Time out after n seconds");
        System.out.println("\t--SYSTIMEOUT n Hard time out (kill process) after n seconds, Unix only");
        System.out.println("\t--SINGLEFLOW Stop after finding first leak");
        System.out.println("\t--IMPLICIT Enable implicit flows");
        System.out.println("\t--NOSTATIC Disable static field tracking");
        System.out.println("\t--NOEXCEPTIONS Disable exception tracking");
        System.out.println("\t--APLENGTH n Set access path length to n");
        System.out.println("\t--CGALGO x Use callgraph algorithm x");
        System.out.println("\t--NOCALLBACKS Disable callback analysis");
        System.out.println("\t--LAYOUTMODE x Set UI control analysis mode to x");
        System.out.println("\t--ALIASFLOWINS Use a flow insensitive alias search");
        System.out.println("\t--NOPATHS Do not compute result paths");
        System.out.println("\t--AGGRESSIVETW Use taint wrapper in aggressive mode");
        System.out.println("\t--PATHALGO Use path reconstruction algorithm x");
        System.out.println("\t--LIBSUMTW Use library summary taint wrapper");
        System.out.println("\t--SUMMARYPATH Path to library summaries");
        System.out.println("\t--SYSFLOWS Also analyze classes in system packages");
        System.out.println("\t--NOTAINTWRAPPER Disables the use of taint wrappers");
        System.out.println("\t--NOTYPETIGHTENING Disables the use of taint wrappers");
        System.out.println("\t--LOGSOURCESANDSINKS Print out concrete source/sink instances");
        System.out.println("\t--CALLBACKANALYZER x Uses callback analysis algorithm x");
        System.out.println("\t--MAXTHREADNUM x Sets the maximum number of threads to be used by the analysis to x");
        System.out.println();
        System.out.println("New in DroidPerm:");
        System.out.println("\t--ADDITIONALCP Additional classpath for API code, besides android.jar");
        System.out.println("\t--PERM-DEF-FILE Path to permission definitions file. Default is PermissionDefs.txt");
        System.out
                .println("\t--XML-PERM-DEF-FILE Path to xml permission definitions file. Optional. Even if specified, "
                        + "a txt perm def file is also necessary for checker definitions.");
        System.out.println(
                "\t--USE-ANNO-PERM-DEF Use permission definitions provided as @RequiresPermission annotations.");
        System.out.println("\t--TAINT-ANALYSIS-ENABLED true/false.");
        System.out.println("\t--CODE-ELIMINATION-MODE Various options for irrelevant code elimination.");
        System.out.println("\t--TXT-OUT DroidPerm output file: txt format.");
        System.out.println("\t--XML-OUT DroidPerm output file: xml format.");
        System.out.println("\t--CALL-GRAPH-DUMP-FILE <file>: Dump the call graph to a file.");
        System.out.println("\t--PRINT-ANNO-PERM-DEF: Print available permission def annoations.");
        System.out.println("\t--COLLECT-PERM-ANNO-ONLY: Only collect permission annotations. Do not run DroidPerm."
                + " Option --xml-out if specified will be the file where annotations are stored.");
        System.out.println();
        System.out.println("Supported callgraph algorithms: AUTO, CHA, RTA, VTA, SPARK, GEOM");
        System.out.println("Supported layout mode algorithms: NONE, PWD, ALL");
        System.out.println("Supported path algorithms: CONTEXTSENSITIVE, CONTEXTINSENSITIVE, SOURCESONLY");
        System.out.println("Supported callback algorithms: DEFAULT, FAST");
        System.out.println("Options for CODE-ELIMINATION-MODE: NoCodeElimination, PropagateConstants, " +
                "RemoveSideEffectFreeCode");
        System.out.println();
        System.out.println("Options relevant for DroidPerm:");
        System.out.println("\t--NOTAINTWRAPPER - required to enable the analysis of framework classes");
        System.out.println("\t--ADDITIONALCP - doesn't work for now, " +
                "current workaround is to instrument android.jar and put all the classpath inside");

    }

    private static String callgraphAlgorithmToString(CallgraphAlgorithm algorihm) {
        switch (algorihm) {
            case AutomaticSelection:
                return "AUTO";
            case CHA:
                return "CHA";
            case VTA:
                return "VTA";
            case RTA:
                return "RTA";
            case SPARK:
                return "SPARK";
            case GEOM:
                return "GEOM";
            default:
                return "unknown";
        }
    }

    private static String layoutMatchingModeToString(LayoutMatchingMode mode) {
        switch (mode) {
            case NoMatch:
                return "NONE";
            case MatchSensitiveOnly:
                return "PWD";
            case MatchAll:
                return "ALL";
            default:
                return "unknown";
        }
    }

    private static String pathAlgorithmToString(PathBuilder pathBuilder) {
        switch (pathBuilder) {
            case ContextSensitive:
                return "CONTEXTSENSITIVE";
            case ContextInsensitive:
                return "CONTEXTINSENSITIVE";
            case ContextInsensitiveSourceFinder:
                return "SOURCESONLY";
            default:
                return "UNKNOWN";
        }
    }

    private static String callbackAlgorithmToString(InfoflowAndroidConfiguration.CallbackAnalyzer analyzer) {
        switch (analyzer) {
            case Default:
                return "DEFAULT";
            case Fast:
                return "FAST";
            default:
                return "UNKNOWN";
        }
    }

    private static void runAnalysisForFile(String androidJarORSdkDir, File apkFile) throws IOException, JAXBException {
        initTime = System.nanoTime();

        // Directory handling
        System.out.println("Analyzing file " + apkFile + "...");

        if (collectPermAnnoOnly) {
            collectPermAnno(apkFile);
            return;
        }

        // Run FlowDroid
        System.gc();
        if (timeout > 0) {
            runAnalysisTimeout(apkFile.getAbsolutePath(), androidJarORSdkDir);
        } else if (sysTimeout > 0) {
            runAnalysisSysTimeout(apkFile.getAbsolutePath(), androidJarORSdkDir);
        } else {
            runAnalysis(apkFile.getAbsolutePath(), androidJarORSdkDir);
        }

        if (callGraphDumpFile != null) {
            DebugUtil.dumpPointsToAndCallGraph(callGraphDumpFile);
        }

        //Run DroidPerm
        if (printAnnoPermDef) {
            PermAnnotationService.printAnnoPermDefs();
        }
        IPermissionDefProvider permDefProvider = getPermDefProvider();
        new MethodPermDetector(txtOut, xmlOut, permDefProvider).analyzeAndPrint();
        System.out.println("Total run time: " + (System.nanoTime() - initTime) / 1E9 + " seconds");
    }

    private static void collectPermAnno(File apkFile) throws JAXBException, IOException {
        String apkFilePath = apkFile.getAbsolutePath();

        Options.v().set_allow_phantom_refs(true);
        Options.v().set_src_prec(Options.src_prec_apk_class_jimple);
        Options.v().set_process_dir(Collections.singletonList(apkFilePath));
        Options.v().set_soot_classpath(apkFilePath);
        Options.v().set_process_multiple_dex(true);
        Main.v().autoSetOptions();
        Scene.v().loadNecessaryClasses();

        PermAnnotationService.printAnnoPermDefs();
        if (xmlOut != null) {
            PermissionDefList out = new PermissionDefList();
            out.setPermissionDefs(PermAnnotationService.getPermissionDefs());
            XmlPermDefMiner.save(out, xmlOut);
        }
    }

    private static IPermissionDefProvider getPermDefProvider() throws IOException {
        try {
            TxtPermissionDefProvider txtPermDefProvider = new TxtPermissionDefProvider(txtPermDefFile);
            List<Set<AndroidMethod>> permDefSources = new ArrayList<>();
            permDefSources.add(txtPermDefProvider.getSensitiveDefs());
            if (xmlPermDefFile != null) {
                permDefSources.add(new XMLPermissionDefParser(xmlPermDefFile).getSensitiveDefs());
            }
            if (useAnnoPermDef) {
                permDefSources.add(PermAnnotationService.getSensitiveDefs());
            }
            //noinspection unchecked
            return new AggregatePermDefProvider(txtPermDefProvider.getPermCheckerDefs(),
                    permDefSources.toArray(new Set[permDefSources.size()]));
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private static InfoflowResults runAnalysis(final String fileName, final String androidJar) {
        try {
            final DPSetupApplication setupApplication =
                    new DPSetupApplication(androidJar, fileName, additionalClasspath, ipcManager);

            // Set configuration object
            setupApplication.setConfig(config);

            //toclean insert additional Soot options here
            setupApplication.setSootConfig(options -> {
                //Many apk files have multiple de files inside. This option is required for them.
                //Some risks are possible when option is enabled, for example when the same class is in several dex
                // files.
                options.set_process_multiple_dex(true);

                options.set_keep_line_number(true);
                options.set_include_all(true);

                //Required to distinguish between application and library classes in DroidPerm
                Options.v().set_process_dir(Collections.singletonList(fileName));

                //options.setPhaseOption("cg.spark", "dump-html"); //output format is unintelligible.

                //just some execution statistics, nothing useful for debugging.
                options.setPhaseOption("cg.spark", "geom-dump-verbose:sootOutput/geom-dump-verbose");

                //options.set_verbose(true);//for low-level debugging of Soot.
            });

            setupApplication.setTaintWrapper(createTaintWrapper());
            setupApplication.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");

            if (DEBUG) {
                setupApplication.printEntrypoints();
                setupApplication.printSourceDefinitions();
                setupApplication.printSinkDefinitions();
            }

            System.out.println("Running data flow analysis...");
            final InfoflowResults res
                    = setupApplication.runInfoflow(new FlowDroidResultsAvailableHandler(flowDroidXmlOut));

            printSourcesAndSinks(setupApplication);
            System.out.println("FlowDroid has run for " + (System.nanoTime() - initTime) / 1E9 + " seconds\n\n");

            return res;
        } catch (IOException ex) {
            throw new RuntimeException("Could not read file: " + ex.getMessage(), ex);
        } catch (XmlPullParserException ex) {
            throw new RuntimeException("Could not read Android manifest file: " + ex.getMessage(), ex);
        }
    }

    private static void runAnalysisTimeout(final String fileName, final String androidJar) {
        FutureTask<InfoflowResults> task = new FutureTask<>(() -> {

            try (BufferedWriter wr = new BufferedWriter(
                    new FileWriter("_out_" + new File(fileName).getName() + ".txt"))) {
                final long beforeRun = System.nanoTime();
                wr.write("Running data flow analysis...\n");
                final InfoflowResults res = runAnalysis(fileName, androidJar);
                wr.write("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds\n");

                wr.flush();
                return res;
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(task);

        try {
            System.out.println("Running infoflow task...");
            task.get(timeout, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            System.err.println("Infoflow computation failed: " + e.getMessage());
            e.printStackTrace();
        } catch (TimeoutException e) {
            System.err.println("Infoflow computation timed out: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("Infoflow computation interrupted: " + e.getMessage());
            e.printStackTrace();
        }

        // Make sure to remove leftovers
        executor.shutdown();
    }

    /**
     * Duplication: Should reuse code from runAnalysis(). Not used in DroidPerm.
     */
    private static void runAnalysisSysTimeout(final String fileName, final String androidJar) {
        String classpath = System.getProperty("java.class.path");
        String javaHome = System.getProperty("java.home");
        String executable = "/usr/bin/timeout";
        String[] command = new String[]{executable,
                                        "-s", "KILL",
                                        sysTimeout + "m",
                                        javaHome + "/bin/java",
                                        "-cp", classpath,
                                        "soot.jimple.infoflow.android.TestApps.Test",
                                        fileName,
                                        androidJar,
                                        config.getStopAfterFirstFlow() ? "--singleflow" : "--nosingleflow",
                                        config.getEnableImplicitFlows() ? "--implicit" : "--noimplicit",
                                        config.getEnableStaticFieldTracking() ? "--static" : "--nostatic",
                                        "--aplength",
                                        Integer.toString(InfoflowAndroidConfiguration.getAccessPathLength()),
                                        "--cgalgo", callgraphAlgorithmToString(config.getCallgraphAlgorithm()),
                                        config.getEnableCallbacks() ? "--callbacks" : "--nocallbacks",
                                        config.getEnableExceptionTracking() ? "--exceptions" : "--noexceptions",
                                        "--layoutmode", layoutMatchingModeToString(config.getLayoutMatchingMode()),
                                        config.getFlowSensitiveAliasing() ? "--aliasflowsens" : "--aliasflowins",
                                        config.getComputeResultPaths() ? "--paths" : "--nopaths",
                                        aggressiveTaintWrapper ? "--aggressivetw" : "--nonaggressivetw",
                                        "--pathalgo", pathAlgorithmToString(config.getPathBuilder()),
                                        (summaryPath != null && !summaryPath.isEmpty()) ? "--summarypath" : "",
                                        (summaryPath != null && !summaryPath.isEmpty()) ? summaryPath : "",
                                        (flowDroidXmlOut != null && !flowDroidXmlOut.isEmpty()) ? "--saveresults" : "",
                                        noTaintWrapper ? "--notaintwrapper" : "",
                                        //				"--repeatCount", Integer.toString(repeatCount),
                                        config.getEnableArraySizeTainting() ? "" : "--noarraysize",
                                        InfoflowAndroidConfiguration.getUseTypeTightening() ? "" : "--notypetightening",
                                        InfoflowAndroidConfiguration.getUseThisChainReduction() ? "" : "--safemode",
                                        config.getLogSourcesAndSinks() ? "--logsourcesandsinks" : "",
                                        "--callbackanalyzer", callbackAlgorithmToString(config.getCallbackAnalyzer()),
                                        "--maxthreadnum", Integer.toString(config.getMaxThreadNum()),
                                        config.getEnableArraySizeTainting() ? "--arraysizetainting" : ""
        };
        System.out.println("Running command: " + executable + " " + Arrays.toString(command));
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectOutput(new File("out_" + new File(fileName).getName() + "_" + repeatCount + ".txt"));
            pb.redirectError(new File("err_" + new File(fileName).getName() + "_" + repeatCount + ".txt"));
            Process proc = pb.start();
            proc.waitFor();
        } catch (IOException ex) {
            System.err.println("Could not execute timeout command: " + ex.getMessage());
            ex.printStackTrace();
        } catch (InterruptedException ex) {
            System.err.println("Process was interrupted: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static ITaintPropagationWrapper createTaintWrapper() throws IOException {
        final ITaintPropagationWrapper taintWrapper;
        if (noTaintWrapper) {
            taintWrapper = null;
        } else if (summaryPath != null && !summaryPath.isEmpty()) {
            System.out.println("Using the StubDroid taint wrapper");
            taintWrapper = LibrarySummaryTWBuilder.createLibrarySummaryTW(summaryPath);
            if (taintWrapper == null) {
                throw new RuntimeException("Could not initialize StubDroid");
            }
        } else {
            final EasyTaintWrapper easyTaintWrapper;
            if (new File("../soot-infoflow/EasyTaintWrapperSource.txt").exists()) {
                easyTaintWrapper = new EasyTaintWrapper("../soot-infoflow/EasyTaintWrapperSource.txt");
            } else {
                easyTaintWrapper = new EasyTaintWrapper("EasyTaintWrapperSource.txt");
            }
            easyTaintWrapper.setAggressiveMode(aggressiveTaintWrapper);
            taintWrapper = easyTaintWrapper;
        }
        return taintWrapper;
    }

    private static void printSourcesAndSinks(DPSetupApplication setupApplication) {
        if (config.isTaintAnalysisEnabled() && config.getLogSourcesAndSinks()) {
            if (!setupApplication.getCollectedSources().isEmpty()) {
                List<Stmt> sortedSources = setupApplication.getCollectedSources().stream()
                        .sorted(new UnitComparator()).collect(Collectors.toList());

                System.out.println("\nCollected sources:");
                for (Stmt stmt : sortedSources) {
                    System.out.println("\t" + CallGraphUtil.getStmtToMethodMap().get(stmt));
                    System.out.println("\t\t" + stmt + " : " + stmt.getJavaSourceStartLineNumber());
                    System.out.println("\t\tSourceType: " + setupApplication.getSourceType(stmt));
                }
            }
            if (!setupApplication.getCollectedSinks().isEmpty()) {
                List<Stmt> sortedSinks = setupApplication.getCollectedSinks().stream()
                        .sorted(new UnitComparator()).collect(Collectors.toList());

                System.out.println("\nCollected sinks:");
                for (Stmt stmt : sortedSinks) {
                    System.out.println("\t" + CallGraphUtil.getStmtToMethodMap().get(stmt));
                    System.out.println("\t\t" + stmt + " : " + stmt.getJavaSourceStartLineNumber());
                }
            }
            System.out.println();
        }
    }
}
