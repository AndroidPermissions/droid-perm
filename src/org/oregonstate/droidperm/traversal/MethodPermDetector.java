package org.oregonstate.droidperm.traversal;

import com.google.common.collect.*;
import org.oregonstate.droidperm.debug.DebugUtil;
import org.oregonstate.droidperm.jaxb.JaxbCallback;
import org.oregonstate.droidperm.jaxb.JaxbCallbackList;
import org.oregonstate.droidperm.jaxb.JaxbStmt;
import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.main.DroidPermMain;
import org.oregonstate.droidperm.scene.*;
import org.oregonstate.droidperm.sens.DPProcessManifest;
import org.oregonstate.droidperm.sens.SensitiveCollectorService;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PrintUtil;
import org.oregonstate.droidperm.util.SortUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private File txtOut;
    private File xmlOut;
    private ScenePermissionDefService scenePermDef;
    private CallGraphPermDefService cgService;
    private final DPProcessManifest manifest;

    private ClasspathFilter classpathFilter;

    public static final MethodOrMethodContext dummyMainMethod = getDummyMain();

    private CheckerAnalysis checkerAnalysis;

    private LinkedHashSet<Edge> sensEdges;
    private ContextSensOutflowCPHolder sensitivePathsHolder;

    private SceneAnalysisResult sceneResult;
    private JaxbCallbackList jaxbData;

    @SuppressWarnings("FieldCanBeLocal")
    private long startTime;
    private long lastStepTime;

    private static MethodOrMethodContext getDummyMain() {
        String sig = "<dummyMainClass: void dummyMainMethod(java.lang.String[])>";
        return Scene.v().getMethod(sig);
    }

    public MethodPermDetector(File txtOut, File xmlOut, ScenePermissionDefService scenePermDef,
                              ClasspathFilter classpathFilter, File apkFile)
            throws IOException, XmlPullParserException {
        this.txtOut = txtOut;
        this.xmlOut = xmlOut;
        this.scenePermDef = scenePermDef;
        this.classpathFilter = classpathFilter;
        this.cgService = new CallGraphPermDefService(scenePermDef);
        this.manifest = new DPProcessManifest(apkFile);
    }

    public void analyzeAndPrint() throws Exception {
        startTime = System.currentTimeMillis();
        lastStepTime = startTime;
        logger.info("\n\n"
                + "Start of DroidPerm logs\n"
                + "========================================================================\n");
        analyze();
        printResults();

        System.out.println("\n\nDroidPerm execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze() throws Exception {
        SceneUtil.getMethodOf(null);//triggering stmt to method map initialziation

        long currentTime = System.currentTimeMillis();
        System.out.println("\n\nDroidPerm stmt to method map build time: "
                + (currentTime - lastStepTime) / 1E3 + " seconds");
        lastStepTime = currentTime;

        if (DroidPermMain.augmentCallGraph) {
            CallGraphUtil.augmentCGWithSafeEdges(classpathFilter);
        }

        logger.info("Processing sensitives");
        sensEdges = cgService.buildSensEdges();
        sensitivePathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, sensEdges, classpathFilter, cgService);
        SetMultimap<MethodOrMethodContext, String> callbackToRequiredPermsMap = buildCallbackToRequiredPermsMap();

        logger.info("Processing checkers");
        LinkedHashSet<Edge> checkerEdges = CallGraphUtil.getEdgesInto(scenePermDef.getPermCheckers());
        checkerAnalysis = new CheckerAnalysis(dummyMainMethod, checkerEdges, classpathFilter, cgService,
                callbackToRequiredPermsMap);

        //Section 4: Scene undetected items analysis
        Set<Stmt> sensFieldRefs = this.sensEdges.stream()
                .flatMap(edge -> cgService.getSensitiveArgInitializerStmts(edge).stream()).collect(Collectors.toSet());
        Set<Edge> detectedCheckEdges = checkerAnalysis.getDetectedCheckerEdges();
        sceneResult = UndetectedItemsUtil.sceneAnalysis(sensEdges, sensFieldRefs, detectedCheckEdges, scenePermDef,
                classpathFilter, dummyMainMethod.method());

        jaxbData = JaxbUtil.buildJaxbData(this, checkerAnalysis);
    }

    private void printResults() throws Exception {
        DebugUtil.logClassesWithCallbacks(sensitivePathsHolder.getUiCallbacks());
        DebugUtil.logFragments(sensitivePathsHolder.getUiCallbacks());
        sensitivePathsHolder.printPathsFromCallbackToSensitive();
        printReachableSensitivesInCallbackStmts(jaxbData, System.out);

        long currentTime = System.currentTimeMillis();
        System.out.println("\n\nDroidPerm reachability analysis execution time: "
                + (currentTime - lastStepTime) / 1E3 + " seconds");
        lastStepTime = currentTime;

        printSceneResults(sceneResult);

        //Printing main results - sensitives and checkers in context
        checkerAnalysis.printCheckersInContext(true);
        printSensitivesInContext(true);

        checkerAnalysis.printCheckersInContext(false);
        printSensitivesInContext(false);

        //Printing to files
        if (txtOut != null) {
            try (PrintStream summaryOut = new PrintStream(new FileOutputStream(txtOut))) {
                printReachableSensitivesInCallbackStmts(jaxbData, summaryOut);
            }
        }
        if (xmlOut != null) {
            jaxbData.setUndetectedCHADangerousPermDefs(
                    SensitiveCollectorService.retainDangerousPermissionDefs(sceneResult.permDefsCHA));
            jaxbData.setUnreachableDangerousPermDefs(
                    SensitiveCollectorService.retainDangerousPermissionDefs(sceneResult.permDefs));
            jaxbData.setCompileApi23Plus(scenePermDef.isCompileSdkVersion_23_OrMore());
            jaxbData.setTargetSdkVersion(manifest.targetSdkVersion());
            JaxbUtil.save(jaxbData, JaxbCallbackList.class, xmlOut);
        }
        System.out.println("\n\nDroidPerm checker/sensitive summaries execution time: "
                + (System.currentTimeMillis() - lastStepTime) / 1E3 + " seconds");
    }

    private void printSceneResults(SceneAnalysisResult sceneResult) {
        UndetectedItemsUtil.printUndetectedSensitives(sceneResult.permToReferredMethodSensMapCHA,
                "Undetected method sensitives, CHA-reachable", false);
        UndetectedItemsUtil.printUndetectedSensitives(sceneResult.permToReferredFieldSensMapCHA,
                "Undetected field sensitives, CHA-reachable", true);
        PrintUtil.printMultimapOfStmtValues(sceneResult.checkersCHA, "Undetected checkers, CHA-reachable", "", "\t",
                "from ", false);
        PrintUtil.printMultimapOfStmtValues(sceneResult.requestersCHA, "Requesters, CHA-reachable", "", "\t", "from ",
                false);

        UndetectedItemsUtil.printUndetectedSensitives(sceneResult.permToReferredMethodSensMap,
                "Unreachable method sensitives", false);
        UndetectedItemsUtil.printUndetectedSensitives(sceneResult.permToReferredFieldSensMap,
                "Unreachable field sensitives", true);
        PrintUtil.printMultimapOfStmtValues(sceneResult.checkers, "Unreachable checkers", "", "\t", "from ",
                false);
        PrintUtil.printMultimapOfStmtValues(sceneResult.requesters, "Unreachable requesters", "", "\t", "from ", false);

        Multimap<String, Stmt> referredDangerousPerm =
                SceneUtil.resolveConstantUsages(SensitiveCollectorService.getAllDangerousPerm(), classpathFilter);
        SensitiveCollectorService.printPermissionReferences(referredDangerousPerm);
    }

    private SetMultimap<MethodOrMethodContext, String> buildCallbackToRequiredPermsMap() {
        return sensitivePathsHolder.getSortedReachableCallbacks().stream().collect(MyCollectors.toMultimap(
                callback -> callback,
                callback -> sensitivePathsHolder.getCallsToSensitiveFor(callback).stream()
                        .flatMap(sensEdge -> cgService.getPermissionsFor(sensEdge).stream())
        ));
    }

    private void printReachableSensitivesInCallbackStmts(JaxbCallbackList data, PrintStream out) {
        out.println("\nOutput for droid-perm-plugin, required permissions for statements directly inside callbacks:");
        out.println("========================================================================");

        for (JaxbCallback callback : data.getCallbacks()) {
            out.println("\n" + callback + " :");
            for (JaxbStmt jaxbStmt : callback.getStmts()) {
                String checkMsg = jaxbStmt.allGuarded() ? "" : " --- checks INCOMPLETE";
                out.println("    " + jaxbStmt.getLine() + ": "
                        + jaxbStmt.getCallFullSignature() + " : " + jaxbStmt.getPermDisplayStrings() + checkMsg);
            }
        }
    }

    /**
     * All sensitives in context are printed here, including those that are not reachable from callbacks.
     */
    public void printSensitivesInContext(boolean printCallbacks) {
        String noCallbacksHeader = "Sensitives in context in the call graph:";
        String callbacksHeader = "Sensitives in context in the call graph, with reaching callbacks:";
        String header = printCallbacks ? callbacksHeader : noCallbacksHeader;
        System.out.println(
                "\n\n" + header + " \n========================================================================");
        Multimap<Set<String>, Edge> permsToSensEdgesMap = buildPermsToSensEdgesMap();
        for (Set<String> permSet : permsToSensEdgesMap.keySet()) {
            System.out.println("\n" + permSet + "\n------------------------------------");

            Multimap<MethodOrMethodContext, Edge> sensToSensEdgesMap = permsToSensEdgesMap.get(permSet).stream()
                    .collect(MyCollectors.toMultimapGroupingBy(Edge::getTgt));
            Map<Set<CheckerAnalysis.PermCheckStatus>, Integer> sensitivesCountByStatus = new HashMap<>();
            for (MethodOrMethodContext sens : sensToSensEdgesMap.keySet()) {
                cgService.printSensitiveHeader(sens, "\n");
                for (Edge sensEdge : sensToSensEdgesMap.get(sens)) {
                    cgService.printSensitiveContext(sensEdge, "\t");
                    if (TryCatchCheckerUtil.isTryCatchChecked(sensEdge)) {
                        System.out.println("\t\tTRY-CATCH CHECKED");
                    }

                    ListMultimap<CheckerAnalysis.PermCheckStatus, MethodOrMethodContext> permCheckStatusToCallbacks =
                            getPermCheckStatusToCallbacksMap(sensEdge, permSet);
                    if (!classpathFilter.test(sensEdge.src())) {
                        System.out.println("\t\tCallbacks: BLOCKED");
                    } else if (!permCheckStatusToCallbacks.isEmpty()) {
                        List<MethodOrMethodContext> callbacks = permCheckStatusToCallbacks.values().stream()
                                .collect(Collectors.toList());
                        System.out.println("\t\tCallback types: " + CallbackTypeUtil.getCallbackTypes(callbacks));
                        for (CheckerAnalysis.PermCheckStatus status : CheckerAnalysis.PermCheckStatus.values()) {
                            List<MethodOrMethodContext> methodsForStatus = permCheckStatusToCallbacks.get(status);
                            if (!methodsForStatus.isEmpty()) {
                                System.out.println(
                                        "\t\tCallbacks where " + status + ": " + methodsForStatus.size());
                                if (printCallbacks) {
                                    methodsForStatus.forEach(
                                            meth -> System.out.println("\t\t\t" + meth
                                                    + (sensitivePathsHolder.isPathAmbiguous(meth, sensEdge)
                                                       ? "" : ", path is NON-AMBIGUOUS")));
                                }
                            }
                        }
                    } else {
                        System.out.println("\t\tCallbacks: NONE, POSSIBLY BLOCKED");
                    }

                    //count by status
                    Set<CheckerAnalysis.PermCheckStatus> statuses = permCheckStatusToCallbacks.keySet();
                    int oldCount =
                            sensitivesCountByStatus.containsKey(statuses) ? sensitivesCountByStatus.get(statuses)
                                                                          : 0;
                    sensitivesCountByStatus.put(statuses, oldCount + 1);
                }
            }

            if (!printCallbacks) {
                System.out.println();
                sensitivesCountByStatus.keySet().forEach(statuses
                        -> System.out.println(
                        "Total sensitives+context with status " + statuses
                                + " : " + sensitivesCountByStatus.get(statuses))
                );
            }
        }
    }

    private ListMultimap<CheckerAnalysis.PermCheckStatus, MethodOrMethodContext> getPermCheckStatusToCallbacksMap(
            Edge sensEdge, Set<String> permSet) {
        Set<MethodOrMethodContext> reachableCallbacks = sensitivePathsHolder.getReachingCallbacks(sensEdge);
        if (reachableCallbacks == null) {
            reachableCallbacks = Collections.emptySet();
        }
        //noinspection Convert2MethodRef
        return reachableCallbacks.stream().sorted(SortUtil.methodOrMCComparator).collect(
                MyCollectors.toMultimapGroupingBy(() -> ArrayListMultimap.create(),
                        callback -> checkerAnalysis.getPermCheckStatusForAny(permSet, callback)));
    }

    private Multimap<Set<String>, Edge> buildPermsToSensEdgesMap() {
        //noinspection Convert2MethodRef
        return sensEdges.stream().collect(
                MyCollectors.toMultimapGroupingBy(() -> LinkedHashMultimap.create(), cgService::getPermissionsFor));
    }

    public CallGraphPermDefService getCgService() {
        return cgService;
    }

    public ContextSensOutflowCPHolder getSensitivePathsHolder() {
        return sensitivePathsHolder;
    }

    public SceneAnalysisResult getSceneResult() {
        return sceneResult;
    }

}
