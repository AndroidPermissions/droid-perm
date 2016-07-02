package org.oregonstate.droidperm.perm.miner.jaxb_in;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/16/2016.
 */
@XmlRootElement(name = "root")
public class JaxbItemList {

    private List<JaxbItem> item = new ArrayList<>();

    @XmlElement
    public List<JaxbItem> getItem() {
        return item;
    }

    public void setItem(List<JaxbItem> items) {
        this.item = items;
    }

    public void addItem(JaxbItem item) {
        this.item.add(item);
    }
}
