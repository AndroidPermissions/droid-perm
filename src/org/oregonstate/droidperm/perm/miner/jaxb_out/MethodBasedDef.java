package org.oregonstate.droidperm.perm.miner.jaxb_out;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 12/14/2016.
 */
@SuppressWarnings("unused")
public class MethodBasedDef {

    @XmlAttribute
    private String className;
    @XmlAttribute
    private String target;

    public MethodBasedDef() {
    }

    public MethodBasedDef(String className, String target) {
        this.className = className;
        this.target = target;
    }

    public String getClassName() {
        return className;
    }

    public String getTarget() {
        return target;
    }
}
