package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/21/2016.
 */
@XmlRootElement(name = "target")
public class Target {
    private String className;
    private TargetType targetType;
    private String methodFieldName;
    private String permissionRelationship;
    private List<String> permissions = new ArrayList<>();

    public Target(){
    }

    @XmlAttribute
    public String getClassName() { return className; }

    public void setClassName(String className) {
        this.className = className;
    }

    @XmlAttribute
    public TargetType getTargetType() { return targetType; }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType;
    }

    @XmlAttribute
    public String getMethodFieldName() { return methodFieldName; }

    public void setMethodFieldName(String methodFieldName) {
        this.methodFieldName = methodFieldName;
    }

    @XmlAttribute
    public String getPermissionRelationship() { return permissionRelationship; }

    public void setPermissionRelationship(String permissionRelationship) {
        this.permissionRelationship = permissionRelationship;
    }

    @XmlElement
    public List<String> getPermissions() { return permissions; }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public void addPermission(String permission) {
        this.permissions.add(permission);
    }
}
