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
    private List<PermissionDef> permissionDefs = new ArrayList<>();

    @XmlElement(name = "permissionDef")
    public List<PermissionDef> getPermissionDefs() {
        return permissionDefs;
    }

    public void setPermissionDefs(List<org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef> permissionDefs) {
        this.permissionDefs = permissionDefs;
    }

    public void addPermissionDef(PermissionDef permissionDef) {
        this.permissionDefs.add(permissionDef);
    }
}
