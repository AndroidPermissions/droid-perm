package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.jaxb_out.PermTargetKind;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import soot.jimple.infoflow.android.data.AndroidMethod;

import java.util.stream.Collectors;

/**
 * Converts permisison definitions from one format to another
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 11/30/2016.
 */
public class PermissionDefConverter {

    /**
     * Lost information: OpperationKind for each permission.
     */
    public static PermissionDef forMethod(AndroidMethod def) {
        return new PermissionDef(def.getClassName(), def.getSubSignature(), PermTargetKind.Method,
                def.getPermissions().stream().map(perm -> new Permission(perm, null))
                        .collect(Collectors.toList())
        );
    }

    /**
     * Lost information: OpperationKind for each permission.
     */
    public static PermissionDef forField(FieldSensitiveDef def) {
        return new PermissionDef(def.getClassName(), def.getName(), PermTargetKind.Field,
                def.getPermissions().stream().map(perm -> new Permission(perm, null))
                        .collect(Collectors.toList())
        );
    }
}
