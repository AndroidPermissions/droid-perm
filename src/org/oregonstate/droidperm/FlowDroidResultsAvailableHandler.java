package org.oregonstate.droidperm;

import com.google.common.base.Strings;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.UnitComparator;
import soot.*;
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
import java.util.*;
import java.util.stream.Collectors;

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
        Map<Unit, SootMethod> stmtToMethodMap = CallGraphUtil.getStmtToMethodMap();

        System.out.println("\n\nFlowDroid: detected flows \n" +
                "========================================================================\n");
        if (results == null) {
            System.out.println("No flows detected.");
            return;
        }
        List<ResultSinkInfo> sortedSinks = results.getResults().keySet().stream()
                .sorted(Comparator.comparing(ResultSinkInfo::getSink, new UnitComparator()))
                .collect(Collectors.toList());
        for (ResultSinkInfo sink : sortedSinks) {
            Stmt sinkStmt = sink.getSink();
            System.out.println("\n"
                    + "+----------------------------------------------------------------------\n"
                    + "| Flows to sink " + sinkStmt + " : " + sinkStmt.getJavaSourceStartLineNumber() + "\n"
                    + "| \tin " + stmtToMethodMap.get(sinkStmt)
                    + ", from sources:\n"
                    + "+----------------------------------------------------------------------");
            List<ResultSourceInfo> sortedSources = results.getResults().get(sink).stream()
                    .sorted(Comparator.comparing(ResultSourceInfo::getSource, new UnitComparator()))
                    .collect(Collectors.toList());
            for (ResultSourceInfo source : sortedSources) {
                Stmt sourceStmt = source.getSource();
                SootMethod sourceContainerMethod = cfg.getMethodOf(sourceStmt);
                System.out.println(""
                        + "| From source " + sourceStmt + " : " + sourceStmt.getJavaSourceStartLineNumber() + "\n"
                        + "| \tin " + sourceContainerMethod
                        + "\n+----------------------------------------------------------------------\n");
                if (source.getPath() != null) {
                    Map<Stmt, Pair<MethodOrMethodContext, Integer>> containerMethods =
                            CallGraphUtil.resolveContainersForDataflow(source.getPath());
                    Stmt[] path = source.getPath();
                    Set<MethodOrMethodContext> coveredMethods = new HashSet<>();
                    String lastIndent = null;
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
                            System.out.println("\n" + indent + "________________" + methShortString);
                        } else if (lastIndent != null && !lastIndent.equals(indent)) {
                            System.out.println(indent);
                        }
                        System.out.println(indent + stmt + " : " + stmt.getJavaSourceStartLineNumber());
                        lastIndent = indent;
                    }
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
