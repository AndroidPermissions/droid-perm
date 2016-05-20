package org.oregonstate.droidperm.jaxb;

import soot.SootMethod;
import soot.jimple.Stmt;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/26/2016.
 */
@XmlRootElement
public class JaxbStmt {

    private String callClass;
    private String callSignature;
    private int line;
    private Map<String, Boolean> permissionStatusMap;

    public JaxbStmt() {
    }

    public JaxbStmt(Stmt stmt, Map<String, Boolean> permissionStatusMap) {
        SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
        callClass = sootMethod.getDeclaringClass().getName();
        callSignature = sootMethod.getSubSignature();
        line = stmt.getJavaSourceStartLineNumber();
        this.permissionStatusMap = permissionStatusMap;
    }

    @XmlAttribute
    public String getCallClass() {
        return callClass;
    }

    public void setCallClass(String callClass) {
        this.callClass = callClass;
    }

    @XmlAttribute
    public String getCallSignature() {
        return callSignature;
    }

    public String getCallFullSignature() {
        return ("<" +
                callClass +
                ": " +
                callSignature +
                ">")
                .intern();
    }

    public void setCallSignature(String callSignature) {
        this.callSignature = callSignature;
    }

    @XmlAttribute
    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public boolean allGuarded() {
        return permissionStatusMap.values().stream().allMatch(guarded -> guarded);
    }

    /**
     * Map from permission to guarded status.
     */
    @XmlElement(name = "permission")
    public Map<String, Boolean> getPermissionStatusMap() {
        return permissionStatusMap;
    }

    public List<String> getPermDisplayStrings() {
        String prefix = "android.permission.";
        int prefixLen = prefix.length();
        return permissionStatusMap.keySet().stream().map(perm ->
                (perm.startsWith(prefix) ? perm.substring(prefixLen) : prefix)
                        + (permissionStatusMap.get(perm) ? "" : " (no check)"))
                .collect(Collectors.toList());
    }

    public void setPermissionStatusMap(Map<String, Boolean> permissionStatusMap) {
        this.permissionStatusMap = permissionStatusMap;
    }
}
