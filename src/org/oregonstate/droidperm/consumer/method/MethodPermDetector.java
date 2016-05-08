package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.jaxb.JaxbCallback;
import org.oregonstate.droidperm.jaxb.JaxbCallbackList;
import org.oregonstate.droidperm.jaxb.JaxbStmt;
import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.PermissionDefParser;
import org.oregonstate.droidperm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.options.Options;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private File permissionDefFile;
    private File txtOut;
    private File xmlOut;

    @SuppressWarnings("FieldCanBeLocal")
    private MethodOrMethodContext dummyMainMethod;

    private Set<MethodOrMethodContext> permCheckers;

    //toperf holders for checkers and sensitives could be combined into one. One traversal could produce both.
    @SuppressWarnings("FieldCanBeLocal")
    private CallPathHolder checkerPathsHolder;
    private Map<MethodOrMethodContext, Set<String>> callbackToCheckedPermsMap;

    private Map<AndroidMethod, Set<MethodOrMethodContext>> resolvedSensitiveDefs;
    private Map<MethodOrMethodContext, AndroidMethod> sensitiveToSensitiveDefMap;
    private Set<MethodOrMethodContext> sensitives;

    /**
     * A map from permission sets to sets of resolved sensitive method definitions requiring this permission set.
     */
    private Map<Set<String>, Set<AndroidMethod>> permissionToSensitiveDefMap;

    private CallPathHolder sensitivePathsHolder;

    /**
     * Map from callback to the set of permissions checked but unused within that callback.
     */
    private Map<MethodOrMethodContext, Set<String>> unusedChecks;
    private JaxbCallbackList jaxbData;

    public MethodPermDetector(File permissionDefFile, File txtOut, File xmlOut) {
        this.permissionDefFile = permissionDefFile;
        this.txtOut = txtOut;
        this.xmlOut = xmlOut;
    }

    public void analyzeAndPrint() {
        long startTime = System.currentTimeMillis();
        analyze();
        printResults();

        System.out.println("DroidPerm execution time: " + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze() {
        Options.v().set_allow_phantom_refs(false); // prevents PointsToAnalysis from being released

        PermissionDefParser permissionDefParser;
        try {
            permissionDefParser = new PermissionDefParser(permissionDefFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Set<SootMethodAndClass> permCheckerDefs = permissionDefParser.getPermCheckerDefs();
        Set<AndroidMethod> sensitiveDefs = permissionDefParser.getSensitiveDefs();

        dummyMainMethod = getDummyMain();

        //checkers
        permCheckers = CallGraphUtil.getNodesFor(HierarchyUtil.resolveAbstractDispatches(permCheckerDefs));
        checkerPathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, permCheckers);
        callbackToCheckedPermsMap = CheckerUtil.buildCallbackToCheckedPermsMap(checkerPathsHolder);

        //sensitives
        resolvedSensitiveDefs = CallGraphUtil.resolveCallGraphEntriesToMap(sensitiveDefs);
        sensitiveToSensitiveDefMap = buildSensitiveToSensitiveDefMap();

        sensitives = resolvedSensitiveDefs.values().stream().collect(MyCollectors.toFlatSet());
        permissionToSensitiveDefMap = buildPermissionToSensitiveDefMap(resolvedSensitiveDefs.keySet());

        //select one of the call path algorithms.
        //sensitivePathsHolder = new OutflowCPHolder(dummyMainMethod, sensitives);
        //sensitivePathsHolder = new InflowCPHolder(dummyMainMethod, sensitives);
        sensitivePathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, sensitives);

        unusedChecks = buildUnusedChecks();
        jaxbData = JaxbUtil.buildJaxbData(this);
        //DebugUtil.printTargets(sensitives);
    }

    private void printResults() {
        //setupApp.printProducerDefs();
        //setupApp.printConsumerDefs();
        printCheckers();
        printSensitives();
        sensitivePathsHolder.printPathsFromCallbackToSensitive();
        printCoveredCallbacks();

        //Print main results tu System.out and optionally to a file
        printReachableSensitivesInCallbackStmts(jaxbData, System.out);
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

        printUnusedChecks();
        //DebugUtil.pointsToTest();
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

    private void printCoveredCallbacks() {
        System.out
                .println("\n\nCovered callbacks for each permission/sensitive \n====================================");

        for (Set<String> permSet : permissionToSensitiveDefMap.keySet()) {
            System.out.println("\n" + permSet + "\n------------------------------------");

            //sorting methods by toString() efficiently, without computing toString() each time.
            Collection<MethodOrMethodContext> sortedSensitives = permissionToSensitiveDefMap.get(permSet).stream()
                    .flatMap(sensDef -> resolvedSensitiveDefs.get(sensDef).stream())
                    //collection into TreeMap with keys produced by toString() ensures sorting by toString()
                    .collect(Collectors
                            .toMap(Object::toString, Function.identity(), StreamUtil.throwingMerger(), TreeMap::new))
                    .values();
            for (MethodOrMethodContext sensitive : sortedSensitives) {
                System.out.println("\nCallbacks for: " + sensitive);

                //true for covered callbacks, false for not covered
                Map<PermCheckStatus, List<MethodOrMethodContext>> permCheckStatusToCallbacks =
                        sensitivePathsHolder.getReachableCallbacks(sensitive).stream()
                                .sorted(SortUtil.getSootMethodPrettyPrintComparator())
                                .collect(Collectors.groupingBy(
                                        callback -> getPermCheckStatusForAny(permSet, callback)));

                for (PermCheckStatus status : PermCheckStatus.values()) {
                    if (permCheckStatusToCallbacks.get(status) != null) {
                        System.out.println("Perm check " + status + ":");
                        for (MethodOrMethodContext callback : permCheckStatusToCallbacks.get(status)) {
                            System.out.println("    " + callback);
                            //DebugUtil.printCallClassesAndLineNumbers(sensitive, callback, sensitivePathsHolder);
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
            } else if (!callbackToCheckedPermsMap.get(callback).isEmpty()) {
                return PermCheckStatus.UNRELATED;
            }
        }
        return PermCheckStatus.NOT_DETECTED;
    }

    /**
     * @return A value indicating whether the permissions in this set are checked by permission checks in the given
     * callback. Checks for multiple permissions are linked by AND relationship.
     */
    public PermCheckStatus getPermCheckStatusForAll(Set<String> permSet, MethodOrMethodContext callback) {
        if (callbackToCheckedPermsMap.get(callback) != null) {
            if (callbackToCheckedPermsMap.get(callback).containsAll(permSet)) {
                return PermCheckStatus.DETECTED;
            } else if (!callbackToCheckedPermsMap.get(callback).isEmpty()) {
                return PermCheckStatus.UNRELATED;
            }
        }
        return PermCheckStatus.NOT_DETECTED;
    }

    private static void printReachableSensitivesInCallbackStmts(JaxbCallbackList data, PrintStream out) {
        out.println("\nRequired permissions for code inside each callback:");
        out.println("========================================================================");

        for (JaxbCallback callback : data.getCallbacks()) {
            out.println("\n" + callback + " :");
            for (JaxbStmt jaxbStmt : callback.getStmts()) {
                String checkMsg = jaxbStmt.isGuarded() ? "YES" : "NO";
                out.println("    " + jaxbStmt.getLine() + ": "
                        + jaxbStmt.getCallFullSignature() + " : " + jaxbStmt.getPermissions() + ", guarded: " +
                        checkMsg);
            }
        }
        out.println();
    }

    public Set<String> getPermissionsFor(Collection<MethodOrMethodContext> sensitives) {
        return sensitives.stream().map(sens -> sensitiveToSensitiveDefMap.get(sens)).map(AndroidMethod::getPermissions)
                .collect(MyCollectors.toFlatSet());
    }

    public void printCheckers() {
        System.out.println("\n\nCheckers in the app: \n====================================");
        permCheckers.forEach(System.out::println);
    }

    public void printSensitives() {
        System.out.println("\n\nSensitives in the app: \n====================================");
        sensitives.forEach(System.out::println);
    }

    public CallPathHolder getSensitivePathsHolder() {
        return sensitivePathsHolder;
    }

    public enum PermCheckStatus {
        DETECTED("Permission check detected"),
        UNRELATED("Unrelated permission check detected"),
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

    private Map<MethodOrMethodContext, Set<String>> buildUnusedChecks() {
        Map<MethodOrMethodContext, Set<String>> callbackToRequiredPermsMap = buildCallbackToRequiredPermsMap();

        //need some data structures about used permissions first

        return callbackToCheckedPermsMap.keySet().stream().collect(Collectors.toMap(
                callback -> callback,
                callback -> {
                    //TreeSet ensures sorting by name
                    Set<String> perms = new TreeSet<>(callbackToCheckedPermsMap.get(callback));
                    Set<String> consumedPerms = callbackToRequiredPermsMap.get(callback);
                    consumedPerms = consumedPerms != null ? consumedPerms : Collections.emptySet();
                    perms.removeAll(consumedPerms);
                    return perms;
                }
        ));
    }

    private Map<MethodOrMethodContext, Set<String>> buildCallbackToRequiredPermsMap() {
        return sensitivePathsHolder.getReachableCallbacks().stream().collect(Collectors.toMap(
                callback -> callback,
                callback -> sensitivePathsHolder.getCallsToSensitiveFor(callback).stream()
                        .map(sensitiveCall -> sensitiveToSensitiveDefMap.get(sensitiveCall.getTgt()).getPermissions())
                        .collect(MyCollectors.toFlatSet())
        ));
    }

    private void printUnusedChecks() {
        System.out.println("\nChecked permissions inside each callback:");
        System.out.println("========================================================================");

        for (MethodOrMethodContext callback : checkerPathsHolder.getReachableCallbacks()) {
            //now callbacks are nicely sorted
            System.out.println("\n" + callback + " :");
            for (String perm : unusedChecks.get(callback)) {
                String status = "NO";
                System.out.println("    " + perm + " used: " + status);
            }
        }
        System.out.println();
    }

}
