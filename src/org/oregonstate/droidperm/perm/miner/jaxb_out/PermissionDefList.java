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
    private List<CheckerDef> checkerDefs = new ArrayList<>();
    @XmlElementWrapper
    @XmlElement(name = "parametricSensDef")
    private List<ParametricSensDef> parametricSensDefs = new ArrayList<>();

    public PermissionDefList() {
    }

    public PermissionDefList(List<PermissionDef> permissionDefs) {
        this.permissionDefs = permissionDefs;
    }

    public List<PermissionDef> getPermissionDefs() {
        return permissionDefs;
    }

    public List<CheckerDef> getCheckerDefs() {
        return checkerDefs;
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
