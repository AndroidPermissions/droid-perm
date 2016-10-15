package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.perm.miner.jaxb_out.TargetType;
import soot.jimple.infoflow.android.data.AndroidMethod;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/28/2016.
 */
public class XMLPermissionDefParser {

    private Set<AndroidMethod> sensitiveDefs;

    public XMLPermissionDefParser(File xmlPermDefFile) throws JAXBException {
        sensitiveDefs = buildXmlSensitives(XmlPermDefMiner.unmarshallPermDefs(xmlPermDefFile));
    }

    //todo for some reason not all sensitives are registered.
    private static Set<AndroidMethod> buildXmlSensitives(PermissionDefList permissionDefList) {
        Set<AndroidMethod> xmlSensitives = new LinkedHashSet<>();
        String delimiters = "[ \\(\\),]+";
        String returnType;
        String targetName;


        for (PermissionDef permissionDef : permissionDefList.getPermissionDefs()) {
            //todo permissions targeting fields are ignored for the moment
            if (permissionDef.getTargetType() != TargetType.Field && !permissionDef.getPermissions().isEmpty()) {
                String[] tokens = permissionDef.getTargetName().split(delimiters);
                returnType = tokens[0];
                targetName = tokens[1];

                List<String> parameters = new ArrayList<>();
                for (int i = 2; i < tokens.length; i++) {
                    if (tokens[i].length() > 1) {
                        parameters.add(tokens[i]);
                    }
                }

                Set<String> permissions = permissionDef.getPermissions().stream().map(Permission::getName)
                        .collect(Collectors.toSet());

                xmlSensitives.add(new AndroidMethod(targetName, parameters, returnType,
                        permissionDef.getClassName(), permissions));
            }
        }
        return xmlSensitives;
    }

    public Set<AndroidMethod> getSensitiveDefs() {
        return sensitiveDefs;
    }
}
