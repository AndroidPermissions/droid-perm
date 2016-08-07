package org.oregonstate.droidperm.infoflow.android;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
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
        return isClassInAndroidPackage(typeName) || super.isAndroidCallback(typeName);
    }

    protected void analyzeClassInterfaceCallbacks(SootClass baseClass, SootClass sootClass,
                                                  SootClass lifecycleElement) {
        // We cannot create instances of abstract classes anyway, so there is no
        // reason to look for interface implementations
        if (!baseClass.isConcrete())
            return;

        // For a first take, we consider all classes in the android.* packages
        // to be part of the operating system
        if (baseClass.getName().startsWith("android."))
            return;

        // If we are a class, one of our superclasses might implement an Android
        // interface
        if (sootClass.hasSuperclass())
            analyzeClassInterfaceCallbacks(baseClass, sootClass.getSuperclass(), lifecycleElement);

        // Do we implement one of the well-known interfaces?
        for (SootClass i : collectAllInterfaces(sootClass)) {
            if (isAndroidCallback(i.getName()))
                for (SootMethod sm : i.getMethods())
                    try {
                        checkAndAddMethod(getMethodFromHierarchyEx(baseClass, sm.getSubSignature()), lifecycleElement);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
        }
    }
}
