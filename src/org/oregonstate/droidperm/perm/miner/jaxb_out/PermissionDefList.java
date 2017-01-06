package org.oregonstate.droidperm.perm.miner.jaxb_out;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/20/2016.
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "PermissionDefinitions")
@XmlAccessorType(XmlAccessType.FIELD)
public class PermissionDefList {
    @XmlElement(name = "permissionDef")
    private List<PermissionDef> permissionDefs = new ArrayList<>();
    @XmlElementWrapper
    @XmlElement(name = "checkerDef")
    private List<MethodBasedDef> checkerDefs = new ArrayList<>();
    @XmlElementWrapper
    @XmlElement(name = "requesterDef")
    private List<MethodBasedDef> requesterDefs = new ArrayList<>();
    @XmlElementWrapper
    @XmlElement(name = "parametricSensDef")
    private List<ParametricSensDef> parametricSensDefs = new ArrayList<>();

    public PermissionDefList() {
    }

    public PermissionDefList(List<PermissionDef> permissionDefs) {
        this.permissionDefs = permissionDefs;
    }

    public PermissionDefList(List<PermissionDef> permissionDefs, List<MethodBasedDef> checkerDefs,
                             List<ParametricSensDef> parametricSensDefs) {
        this.permissionDefs = permissionDefs;
        this.checkerDefs = checkerDefs;
        this.parametricSensDefs = parametricSensDefs;
    }

    public List<PermissionDef> getPermissionDefs() {
        return permissionDefs;
    }

    public List<MethodBasedDef> getCheckerDefs() {
        return checkerDefs;
    }

    public List<MethodBasedDef> getRequesterDefs() {
        return requesterDefs;
    }

    public List<ParametricSensDef> getParametricSensDefs() {
        return parametricSensDefs;
    }

    public void setPermissionDefs(List<org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef> permissionDefs) {
        this.permissionDefs = permissionDefs;
    }

    public void addPermissionDef(PermissionDef permissionDef) {
        this.permissionDefs.add(permissionDef);
    }
}
