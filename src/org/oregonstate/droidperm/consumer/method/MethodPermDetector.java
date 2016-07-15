package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.jaxb.*;
import org.oregonstate.droidperm.perm.IPermissionDefProvider;
import org.oregonstate.droidperm.perm.TxtPermissionDefParser;
import org.oregonstate.droidperm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private File permissionDefFile;
    private static final File outflowIgnoreListFile = new File("OutflowIgnoreList.txt");
    private File txtOut;
    private File xmlOut;

    @SuppressWarnings("FieldCanBeLocal")
    private MethodOrMethodContext dummyMainMethod;

    private Set<MethodOrMethodContext> permCheckers;

    //toperf holders for checkers and sensitives could be combined into one. One traversal could produce both.
    //this is actually required for flow-sensitive analysis.
    @SuppressWarnings("FieldCanBeLocal")
    private CallPathHolder checkerPathsHolder;
    private Map<MethodOrMethodContext, Set<String>> callbackToCheckedPermsMap;

    private Map<AndroidMethod, Set<MethodOrMethodContext>> resolvedSensitiveDefs;
    private Map<MethodOrMethodContext, AndroidMethod> sensitiveToSensitiveDefMap;

    /**
     * This set is sorted.
     */
    private LinkedHashSet<MethodOrMethodContext> sensitives;

    /**
     * A map from permission sets to sets of resolved sensitive method definitions requiring this permission set.
     */
    private Map<Set<String>, Set<AndroidMethod>> permissionToSensitiveDefMap;

    private Map<Set<String>, LinkedHashSet<MethodOrMethodContext>> permsToSensitivesMap;
    private LinkedHashMap<MethodOrMethodContext, List<MethodInContext>> sensitiveToSensInContextMap;

    private ContextSensOutflowCPHolder sensitivePathsHolder;

    private Map<MethodOrMethodContext, Set<String>> callbackToRequiredPermsMap;
    private Set<String> sometimesNotCheckedPerms;

    /**
     * Map from callback to lvl 2 map describing checked permissions in this callback.
     * <p>
     * Lvl2 map: from checked permissions to usage status of this check: used, unused or possibly used through ICC.
     */
    private Map<MethodOrMethodContext, Map<String, CheckerUsageStatus>> callbackCheckerStatusMap;

    private JaxbCallbackList jaxbData;

    public MethodPermDetector(File permissionDefFile, File txtOut, File xmlOut) {
        this.permissionDefFile = permissionDefFile;
        this.txtOut = txtOut;
        this.xmlOut = xmlOut;
    }

    public void analyzeAndPrint() {
        long startTime = System.currentTimeMillis();
        logger.warn("\n\n"
                + "Start of DroidPerm logs\n"
                + "===========================================\n");
        analyze();
        printResults();

        System.out.println("\nDroidPerm execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze() {
        Options.v().set_allow_phantom_refs(false); // prevents PointsToAnalysis from being released

        IPermissionDefProvider permissionDefProvider;
        Set<SootMethod> outflowIgnoreSet;
        try {
            permissionDefProvider = new TxtPermissionDefParser(permissionDefFile);
            outflowIgnoreSet = OutflowIgnoreListLoader.load(outflowIgnoreListFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Set<SootMethodAndClass> permCheckerDefs = permissionDefProvider.getPermCheckerDefs();
        Set<AndroidMethod> sensitiveDefs = permissionDefProvider.getSensitiveDefs();

        dummyMainMethod = getDummyMain();
        permCheckers = CallGraphUtil.getNodesFor(HierarchyUtil.resolveAbstractDispatches(permCheckerDefs));
        resolvedSensitiveDefs = CallGraphUtil.resolveCallGraphEntriesToMap(sensitiveDefs);
        sensitives = resolvedSensitiveDefs.values().stream().flatMap(Collection::stream)
                .sorted(new MethodOrMCSortingComparator())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        //sensitives should be added to ignore method list, to prevent their body from being analyzed
        outflowIgnoreSet.addAll(
                sensitives.stream().map(MethodOrMethodContext::method).collect(Collectors.toList())
        );

        //checkers

        checkerPathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, permCheckers, outflowIgnoreSet);
        callbackToCheckedPermsMap = CheckerUtil.buildCallbackToCheckedPermsMap(checkerPathsHolder);

        //sensitives
        sensitiveToSensitiveDefMap = buildSensitiveToSensitiveDefMap();
        permissionToSensitiveDefMap = buildPermissionToSensitiveDefMap(resolvedSensitiveDefs.keySet());
        permsToSensitivesMap = buildPermsToSensitivesMap();

        //select one of the call path algorithms.
        //sensitivePathsHolder = new OutflowCPHolder(dummyMainMethod, sensitives);
        //sensitivePathsHolder = new InflowCPHolder(dummyMainMethod, sensitives);
        //sensitivePathsHolder = new PartialPointsToCPHolder(dummyMainMethod, sensitives, outflowIgnoreSet);
        sensitivePathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, sensitives, outflowIgnoreSet);

        sensitiveToSensInContextMap = buildSensitiveToSensInContextMap();
        callbackToRequiredPermsMap = buildCallbackToRequiredPermsMap();
        sometimesNotCheckedPerms = buildSometimesNotCheckedPerms();
        callbackCheckerStatusMap = buildCheckerStatusMap();
        jaxbData = JaxbUtil.buildJaxbData(this);
        //DebugUtil.printTargets(sensitives);
    }

    private LinkedHashMap<MethodOrMethodContext, List<MethodInContext>> buildSensitiveToSensInContextMap() {
        return sensitives.stream().collect(Collectors.toMap(
                meth -> meth,
                meth -> sensitivePathsHolder.getSensitivesInContext().stream()
                        .filter(sensInC -> sensInC.method == meth)
                        .sorted(new MethodInContext.SortingComparator())
                        .collect(Collectors.toList()),
                StreamUtil.throwingMerger(),
                LinkedHashMap::new
        ));
    }

    private void printResults() {
        //setupApp.printProducerDefs();
        //setupApp.printConsumerDefs();
        sensitivePathsHolder.printPathsFromCallbackToSensitive();
        if (sensitivePathsHolder instanceof ContextSensOutflowCPHolder) {
            printSensitivesInContextToCallbacks();
        }
        printUnusedChecks();

        printReachableSensitivesInCallbackStmts(jaxbData, System.out);
        printCheckersInContext();
        printSensitivesInContext();

        //Printing to files
        if (txtOut != null) {
            try (PrintStream summaryOut = new PrintStream(new FileOutputStream(txtOut))) {
                printReachableSensitivesInCallbackStmts(jaxbData, summaryOut);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        if (xmlOut != null) {
            try {
                JaxbUtil.save(jaxbData, xmlOut);
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        }
        //DebugUtil.pointsToTest();
    }

    private Map<Set<String>, LinkedHashSet<MethodOrMethodContext>> buildPermsToSensitivesMap() {
        return sensitives.stream().collect(
                Collectors.groupingBy(this::getPermissionsFor, Collectors.toCollection(LinkedHashSet::new)));
    }

    private Set<String> getPermissionsFor(MethodOrMethodContext sensitive) {
        return permissionToSensitiveDefMap.keySet().stream().filter(
                permSet -> {
                    Set<AndroidMethod> sensitiveDefs = permissionToSensitiveDefMap.get(permSet);
                    return sensitiveDefs.stream().map(sensDef -> resolvedSensitiveDefs.get(sensDef))
                            .anyMatch(methSet -> methSet.contains(sensitive));
                }
        ).findAny().orElse(null);
    }

    private Map<MethodOrMethodContext, AndroidMethod> buildSensitiveToSensitiveDefMap() {
        return resolvedSensitiveDefs.keySet().stream().map(def ->
                resolvedSensitiveDefs.get(def).stream().collect(Collectors.toMap(sens -> sens, sens -> def))
        ).collect(MyCollectors.toFlatMap());
    }

    private Map<Set<String>, Set<AndroidMethod>> buildPermissionToSensitiveDefMap(Set<AndroidMethod> permissionDefs) {
        return permissionDefs.stream().collect(Collectors.toMap(
                sensitiveDef -> new HashSet<>(sensitiveDef.getPermissions()),
                sensitiveDef -> new HashSet<>(Collections.singleton(sensitiveDef)),
                StreamUtil::mutableUnion //merge function for values
        ));
    }

    private static MethodOrMethodContext getDummyMain() {
        String sig = "<dummyMainClass: void dummyMainMethod(java.lang.String[])>";
        return CallGraphUtil.getEntryPointMethod(Scene.v().getMethod(sig));
    }

    private void printSensitivesInContextToCallbacks() {
        System.out.println("\n\nFor each sensitive in context, reaching callbacks" +
                "\n====================================");

        for (Set<String> permSet : permissionToSensitiveDefMap.keySet()) {
            //sorting methods by toString() efficiently, without computing toString() each time.
            List<MethodInContext> sortedSensitives = permsToSensitivesMap.get(permSet).stream()
                    .flatMap(meth -> sensitiveToSensInContextMap.get(meth).stream()).collect(Collectors.toList());

            if (!sortedSensitives.isEmpty()) {
                System.out.println("\n" + permSet + "\n------------------------------------");
            }

            for (MethodInContext sensInC : sortedSensitives) {
                System.out.println("\nSensitive: " + sensInC);

                Set<MethodOrMethodContext> reachableCallbacks =
                        sensitivePathsHolder.getSensitiveInCToCallbacksMap().get(sensInC);
                Map<PermCheckStatus, List<MethodOrMethodContext>> permCheckStatusToCallbacks =
                        reachableCallbacks.stream().sorted(SortUtil.getSootMethodPrettyPrintComparator())
                                .collect(Collectors.groupingBy(
                                        callback -> getPermCheckStatusForAny(permSet, callback)));

                System.out.println("From callbacks:");
                for (PermCheckStatus status : PermCheckStatus.values()) {
                    if (permCheckStatusToCallbacks.get(status) != null) {
                        System.out.println("\tPerm check " + status + ":");
                        for (MethodOrMethodContext callback : permCheckStatusToCallbacks.get(status)) {
                            System.out.println("\t\t" + callback);
                        }
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * @return A value indicating whether the permissions in this set are checked by permission checks in the given
     * callback. Checks for multiple permissions are linked by OR relationship.
     * <p>
     * For multiple permissions OR (ANY ONE OF) is the most commonly used case, to account for the 2 Location
     * permissions.
     */
    private PermCheckStatus getPermCheckStatusForAny(Set<String> permSet, MethodOrMethodContext callback) {
        if (callbackToCheckedPermsMap.get(callback) != null) {
            if (!Collections.disjoint(permSet, callbackToCheckedPermsMap.get(callback))) {
                return PermCheckStatus.DETECTED;
            }
        }
        return PermCheckStatus.NOT_DETECTED;
    }

    /**
     * @return A value indicating whether the permissions in this set are checked by permission checks in the given
     * callback. Checks for multiple permissions are linked by AND relationship.
     */
    public PermCheckStatus getPermCheckStatusForAll(Collection<String> permSet, MethodOrMethodContext callback) {
        if (callbackToCheckedPermsMap.get(callback) != null) {
            if (callbackToCheckedPermsMap.get(callback).containsAll(permSet)) {
                return PermCheckStatus.DETECTED;
            }
        }
        return PermCheckStatus.NOT_DETECTED;
    }

    public Map<String, CheckerUsageStatus> getCheckerStatusMap(MethodOrMethodContext callback) {
        return callbackCheckerStatusMap.get(callback);
    }

    private static void printReachableSensitivesInCallbackStmts(JaxbCallbackList data, PrintStream out) {
        out.println("\nRequired permissions for code inside each callback:");
        out.println("========================================================================");

        for (JaxbCallback callback : data.getCallbacks()) {
            out.println("\n" + callback + " :");
            for (JaxbStmt jaxbStmt : callback.getStmts()) {
                String checkMsg = jaxbStmt.allGuarded() ? "" : " --- checks INCOMPLETE";
                out.println("    " + jaxbStmt.getLine() + ": "
                        + jaxbStmt.getCallFullSignature() + " : " + jaxbStmt.getPermDisplayStrings() + checkMsg);
            }
        }
        out.println();
    }

    public Set<String> getPermissionsFor(Collection<MethodOrMethodContext> sensitives) {
        return sensitives.stream().map(sens -> sensitiveToSensitiveDefMap.get(sens)).map(AndroidMethod::getPermissions)
                .collect(MyCollectors.toFlatSet());
    }

    /**
     * All sensitives in context are printed here, including those that are not reachable from callbacks.
     */
    public void printSensitivesInContext() {
        System.out.println("\n\nSensitives in context in the call graph: \n====================================");
        for (Set<String> permSet : permsToSensitivesMap.keySet()) {
            System.out.println("\n" + permSet + "\n------------------------------------");

            CallGraph cg = Scene.v().getCallGraph();
            for (MethodOrMethodContext sens : permsToSensitivesMap.get(permSet)) {
                boolean printed = false;
                Iterable<Edge> edgesInto = IteratorUtil.asIterable(cg.edgesInto(sens));

                for (Edge edgeInto : edgesInto) {
                    //We don't print sensitives whose context is another sensitive.
                    //noinspection ConstantConditions
                    if (sens.method().getDeclaringClass() != edgeInto.src().getDeclaringClass()) {
                        if (!printed) {
                            System.out.println(sens);
                            printed = true;
                        }
                        System.out.println("\tfrom " + edgeInto.getSrc());
                    }
                }
            }
        }
    }

    /**
     * All checkers in context are printed here, including those that are not reachable from callbacks.
     */
    public void printCheckersInContext() {
        System.out.println("\n\nCheckers in context in the call graph: \n====================================");
        CallGraph cg = Scene.v().getCallGraph();
        for (MethodOrMethodContext sens : permCheckers) {
            boolean printed = false;
            Iterable<Edge> edgesInto = IteratorUtil.asIterable(cg.edgesInto(sens));

            for (Edge edgeInto : edgesInto) {
                //We don't print checkers whose context is another checker.
                //noinspection ConstantConditions
                if (sens.method().getDeclaringClass() != edgeInto.src().getDeclaringClass()) {
                    if (!printed) {
                        System.out.println(sens);
                        printed = true;
                    }
                    System.out.println("\tfrom " + edgeInto.getSrc());
                    System.out.println("\t\t Permissions: " + CheckerUtil.getPossiblePermissionsFromChecker(edgeInto));
                }
            }
        }
    }

    public CallPathHolder getSensitivePathsHolder() {
        return sensitivePathsHolder;
    }

    public enum PermCheckStatus {
        DETECTED("Permission check detected"),
        NOT_DETECTED("No permission check detected");

        private String description;

        PermCheckStatus(String description) {
            this.description = description;
        }

        @SuppressWarnings("unused")
        public String description() {
            return description;
        }
    }

    private Map<MethodOrMethodContext, Set<String>> buildCallbackToRequiredPermsMap() {
        return sensitivePathsHolder.getReachableCallbacks().stream().collect(Collectors.toMap(
                callback -> callback,
                callback -> sensitivePathsHolder.getCallsToSensitiveFor(callback).stream()
                        .map(sensitiveCall -> sensitiveToSensitiveDefMap.get(sensitiveCall.getTgt()).getPermissions())
                        .collect(MyCollectors.toFlatSet())
        ));
    }

    private Set<String> buildSometimesNotCheckedPerms() {
        return callbackToRequiredPermsMap.keySet().stream().flatMap(callback ->
                callbackToRequiredPermsMap.get(callback).stream()
                        //only keep permissions that are required but not checked, globally
                        .filter(perm -> callbackToCheckedPermsMap.get(callback) == null ||
                                !callbackToCheckedPermsMap.get(callback).contains(perm))
        ).collect(Collectors.toSet());
    }

    private Map<MethodOrMethodContext, Map<String, CheckerUsageStatus>> buildCheckerStatusMap() {
        return callbackToCheckedPermsMap.keySet().stream().collect(Collectors.toMap(
                callback -> callback,
                callback -> {
                    Set<String> perms = callbackToCheckedPermsMap.get(callback);

                    return perms.stream().collect(Collectors.toMap(
                            perm -> perm,
                            perm -> getCheckUsageStatus(callback, perm)
                    ));
                }
        ));
    }

    private CheckerUsageStatus getCheckUsageStatus(MethodOrMethodContext callback, String perm) {
        Set<String> reqPerms = callbackToRequiredPermsMap.get(callback);
        reqPerms = reqPerms != null ? reqPerms : Collections.emptySet();

        if (reqPerms.contains(perm)) {
            return CheckerUsageStatus.USED;
        } else if (sometimesNotCheckedPerms.contains(perm)) {
            return CheckerUsageStatus.UNUSED_POSSIBLY_ICC;
        } else {
            return CheckerUsageStatus.UNUSED;
        }
    }

    private void printUnusedChecks() {
        System.out.println("\nChecked permissions inside each callback:");
        System.out.println("========================================================================");

        for (MethodOrMethodContext callback : checkerPathsHolder.getReachableCallbacks()) {
            //now callbacks are nicely sorted
            System.out.println("\n" + callback + " :");
            for (String perm : callbackCheckerStatusMap.get(callback).keySet()) {
                CheckerUsageStatus status = callbackCheckerStatusMap.get(callback).get(perm);
                String statusString = status == CheckerUsageStatus.USED ? "used"
                                                                        : status == CheckerUsageStatus.UNUSED
                                                                          ? "NOT used"
                                                                          : "NOT used POSSIBLY ICC";
                System.out.printf("    %-50s  status: %-20s\n", perm, statusString);
            }
        }
        System.out.println();
    }

}
