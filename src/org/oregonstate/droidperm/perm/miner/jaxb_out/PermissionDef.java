package org.oregonstate.droidperm.perm.miner.jaxb_out;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/20/2016.
 */
@XmlRootElement
public class PermissionDef {
    private String className;
    private String targetName;
    private TargetType targetType;
    private PermissionRel permissionRel = PermissionRel.AllOf;
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
    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    @XmlAttribute
    public TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType;
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
        return targetName != null ? targetName.equals(that.targetName) : that.targetName == null;
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (targetName != null ? targetName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PermissionDef{" +
                "className='" + className + '\'' +
                ", targetName='" + targetName + '\'' +
                ", targetType=" + targetType +
                ", permissionRel=" + permissionRel +
                ", permissions=" + permissions +
                '}';
    }
}
