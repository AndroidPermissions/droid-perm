package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.perm.miner.jaxb_out.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/28/2016.
 */
public class XMLPermissionDefParser implements IPermissionDefProvider {

    private static final Logger logger = LoggerFactory.getLogger(XMLPermissionDefParser.class);

    private Set<SootMethodAndClass> permCheckerDefs = new HashSet<>();
    private Set<AndroidMethod> sensitiveDefs = new HashSet<>();

    public XMLPermissionDefParser(File xmlPermDefFile, File txtPermDefFile) throws IOException, JAXBException {
        TxtPermissionDefParser txtPermissionDefParser = new TxtPermissionDefParser(txtPermDefFile);
        sensitiveDefs.addAll(txtPermissionDefParser.getSensitiveDefs());
        permCheckerDefs.addAll(txtPermissionDefParser.getPermCheckerDefs());

        Set<AndroidMethod> xmlSensitives = buildXmlSensitives(XmlPermDefMiner.unmarshallPermDefs(xmlPermDefFile));

        checkDisjoint(txtPermissionDefParser.getSensitiveDefs(), xmlSensitives);
        sensitiveDefs.addAll(xmlSensitives);
    }

    private void checkDisjoint(Set<AndroidMethod> txtSensitives, Set<AndroidMethod> xmlSensitives) {
        Set<String> txtSignatures = txtSensitives.stream().map(SootMethodAndClass::getSubSignature)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> xmlSignatures = xmlSensitives.stream().map(SootMethodAndClass::getSubSignature)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        txtSignatures.retainAll(xmlSignatures);
        if (!txtSignatures.isEmpty()) {
            logger.error("Sensitive defs found in both txt and xml definitions:");
            txtSignatures.forEach(System.err::println);
            throw new RuntimeException("Sensitive defs found in both txt and xml definitions");
        }
    }

    private Set<AndroidMethod> buildXmlSensitives(PermissionDefList permissionDefList) {
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

    @Override
    public Set<SootMethodAndClass> getPermCheckerDefs() {
        return permCheckerDefs;
    }

    @Override
    public Set<AndroidMethod> getSensitiveDefs() {
        return sensitiveDefs;
    }
}
