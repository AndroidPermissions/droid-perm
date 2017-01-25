package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.sens.SensitiveCollectorService;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/27/2016.
 */
public class AnnoPermissionDefUtil {
    public static void printAnnoPermDefs(boolean dangerousPermOnly) {
        System.out.println(
                "\nRequiresPermission annotations: "
                        + getPermissionDefs(dangerousPermOnly).size()
                        + "\n========================================================================\n");

        PermissionDefList pdList = new PermissionDefList();
        pdList.setPermissionDefs(getPermissionDefs(dangerousPermOnly));

        try {
            JaxbUtil.print(pdList, PermissionDefList.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public static void collectPermAnno(File xmlOut, boolean dangerousPermOnly) throws JAXBException, IOException {
        printAnnoPermDefs(dangerousPermOnly);
        if (xmlOut != null) {
            PermissionDefList out = new PermissionDefList();
            out.setPermissionDefs(getPermissionDefs(dangerousPermOnly));
            JaxbUtil.save(out, PermissionDefList.class, xmlOut);
        }
    }

    private static List<PermissionDef> getPermissionDefs(boolean dangerousPermOnly) {
        List<PermissionDef> list = AnnoPermissionDefProvider.getInstance().getPermissionDefs();
        if (dangerousPermOnly) {
            Set<String> dangerousPerm = SensitiveCollectorService.getAllDangerousPerm();
            return list.stream()
                    //retain only definitions with some dangerous permissions
                    .filter(permDef -> !Collections.disjoint(permDef.getPermissionNames(), dangerousPerm))
                    //retain only dangerous permissions in the new permission defs
                    .map(old -> new PermissionDef(old.getClassName(), old.getTarget(), old.getTargetKind(),
                            old.getPermissionRel(),
                            old.getPermissions().stream().filter(perm -> dangerousPerm.contains(perm.getName()))
                                    .collect(Collectors.toList()),
                            old.getComment(), old.isConditional()))
                    .collect(Collectors.toList());
        } else {
            return list;
        }
    }
}
