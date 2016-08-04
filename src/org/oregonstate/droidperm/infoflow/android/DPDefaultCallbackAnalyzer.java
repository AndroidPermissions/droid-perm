package org.oregonstate.droidperm.infoflow.android;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.callbacks.DefaultCallbackAnalyzer;

import java.io.IOException;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/1/2016.
 */
public class DPDefaultCallbackAnalyzer extends DefaultCallbackAnalyzer {

    public DPDefaultCallbackAnalyzer(InfoflowAndroidConfiguration config,
                                     Set<String> entryPointClasses) throws IOException {
        super(config, entryPointClasses);
    }

    public DPDefaultCallbackAnalyzer(InfoflowAndroidConfiguration config,
                                     Set<String> entryPointClasses, String callbackFile) throws IOException {
        super(config, entryPointClasses, callbackFile);
    }

    public DPDefaultCallbackAnalyzer(InfoflowAndroidConfiguration config,
                                     Set<String> entryPointClasses,
                                     Set<String> androidCallbacks) throws IOException {
        super(config, entryPointClasses, androidCallbacks);
    }

    /**
     * This implementation still does not detect Fragment classes when they are returned by callback methods.
     */
    @Override
    protected void analyzeClassInterfaceCallbacks(SootClass baseClass, SootClass sootClass,
                                                  SootClass lifecycleElement) {
        // We cannot create instances of abstract classes anyway, so there is no
        // reason to look for interface implementations
        if (!baseClass.isConcrete()) {
            return;
        }

        // For a first take, we consider all classes in the android.* packages
        // to be part of the operating system
        if (baseClass.getName().startsWith("android.")) {
            return;
        }

        // If we are a class, one of our superclasses might implement an Android
        // interface
        if (sootClass.hasSuperclass()) {
            analyzeClassInterfaceCallbacks(baseClass, sootClass.getSuperclass(), lifecycleElement);
        }

        // Do we implement one of the callback classes or interfaces?
        // Callback classes are required to support fragments.
        Set<SootClass> baseClassesAndInterfaces = collectAllInterfaces(sootClass);
        baseClassesAndInterfaces.addAll(Scene.v().getActiveHierarchy().getSuperclassesOf(sootClass));
        for (SootClass i : baseClassesAndInterfaces) {
            if (isAndroidCallback(i.getName())) {
                for (SootMethod sm : i.getMethods()) {
                    checkAndAddMethod(getMethodFromHierarchyEx(baseClass,
                            sm.getSubSignature()), lifecycleElement);
                }
            }
        }
    }

    @Override
    protected boolean isAndroidCallback(String typeName) {
        SootClass sootClass = Scene.v().containsClass(typeName) ? Scene.v().getSootClass(typeName) : null;
        return (sootClass != null && sootClass.isInterface() && (sootClass.getName().startsWith("android")))
                || super.isAndroidCallback(typeName);
    }
}
