package org.oregonstate.droidperm.perm;

import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.List;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/19/2016.
 */
public class PermissionDef {
    public enum Kind {
        METHOD, URI, STORAGE;
    }

    private SootMethodAndClass methodDef;
    private Kind kind;
    private List<String> permissions;

    public PermissionDef(SootMethodAndClass methodDef, Kind kind, List<String> permissions) {
        this.methodDef = methodDef;
        this.kind = kind;
        this.permissions = permissions;
    }

    public SootMethodAndClass getMethodDef() {
        return methodDef;
    }

    public Kind getKind() {
        return kind;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
