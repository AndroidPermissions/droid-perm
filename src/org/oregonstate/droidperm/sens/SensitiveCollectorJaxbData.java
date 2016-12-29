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
    private List<String> allDeclaredPerms;

    @XmlElementWrapper
    @XmlElement(name = "declaredPerm")
    private List<String> declaredDangerousPerms;

    @XmlElementWrapper
    @XmlElement(name = "referredPerm")
    private List<String> referredDangerousPerms;

    @XmlElementWrapper
    @XmlElement(name = "permWithSensitives")
    private List<String> permsWithSensitives;

    @XmlElementWrapper
    @XmlElement(name = "referredPermDef")
    private List<PermissionDef> referredPermDefs;

    @XmlElement
    private int targetSdkVersion;

    public SensitiveCollectorJaxbData() {
    }

    public SensitiveCollectorJaxbData(List<String> allDeclaredPerms, List<String> declaredDangerousPerms,
                                      List<String> referredDangerousPerms, List<String> permsWithSensitives,
                                      List<PermissionDef> referredPermDefs, int targetSdkVersion) {
        this.allDeclaredPerms = allDeclaredPerms;
        this.declaredDangerousPerms = declaredDangerousPerms;
        this.referredDangerousPerms = referredDangerousPerms;
        this.permsWithSensitives = permsWithSensitives;
        this.referredPermDefs = referredPermDefs;
        this.targetSdkVersion = targetSdkVersion;
    }

    public List<String> getAllDeclaredPerms() {
        return allDeclaredPerms;
    }

    public List<String> getDeclaredDangerousPerms() {
        return declaredDangerousPerms;
    }

    public List<String> getReferredDangerousPerms() {
        return referredDangerousPerms;
    }

    public List<String> getPermsWithSensitives() {
        return permsWithSensitives;
    }

    public List<PermissionDef> getReferredPermDefs() {
        return referredPermDefs;
    }

    public int getTargetSdkVersion() {
        return targetSdkVersion;
    }
}
