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
    private List<PermissionDef> undetectedDangerousPermDefs;

    @XmlElement
    private boolean compileApi23Plus = true;

    @XmlElement
    private int targetSdkVersion;

    public JaxbCallbackList() {
    }

    public JaxbCallbackList(List<JaxbCallback> callbacks,
                            List<PermissionDef> undetectedDangerousPermDefs) {
        this.callbacks = callbacks;
        this.undetectedDangerousPermDefs = undetectedDangerousPermDefs;
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

    public List<PermissionDef> getUndetectedDangerousPermDefs() {
        return undetectedDangerousPermDefs;
    }

    public void setUndetectedDangerousPermDefs(List<PermissionDef> undetectedDangerousPermDefs) {
        this.undetectedDangerousPermDefs = undetectedDangerousPermDefs;
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
}
