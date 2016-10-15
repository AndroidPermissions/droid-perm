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

    private List<JaxbItem> items = new ArrayList<>();

    @XmlElement(name = "item")
    public List<JaxbItem> getItems() {
        return items;
    }

    public void setItems(List<JaxbItem> items) {
        this.items = items;
    }

    public void addItem(JaxbItem item) {
        this.items.add(item);
    }
}
