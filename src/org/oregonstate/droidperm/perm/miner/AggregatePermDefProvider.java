package org.oregonstate.droidperm.perm.miner;

import org.oregonstate.droidperm.perm.IPermissionDefProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/14/2016.
 */
public class AggregatePermDefProvider implements IPermissionDefProvider {

    private static final Logger logger = LoggerFactory.getLogger(AggregatePermDefProvider.class);

    private Set<SootMethodAndClass> permCheckerDefs = new LinkedHashSet<>();
    private Set<AndroidMethod> sensitiveDefs = new LinkedHashSet<>();

    @SafeVarargs
    public AggregatePermDefProvider(Set<SootMethodAndClass> permCheckerDefs,
                                    Set<AndroidMethod>... sensitiveDefSetList) {
        this.permCheckerDefs = Collections.unmodifiableSet(permCheckerDefs);
        this.sensitiveDefs = new LinkedHashSet<>(sensitiveDefSetList[0]);
        for (int i = 1; i < sensitiveDefSetList.length; i++) {
            Set<AndroidMethod> sensDefsToAdd = sensitiveDefSetList[i];
            if (Collections.disjoint(sensitiveDefs, sensDefsToAdd)) {
                sensitiveDefs.addAll(sensDefsToAdd);
            } else {
                Set<AndroidMethod> intersection = new LinkedHashSet<>(sensitiveDefs);
                intersection.retainAll(sensDefsToAdd);
                logger.error("Common sensitive defs found in 2 sources:");
                intersection.forEach(System.err::println);
                throw new RuntimeException("Common sensitive defs found in 2 sources");
            }
        }
        this.sensitiveDefs = Collections.unmodifiableSet(sensitiveDefs);
    }

    @Override
    public Set<SootMethodAndClass> getPermCheckerDefs() {
        return permCheckerDefs;
    }

    @Override
    public Set<AndroidMethod> getSensitiveDefs() {
        return sensitiveDefs;
    }
}
