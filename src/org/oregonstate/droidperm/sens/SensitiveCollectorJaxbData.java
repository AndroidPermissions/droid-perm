package org.oregonstate.droidperm.sens;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 11/24/2016.
 */
@XmlRootElement(name = "appPermissions")
public class SensitiveCollectorJaxbData {

    @XmlElement
    private List<String> declaredPerm;

    @XmlElement
    private List<String> referredPerm;

    @XmlElement
    private List<String> permWithSensitives;

    public SensitiveCollectorJaxbData() {
    }

    public SensitiveCollectorJaxbData(List<String> declaredPerm, List<String> referredPerm,
                                      List<String> permWithSensitives) {
        this.declaredPerm = declaredPerm;
        this.referredPerm = referredPerm;
        this.permWithSensitives = permWithSensitives;
    }

    public List<String> getDeclaredPerm() {
        return declaredPerm;
    }

    public List<String> getReferredPerm() {
        return referredPerm;
    }

    public List<String> getPermWithSensitives() {
        return permWithSensitives;
    }
}
