package org.oregonstate.droidperm.perm.miner.jaxb_out;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/23/2016.
 */
@XmlRootElement
public class Permission {
    private String name;
    private OperationType operationType;

    public Permission() {
    }

    public Permission(String name, OperationType operationType) {
        this.name = name;
        this.operationType = operationType;
    }

    @XmlAttribute
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute
    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Permission that = (Permission) o;
        return name != null ? name.equals(that.name) : that.name == null && operationType == that.operationType;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (operationType != null ? operationType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Permission{" +
                "name='" + name + '\'' +
                ", operationType=" + operationType +
                '}';
    }
}
