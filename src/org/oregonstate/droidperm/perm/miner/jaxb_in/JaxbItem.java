package org.oregonstate.droidperm.perm.miner.jaxb_in;


import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/14/2016.
 */
@XmlRootElement(name = "item")
public class JaxbItem {

    private String name;
    private List<JaxbAnnotation> annotations = new ArrayList<>();

    public JaxbItem() {
    }

    @XmlAttribute
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "annotation")
    public List<JaxbAnnotation> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<JaxbAnnotation> annotations) {
        this.annotations = annotations;
    }

    public void addAnnotation(JaxbAnnotation annotation) {
        annotations.add(annotation);
    }

    @Override
    public String toString() {
        return ("<" +
                name +
                ">")
                .intern();
    }
}
