package org.oregonstate.droidperm;

import com.google.common.base.Strings;
import org.oregonstate.droidperm.util.CallGraphUtil;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.results.xml.InfoflowResultsSerializer;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.toolkits.scalar.Pair;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 6/10/2016.
 */
final class FlowDroidResultsAvailableHandler implements ResultsAvailableHandler {

    String flowDroidXmlOut;

    public FlowDroidResultsAvailableHandler(String flowDroidXmlOut) {
        this.flowDroidXmlOut = flowDroidXmlOut;
    }

    @Override
    public void onResultsAvailable(IInfoflowCFG cfg, InfoflowResults results) {
        printResults(cfg, results);
        if (results != null && !Strings.isNullOrEmpty(flowDroidXmlOut)) {
            printResultsToXml(cfg, results);
        }
    }

    /**
     * Includes tree-like printing of dataflow paths for each flow.
     */
    private void printResults(IInfoflowCFG cfg, InfoflowResults results) {
        Map<Integer, String> indentCache = new HashMap<>();

        System.out.println("\n\nFlowDroid: detected flows \n" +
                "========================================================================\n");
        if (results == null) {
            System.out.println("No flows detected.");
            return;
        }
        for (ResultSinkInfo sink : results.getResults().keySet()) {
            System.out.println("\nFlow to sink " + sink + ", from sources:\n" +
                    "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
            for (ResultSourceInfo source : results.getResults().get(sink)) {
                System.out.println(source.getSource() + "\n\tin "
                        + cfg.getMethodOf(source.getSource()).getSignature());
                if (source.getPath() != null) {
                    Map<Stmt, Pair<MethodOrMethodContext, Integer>> containerMethods =
                            CallGraphUtil.resolveContainersForDataflow(source.getPath());
                    Stmt[] path = source.getPath();

                    System.out.println("\nPath through statements:");
                    System.out.println("____________________________________________________________++++");
                    for (int i = 0; i < path.length; i++) {
                        Stmt stmt = path[i];
                        Pair<MethodOrMethodContext, Integer> methAndInd = containerMethods.get(stmt);
                        if (i == 0 ||
                                getTopLevelClass(containerMethods.get(path[i - 1])) != getTopLevelClass(methAndInd)) {
                            SootClass declaringClass =
                                    methAndInd.getO1() != null ? methAndInd.getO1().method().getDeclaringClass() : null;
                            System.out.println("\t\t\t\t\t\t\t\t\t\t\t"
                                    + "[" + declaringClass + "]");
                        }
                        String indent = getIndent(methAndInd.getO2(), indentCache);
                        System.out.println(indent + stmt + " : " + stmt.getJavaSourceStartLineNumber());
                    }
                    System.out.println("________________________________________________________________");

                    System.out.println("\nPath through methods and statements:");
                    System.out.println("____________________________________________________________()()");

                    Set<MethodOrMethodContext> coveredMethods = new HashSet<>();
                    for (Stmt stmt : path) {
                        Pair<MethodOrMethodContext, Integer> methAndInd = containerMethods.get(stmt);
                        MethodOrMethodContext method = methAndInd.getO1();
                        String indent = getIndent(methAndInd.getO2(), indentCache);
                        //noinspection NumberEquality
                        if (method == null || !coveredMethods.contains(method)) {
                            if (method != null) {
                                coveredMethods.add(method);
                            }
                            String methShortString = method != null ? getShortString(method.method()) : "<null>()";
                            System.out.println("\t\t\t\t\t\t\t\t\t\t\t" + indent + methShortString);
                        }
                        System.out.println(indent + stmt + " : " + stmt.getJavaSourceStartLineNumber());
                    }
                    System.out.println("________________________________________________________________");
                }
            }
            System.out.println();
        }
        System.out.println("\n");
    }

    private String getIndent(Integer level, Map<Integer, String> indentCache) {
        if (!indentCache.containsKey(level)) {
            indentCache.putIfAbsent(0, "");
            if (level > 0) {
                indentCache.putIfAbsent(level, getIndent(level - 1, indentCache) + "\t");
            }
        }
        return indentCache.get(level);
    }

    private SootClass getTopLevelClass(Pair<MethodOrMethodContext, ?> pair) {
        SootClass clazz = pair.getO1() != null ? pair.getO1().method().getDeclaringClass() : null;
        while (clazz != null && clazz.hasOuterClass()) {
            clazz = clazz.getOuterClass();
        }
        return clazz;
    }

    public static String getShortString(SootMethod meth) {
        return "<" +
                Scene.v().quotedNameOf(meth.getDeclaringClass().getName()) +
                ": " +
                meth.getName() +
                "(...)>";
    }

    private void printResultsToXml(IInfoflowCFG cfg, InfoflowResults results) {
        InfoflowResultsSerializer serializer = new InfoflowResultsSerializer(cfg);
        try {
            serializer.serialize(results, flowDroidXmlOut);
        } catch (FileNotFoundException | XMLStreamException ex) {
            System.err.println("Could not write data flow results to file: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }
}
