package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/16/2016.
 */
@XmlRootElement(name = "annotation")
public class JaxbAnnotation {
    private String name;
    private List<JaxbVal> vals = new ArrayList<>();

    @XmlAttribute
    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    @XmlElement(name = "val")
    public List<JaxbVal> getVals() { return vals; }

    public void setVals(List<JaxbVal> vals) {
        this.vals = vals;
    }

    public void addVal(JaxbVal val) { vals.add(val); }

    @Override
    public String toString() {
        return ("<" +
                name +
                ">")
                .intern();
    }
}
