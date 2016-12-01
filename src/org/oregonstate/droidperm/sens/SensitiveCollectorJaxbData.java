package org.oregonstate.droidperm.sens;

import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 11/24/2016.
 */
@XmlRootElement(name = "appPermissions")
public class SensitiveCollectorJaxbData {

    @XmlElementWrapper
    @XmlElement(name = "declaredPerm")
    private List<String> declaredPerms;

    @XmlElementWrapper
    @XmlElement(name = "referredPerm")
    private List<String> referredPerms;

    @XmlElementWrapper
    @XmlElement(name = "permWithSensitives")
    private List<String> permsWithSensitives;

    @XmlElementWrapper
    @XmlElement(name = "referredPermDef")
    private List<PermissionDef> referredPermDefs;

    public SensitiveCollectorJaxbData() {
    }

    public SensitiveCollectorJaxbData(List<String> declaredPerms, List<String> referredPerms,
                                      List<String> permsWithSensitives, List<PermissionDef> referredPermDefs) {
        this.declaredPerms = declaredPerms;
        this.referredPerms = referredPerms;
        this.permsWithSensitives = permsWithSensitives;
        this.referredPermDefs = referredPermDefs;
    }

    public List<String> getDeclaredPerms() {
        return declaredPerms;
    }

    public List<String> getReferredPerms() {
        return referredPerms;
    }

    public List<String> getPermsWithSensitives() {
        return permsWithSensitives;
    }

    public List<PermissionDef> getReferredPermDefs() {
        return referredPermDefs;
    }
}
