package org.oregonstate.droidperm.infoflow.android;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import heros.InterproceduralCFG;
import heros.solver.IDESolver;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.resources.LayoutControl;
import soot.jimple.infoflow.android.source.AccessPathBasedSourceSinkManager;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.source.data.SourceSinkDefinition;

import java.util.*;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/2/2016.
 */
public class DPAccessPathBasedSourceSinkManager extends AccessPathBasedSourceSinkManager {

    protected final LoadingCache<SootClass, Collection<SootClass>> superTypesOfIncluding =
            IDESolver.DEFAULT_CACHE_BUILDER.build(new CacheLoader<SootClass, Collection<SootClass>>() {

                @Override
                public Collection<SootClass> load(SootClass clazz) throws Exception {
                    Hierarchy hierarchy = Scene.v().getActiveHierarchy();
                    List<SootClass> superClasses = clazz.isInterface() ? Collections.singletonList(clazz)
                                                                       : hierarchy.getSuperclassesOfIncluding(clazz);
                    Set<SootClass> superTypes = new HashSet<>(superClasses);
                    superClasses.stream()
                            .flatMap(syperClass -> syperClass.getInterfaces().stream())
                            .forEach(interf -> superTypes.addAll(hierarchy.getSuperinterfacesOfIncluding(interf)));
                    return superTypes;
                }
            });

    public DPAccessPathBasedSourceSinkManager(Set<SourceSinkDefinition> sources,
                                              Set<SourceSinkDefinition> sinks,
                                              Set<SootMethodAndClass> callbackMethods,
                                              LayoutMatchingMode layoutMatching,
                                              Map<Integer, LayoutControl> layoutControls) {
        super(sources, sinks, callbackMethods, layoutMatching, layoutControls);
    }

    @Override
    protected SourceType getSourceType(Stmt sCallSite, InterproceduralCFG<Unit, SootMethod> cfg) {
        //Extending the algorithm from base class.
        //Source can be defined on some base class and called on a subclass now.

        // This might be a normal source method
        if (sCallSite.containsInvokeExpr()) {
            SootMethod method = sCallSite.getInvokeExpr().getMethod();
            SootClass callClass = method.getDeclaringClass();
            String subSig = method.getSubSignature();
            for (SootClass baseClass : superTypesOfIncluding.getUnchecked(callClass)) {
                if (baseClass.declaresMethod(subSig)
                        && sourceMethods.containsKey(methodToSignature.getUnchecked(baseClass.getMethod(subSig)))) {
                    return SourceType.MethodCall;
                }
            }
        }

        return SourceType.NoSource;
    }
}
