package org.oregonstate.droidperm.perm.miner;

import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/27/2016.
 */
public class FieldSensitiveDef {

    private final String className;
    private final String type;
    private final String name;
    private final Set<String> permissions;

    public FieldSensitiveDef(String className, String type, String name, Set<String> permissions) {
        this.className = className;
        this.type = type;
        this.name = name;
        this.permissions = permissions;
    }

    public String getClassName() {
        return className;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public String getSignature() {
        String sb = "<" + className + ": " + type + " " + name + ">";
        return sb.intern();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FieldSensitiveDef that = (FieldSensitiveDef) o;
        return className.equals(that.className) && type.equals(that.type) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = className.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "FieldSensitiveDef{" +
                "className='" + className + '\'' +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
