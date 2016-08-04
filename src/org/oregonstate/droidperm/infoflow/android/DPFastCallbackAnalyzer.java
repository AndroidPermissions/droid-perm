package org.oregonstate.droidperm.infoflow.android;

import soot.Scene;
import soot.SootClass;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.callbacks.FastCallbackAnalyzer;

import java.io.IOException;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/1/2016.
 */
public class DPFastCallbackAnalyzer extends FastCallbackAnalyzer {
    public DPFastCallbackAnalyzer(InfoflowAndroidConfiguration config,
                                  Set<String> entryPointClasses) throws IOException {
        super(config, entryPointClasses);
    }

    public DPFastCallbackAnalyzer(InfoflowAndroidConfiguration config,
                                  Set<String> entryPointClasses, String callbackFile) throws IOException {
        super(config, entryPointClasses, callbackFile);
    }

    public DPFastCallbackAnalyzer(InfoflowAndroidConfiguration config,
                                  Set<String> entryPointClasses, Set<String> androidCallbacks)
            throws IOException {
        super(config, entryPointClasses, androidCallbacks);
    }

    @Override
    protected boolean isAndroidCallback(String typeName) {
        SootClass sootClass = Scene.v().containsClass(typeName) ? Scene.v().getSootClass(typeName) : null;
        return (sootClass != null && sootClass.isInterface() && (sootClass.getName().startsWith("android")))
                || super.isAndroidCallback(typeName);
    }
}
