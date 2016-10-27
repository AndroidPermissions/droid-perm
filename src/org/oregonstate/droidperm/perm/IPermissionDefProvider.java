package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.FieldSensitiveDef;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 6/15/2016.
 */
public interface IPermissionDefProvider {

    Set<SootMethodAndClass> getPermCheckerDefs();

    Set<AndroidMethod> getMethodSensitiveDefs();

    Set<FieldSensitiveDef> getFieldSensitiveDefs();
}
