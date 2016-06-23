package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/20/2016.
 */
@XmlRootElement(name = "PermissionDefinitions")
public class PermissionDefList {
    private List<PermissionDef> PermissionDef = new ArrayList<>();

    @XmlElement
    public List<PermissionDef> getPermissionDef () { return PermissionDef; }

    public void setPermissionDef(List<org.oregonstate.droidperm.miningPermDef.PermissionDef> permissionDef) {
        PermissionDef = permissionDef;
    }

    public void addPermissionDef (PermissionDef permissionDef) { this.PermissionDef.add(permissionDef); }
}
