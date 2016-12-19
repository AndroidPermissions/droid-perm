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
    @XmlElement(name = "referredPermDef")
    private List<PermissionDef> undetectedPermDefs;

    public JaxbCallbackList() {
    }

    public JaxbCallbackList(List<JaxbCallback> callbacks,
                            List<PermissionDef> undetectedPermDefs) {
        this.callbacks = callbacks;
        this.undetectedPermDefs = undetectedPermDefs;
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

    public List<PermissionDef> getUndetectedPermDefs() {
        return undetectedPermDefs;
    }

    public void setUndetectedPermDefs(List<PermissionDef> undetectedPermDefs) {
        this.undetectedPermDefs = undetectedPermDefs;
    }
}
