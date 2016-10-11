package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.perm.miner.jaxb_out.TargetType;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/28/2016.
 */
public class XMLPermissionDefParser implements IPermissionDefProvider {

    private Set<SootMethodAndClass> permCheckerDefs = new HashSet<>();
    private Set<AndroidMethod> sensitiveDefs = new HashSet<>();

    public XMLPermissionDefParser(File xmlPermDefFile, File txtPermDefFile) throws IOException, JAXBException {
        TxtPermissionDefParser txtPermissionDefParser = new TxtPermissionDefParser(txtPermDefFile);
        sensitiveDefs.addAll(txtPermissionDefParser.getSensitiveDefs());
        permCheckerDefs.addAll(txtPermissionDefParser.getPermCheckerDefs());

        addSensitives(XmlPermDefMiner.unmarshallPermDefs(xmlPermDefFile));
    }

    private void addSensitives(PermissionDefList permissionDefList) {
        String delimiters = "[ \\(\\),]+";
        String returnType;
        String targetName;


        for (PermissionDef permissionDef : permissionDefList.getPermissionDefs()) {
            List<String> parameters = new ArrayList<>();
            Set<String> permissions = new HashSet<>();

            //todo permissions targeting fields are ignored for the moment
            if (permissionDef.getTargetType() != TargetType.Field && !permissionDef.getPermissions().isEmpty()) {
                String[] tokens = permissionDef.getTargetName().split(delimiters);
                returnType = tokens[0];
                targetName = tokens[1];
                for (int i = 2; i < tokens.length; i++) {
                    if (tokens[i].length() > 1) {
                        parameters.add(tokens[i]);
                    }
                }

                for (Permission permission : permissionDef.getPermissions()) {
                    permissions.add(permission.getName());
                }


                sensitiveDefs.add(new AndroidMethod(targetName, parameters, returnType,
                        permissionDef.getClassName(), permissions));
            }
        }
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
