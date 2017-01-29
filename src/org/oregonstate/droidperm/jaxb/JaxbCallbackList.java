package org.oregonstate.droidperm.jaxb;

import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/26/2016.
 */
@XmlRootElement(name = "callbacks")
@XmlAccessorType(XmlAccessType.FIELD)
public class JaxbCallbackList {

    @XmlElement(name = "callback")
    private List<JaxbCallback> callbacks = new ArrayList<>();

    @XmlElementWrapper
    @XmlElement(name = "permDef")
    private List<PermissionDef> undetectedCHADangerousPermDefs;

    @XmlElementWrapper
    @XmlElement(name = "permDef")
    private List<PermissionDef> unreachableDangerousPermDefs;

    @XmlElement
    private boolean compileApi23Plus = true;

    @XmlElement
    private int targetSdkVersion;

    @XmlElement
    private int nrReachedSensEdges;

    @XmlElement
    private int nrCHAReachableSensEdges;

    @XmlElement
    private int nrUnreachableSensEdges;

    public JaxbCallbackList() {
    }

    public List<JaxbCallback> getCallbacks() {
        return callbacks;
    }

    public void setCallbacks(List<JaxbCallback> callbacks) {
        this.callbacks = callbacks;
    }

    public void addCallback(JaxbCallback callback) {
        callbacks.add(callback);
    }

    public List<PermissionDef> getUndetectedCHADangerousPermDefs() {
        return undetectedCHADangerousPermDefs;
    }

    public void setUndetectedCHADangerousPermDefs(
            List<PermissionDef> undetectedCHADangerousPermDefs) {
        this.undetectedCHADangerousPermDefs = undetectedCHADangerousPermDefs;
    }

    public List<PermissionDef> getUnreachableDangerousPermDefs() {
        return unreachableDangerousPermDefs;
    }

    public void setUnreachableDangerousPermDefs(List<PermissionDef> unreachableDangerousPermDefs) {
        this.unreachableDangerousPermDefs = unreachableDangerousPermDefs;
    }

    public boolean isCompileApi23Plus() {
        return compileApi23Plus;
    }

    public void setCompileApi23Plus(boolean compileApi23Plus) {
        this.compileApi23Plus = compileApi23Plus;
    }

    public int getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    public int getNrReachedSensEdges() {
        return nrReachedSensEdges;
    }

    public void setNrReachedSensEdges(int nrReachedSensEdges) {
        this.nrReachedSensEdges = nrReachedSensEdges;
    }

    public int getNrCHAReachableSensEdges() {
        return nrCHAReachableSensEdges;
    }

    public void setNrCHAReachableSensEdges(int nrCHAReachableSensEdges) {
        this.nrCHAReachableSensEdges = nrCHAReachableSensEdges;
    }

    public int getNrUnreachableSensEdges() {
        return nrUnreachableSensEdges;
    }

    public void setNrUnreachableSensEdges(int nrUnreachableSensEdges) {
        this.nrUnreachableSensEdges = nrUnreachableSensEdges;
    }
}
