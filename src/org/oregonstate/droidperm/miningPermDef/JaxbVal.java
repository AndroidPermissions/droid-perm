package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/16/2016.
 */

@XmlRootElement
public class JaxbVal {

    private String name;
    private String val;

    public JaxbVal(){
    }

    @XmlAttribute
    public String getName(){ return name; }

    public void setName(String name) { this.name = name; }

    @XmlAttribute
    public String getVal() { return val; }

    public void setVal(String val) { this.val = val; }
}
