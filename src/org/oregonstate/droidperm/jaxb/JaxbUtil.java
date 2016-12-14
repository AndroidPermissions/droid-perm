package org.oregonstate.droidperm.jaxb;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.oregonstate.droidperm.traversal.MethodPermDetector;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.StreamUtil;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/26/2016.
 */
public class JaxbUtil {

    /**
     * todo: There's a discrepancy between the way permission checks are matched with sensitives here and in
     * printCoveredCallbacks(). There ANY ONE perm per sensitive should be checked for the whole sensitive to be
     * checked. Here ALL the perms for a statement should be checked for the statement to be checked.
     */
    public static JaxbCallbackList buildJaxbData(MethodPermDetector detector) {
        CallGraph cg = Scene.v().getCallGraph();
        //Map from callback to lvl 2 map describing checked permissions in this callback.
        //Lvl2 map: from checked permissions to usage status of this check: used, unused or possibly used through ICC.
        Table<MethodOrMethodContext, String, CheckerUsageStatus> callbackCheckerStatusMap =
                buildCheckerStatusMap(detector);

        JaxbCallbackList jaxbCallbackList = new JaxbCallbackList();
        for (MethodOrMethodContext callback : detector.getSensitivePathsHolder().getSortedReachableCallbacks()) {
            JaxbCallback jaxbCallback = new JaxbCallback(callback.method());
            jaxbCallback.setCheckerStatusMap(callbackCheckerStatusMap.row(callback));
            for (Unit unit : callback.method().getActiveBody().getUnits()) {
                Set<Edge> sensEdges = StreamUtil.asStream(cg.edgesOutOf(unit))
                        .map(edge -> detector.getSensitivePathsHolder().getReacheablePresensitives(edge))
                        .collect(MyCollectors.toFlatSet());
                if (!sensEdges.isEmpty()) {
                    Set<String> permSet = detector.getCgService().getPermissionsFor(sensEdges);
                    Map<String, Boolean> permGaurdedMap = permSet.stream()
                            .collect(Collectors.toMap(perm -> perm,
                                    perm -> detector
                                            .getPermCheckStatusForAll(Collections.singletonList(perm), callback) ==
                                            MethodPermDetector.PermCheckStatus.CHECK_DETECTED));

                    JaxbStmt jaxbStmt = new JaxbStmt((Stmt) unit, permGaurdedMap);
                    jaxbCallback.addStmt(jaxbStmt);
                }
            }
            jaxbCallbackList.addCallback(jaxbCallback);
        }
        return jaxbCallbackList;
    }

    private static Table<MethodOrMethodContext, String, CheckerUsageStatus> buildCheckerStatusMap(
            MethodPermDetector detector) {
        Table<MethodOrMethodContext, String, CheckerUsageStatus> table = HashBasedTable.create();
        detector.getCallbackToCheckedPermsMap().keySet().forEach(callback -> {
            Set<String> perms = detector.getCallbackToCheckedPermsMap().get(callback);
            perms.forEach(
                    perm -> table.put(callback, perm, detector.getCheckUsageStatus(callback, perm))
            );
        });
        return table;
    }

    public static void save(JaxbCallbackList data, File file) throws JAXBException {
        save(data, JaxbCallbackList.class, file);
    }

    public static <T> void save(T data, Class<T> dataClass, File file) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(dataClass);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        // output pretty printed
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        jaxbMarshaller.marshal(data, file);
    }

    public static Object load(Class<?> dataClass, File file) throws JAXBException {
        Unmarshaller unmarshaller = JAXBContext.newInstance(dataClass).createUnmarshaller();
        return unmarshaller.unmarshal(file);
    }

    public static <T> void print(T data, Class<T> dataClass) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(dataClass);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(data, System.out);
    }
}
