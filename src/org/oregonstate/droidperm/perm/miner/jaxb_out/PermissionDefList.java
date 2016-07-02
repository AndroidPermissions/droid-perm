package org.oregonstate.droidperm.perm.miner.jaxb_out;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/20/2016.
 */
@XmlRootElement(name = "PermissionDefinitions")
public class PermissionDefList {
    private List<PermissionDef> PermissionDefs = new ArrayList<>();

    @XmlElement(name = "permissionDef")
    public List<PermissionDef> getPermissionDefs() {
        return PermissionDefs;
    }

    public void setPermissionDefs(List<org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef> permissionDefs) {
        PermissionDefs = permissionDefs;
    }

    public void addPermissionDef(PermissionDef permissionDef) {
        this.PermissionDefs.add(permissionDef);
    }
}
