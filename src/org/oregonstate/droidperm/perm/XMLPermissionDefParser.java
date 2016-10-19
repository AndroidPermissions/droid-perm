package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
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
        sensitiveDefs = buildXmlSensitives(XmlPermDefMiner.unmarshallPermDefs(xmlPermDefFile).getPermissionDefs());
    }

    public static Set<AndroidMethod> buildXmlSensitives(List<PermissionDef> permissionDefs) {
        Set<AndroidMethod> xmlSensitives = new LinkedHashSet<>();
        String delimiters = "[ \\(\\),]+";
        String returnType;
        String targetName;


        for (PermissionDef permissionDef : permissionDefs) {
            //todo permissions targeting fields are ignored for the moment
            if (permissionDef.getTargetType() == TargetType.Method) {
                String[] tokens = permissionDef.getTargetName().split(delimiters);
                returnType = tokens[0].trim();
                targetName = tokens[1].trim();

                List<String> parameters = new ArrayList<>();
                for (int i = 2; i < tokens.length; i++) {
                    if (tokens[i].length() > 1) {
                        parameters.add(tokens[i].trim());
                    }
                }

                Set<String> permissions = permissionDef.getPermissions().stream().map(Permission::getName)
                        .collect(Collectors.toSet());

                AndroidMethod sensitiveDef = new AndroidMethod(targetName, parameters, returnType,
                        permissionDef.getClassName(), permissions);
                xmlSensitives.add(sensitiveDef);
            }
        }
        return xmlSensitives;
    }

    public Set<AndroidMethod> getSensitiveDefs() {
        return sensitiveDefs;
    }
}
