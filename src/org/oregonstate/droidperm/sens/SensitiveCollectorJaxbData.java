package org.oregonstate.droidperm.sens;

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

    public SensitiveCollectorJaxbData() {
    }

    public SensitiveCollectorJaxbData(List<String> declaredPerms, List<String> referredPerms,
                                      List<String> permsWithSensitives) {
        this.declaredPerms = declaredPerms;
        this.referredPerms = referredPerms;
        this.permsWithSensitives = permsWithSensitives;
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
}
