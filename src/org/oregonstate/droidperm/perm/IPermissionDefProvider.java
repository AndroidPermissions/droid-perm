package org.oregonstate.droidperm.perm;

import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 6/15/2016.
 */
public interface IPermissionDefProvider {

    Set<SootMethodAndClass> getPermCheckerDefs();

    Set<SootMethodAndClass> getPermRequesterDefs();

    Set<AndroidMethod> getMethodSensitiveDefs();

    Set<FieldSensitiveDef> getFieldSensitiveDefs();

    Set<SootMethodAndClass> getParametricSensDefs();
}
