package org.oregonstate.droidperm.perm.miner;

import org.oregonstate.droidperm.perm.IPermissionDefProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

        Set<AndroidMethod> sensitiveDefs = new LinkedHashSet<>();
        Set<String> sensitiveSigs = new HashSet<>();
        for (Set<AndroidMethod> sensDefsToAdd : sensitiveDefSetList) {
            Set<String> sensitiveSigsToAdd = sensDefsToAdd.stream().map(SootMethodAndClass::getSignature)
                    .collect(Collectors.toSet());
            if (Collections.disjoint(sensitiveSigs, sensitiveSigsToAdd)) {
                sensitiveDefs.addAll(sensDefsToAdd);
                sensitiveSigs.addAll(sensitiveSigsToAdd);
            } else {
                Set<String> intersection = new LinkedHashSet<>(sensitiveSigs);
                intersection.retainAll(sensitiveSigsToAdd);
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
