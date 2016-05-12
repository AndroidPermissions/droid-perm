package org.oregonstate.droidperm.jaxb;

import soot.SootMethod;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/26/2016.
 */
@XmlRootElement(name = "callback")
public class JaxbCallback {

    private String declaringClass;
    private String signature;
    private List<JaxbStmt> stmts = new ArrayList<>();

    @XmlElement
    Map<String, CheckerUsageStatus> checkerStatusMap = new HashMap<>();

    public JaxbCallback() {
    }

    public JaxbCallback(SootMethod sootMethod) {
        declaringClass = sootMethod.getDeclaringClass().getName();
        signature = sootMethod.getSubSignature();
    }

    @XmlAttribute
    public String getDeclaringClass() {
        return declaringClass;
    }

    public void setDeclaringClass(String declaringClass) {
        this.declaringClass = declaringClass;
    }

    @XmlAttribute
    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @XmlElement(name = "statement")
    public List<JaxbStmt> getStmts() {
        return stmts;
    }

    public void setStmts(List<JaxbStmt> stmts) {
        this.stmts = stmts;
    }

    public void addStmt(JaxbStmt stmt) {
        stmts.add(stmt);
    }

    public void setCheckerStatusMap(Map<String, CheckerUsageStatus> checkerStatusMap) {
        this.checkerStatusMap = checkerStatusMap;
    }

    @Override
    public String toString() {
        return ("<" +
                declaringClass +
                ": " +
                signature +
                ">")
                .intern();
    }
}
