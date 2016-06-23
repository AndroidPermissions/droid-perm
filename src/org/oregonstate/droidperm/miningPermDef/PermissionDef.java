package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/20/2016.
 */
@XmlRootElement(name = "PermissionDef")
public class PermissionDef {
    private Target target;

    public PermissionDef(){
        this.target = new Target();
    }

    @XmlElement
    public Target getTarget() { return target; }

    public void setTarget(Target target) {
        this.target = target;
    }
}
