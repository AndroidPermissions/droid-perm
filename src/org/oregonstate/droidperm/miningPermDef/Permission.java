package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/23/2016.
 */
@XmlRootElement(name = "permission")
public class Permission {
    private String readWrite;
    private String name;

    @XmlAttribute
    public String getReadWrite() { return readWrite; }

    public void setReadWrite(String readWrite) {
        this.readWrite = readWrite;
    }

    @XmlAttribute
    public String getName() { return name; }

    public void setName(String name) {
        this.name = name;
    }
}
