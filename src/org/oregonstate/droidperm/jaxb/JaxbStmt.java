package org.oregonstate.droidperm.jaxb;

import soot.SootMethod;
import soot.jimple.Stmt;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/26/2016.
 */
@XmlRootElement
public class JaxbStmt {

    private String callClass;
    private String callSignature;
    private int line;
    private boolean guarded;
    private Collection<String> permissions;

    public JaxbStmt() {
    }

    public JaxbStmt(Stmt stmt, boolean guarded, Collection<String> permissions) {
        SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
        callClass = sootMethod.getDeclaringClass().getName();
        callSignature = sootMethod.getSubSignature();
        line = stmt.getJavaSourceStartLineNumber();
        this.guarded = guarded;
        this.permissions = permissions;
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

    @XmlAttribute
    public boolean isGuarded() {
        return guarded;
    }

    public void setGuarded(boolean guarded) {
        this.guarded = guarded;
    }

    @XmlElement(name = "permission")
    public Collection<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Collection<String> permissions) {
        this.permissions = permissions;
    }
}
