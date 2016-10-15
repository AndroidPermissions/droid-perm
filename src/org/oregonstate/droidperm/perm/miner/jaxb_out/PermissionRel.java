package org.oregonstate.droidperm.perm.miner.jaxb_out;

/**
 * Relationship between a group of permissions on the same target.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/1/2016.
 */
public enum PermissionRel {
    AllOf, AnyOf;

    public static PermissionRel fromAnnotName(String name) {
        switch (name) {
            case "allOf":
                return AllOf;
            case "anyOf":
                return AnyOf;
            default:
                throw new RuntimeException("Unknown Permission rel: " + name);
        }
    }
}
