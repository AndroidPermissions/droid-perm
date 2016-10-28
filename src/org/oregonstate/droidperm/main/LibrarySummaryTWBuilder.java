package org.oregonstate.droidperm.main;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.util.SystemClassHandler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 6/22/2016.
 */
public class LibrarySummaryTWBuilder {
    /**
     * Creates the taint wrapper for using library summaries
     *
     * @return The taint wrapper for using library summaries
     * @throws IOException Thrown if one of the required files could not be read
     * @param summaryPath
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static ITaintPropagationWrapper createLibrarySummaryTW(String summaryPath)
            throws IOException {
        try {
            Class clzLazySummary = Class.forName("soot.jimple.infoflow.methodSummary.data.summary.LazySummary");

            Object lazySummary = clzLazySummary.getConstructor(File.class).newInstance(new File(summaryPath));

            ITaintPropagationWrapper summaryWrapper = (ITaintPropagationWrapper) Class.forName
                    ("soot.jimple.infoflow.methodSummary.taintWrappers.SummaryTaintWrapper").getConstructor
                    (clzLazySummary).newInstance(lazySummary);

            ITaintPropagationWrapper systemClassWrapper = new ITaintPropagationWrapper() {

                private ITaintPropagationWrapper wrapper = new EasyTaintWrapper("EasyTaintWrapperSource.txt");

                private boolean isSystemClass(Stmt stmt) {
                    return stmt.containsInvokeExpr() && SystemClassHandler
                            .isClassInSystemPackage(stmt.getInvokeExpr().getMethod().getDeclaringClass().getName());
                }

                @Override
                public boolean supportsCallee(Stmt callSite) {
                    return isSystemClass(callSite) && wrapper.supportsCallee(callSite);
                }

                @Override
                public boolean supportsCallee(SootMethod method) {
                    return SystemClassHandler.isClassInSystemPackage(method.getDeclaringClass().getName())
                            && wrapper.supportsCallee(method);
                }

                @Override
                public boolean isExclusive(Stmt stmt, Abstraction taintedPath) {
                    return isSystemClass(stmt) && wrapper.isExclusive(stmt, taintedPath);
                }

                @Override
                public void initialize(InfoflowManager manager) {
                    wrapper.initialize(manager);
                }

                @Override
                public int getWrapperMisses() {
                    return 0;
                }

                @Override
                public int getWrapperHits() {
                    return 0;
                }

                @Override
                public Set<Abstraction> getTaintsForMethod(Stmt stmt, Abstraction d1,
                                                           Abstraction taintedPath) {
                    if (!isSystemClass(stmt)) {
                        return null;
                    }
                    return wrapper.getTaintsForMethod(stmt, d1, taintedPath);
                }

                @Override
                public Set<Abstraction> getAliasesForMethod(Stmt stmt, Abstraction d1,
                                                            Abstraction taintedPath) {
                    if (!isSystemClass(stmt)) {
                        return null;
                    }
                    return wrapper.getAliasesForMethod(stmt, d1, taintedPath);
                }

            };

            Method setFallbackMethod = summaryWrapper.getClass().getMethod("setFallbackTaintWrapper",
                    ITaintPropagationWrapper.class);
            setFallbackMethod.invoke(summaryWrapper, systemClassWrapper);

            return summaryWrapper;
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            System.err.println("Could not find library summary classes: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        } catch (InvocationTargetException ex) {
            System.err.println("Could not initialize library summaries: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        } catch (IllegalAccessException | InstantiationException ex) {
            System.err.println("Internal error in library summary initialization: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }
}
