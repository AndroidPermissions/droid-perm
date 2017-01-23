package org.oregonstate.droidperm.scene;

import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 1/22/2017.
 */
public class SceneAnalysisResult {
    /**
     * Map lvl 1: from permission sets to sensitive methods having this set.
     * <p>
     * Map lvl2: from sensitive to its calling context: method and stmt.
     */
    public Map<Set<String>, SetMultimap<SootMethod, Stmt>> permToReferredMethodSensMap;

    /**
     * Map lvl 1: from permission sets to sensitive fields having this set.
     * <p>
     * Map lvl2: from sensitive to its calling context: method and stmt.
     */
    public Map<Set<String>, SetMultimap<SootField, Stmt>> permToReferredFieldSensMap;

    /**
     * Multimap from checkers to calling contexts.
     */
    public Multimap<SootMethod, Stmt> checkers;

    public Multimap<SootMethod, Stmt> requesters;

    /**
     * @see #permToReferredMethodSensMap
     */
    public Map<Set<String>, SetMultimap<SootMethod, Stmt>> permToReferredMethodSensMapCHA;

    /**
     * @see #permToReferredFieldSensMap
     */
    public Map<Set<String>, SetMultimap<SootField, Stmt>> permToReferredFieldSensMapCHA;

    /**
     * @see #checkers
     */
    public Multimap<SootMethod, Stmt> checkersCHA;

    /**
     * @see #requesters
     */
    public Multimap<SootMethod, Stmt> requestersCHA;

    public List<PermissionDef> permDefs;
    public List<PermissionDef> permDefsCHA;
}
