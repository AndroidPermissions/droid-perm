package org.oregonstate.droidperm.perm.miner.jaxb_out;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/20/2016.
 */
@XmlRootElement
public class PermissionDef {
    private String className;
    private String target;
    private PermTargetKind targetKind;
    private PermissionRel permissionRel;
    private List<Permission> permissions = new ArrayList<>();

    public PermissionDef() {
    }

    @XmlAttribute
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @XmlAttribute
    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @XmlAttribute
    public PermTargetKind getTargetKind() {
        return targetKind;
    }

    public void setTargetKind(PermTargetKind targetKind) {
        this.targetKind = targetKind;
    }

    @XmlAttribute
    public PermissionRel getPermissionRel() {
        return permissionRel;
    }

    public void setPermissionRel(PermissionRel permissionRel) {
        this.permissionRel = permissionRel;
    }

    @XmlElement(name = "permission")
    public List<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions;
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public Set<String> getPermissionNames() {
        return getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PermissionDef that = (PermissionDef) o;

        if (className != null ? !className.equals(that.className) : that.className != null) {
            return false;
        }
        return target != null ? target.equals(that.target) : that.target == null;
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (target != null ? target.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PermissionDef{" +
                "className='" + className + '\'' +
                ", target='" + target + '\'' +
                ", targetKind=" + targetKind +
                ", permissionRel=" + permissionRel +
                ", permissions=" + permissions +
                '}';
    }
}
