package org.oregonstate.droidperm.sens;

import org.xmlpull.v1.XmlPullParserException;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/31/2016.
 */
public class DPProcessManifest extends ProcessManifest {

    private Set<String> declaredPermissions;

    public DPProcessManifest(File apkFile) throws IOException, XmlPullParserException {
        super(apkFile);
    }

    /**
     * Called by superclass constructor.
     */
    @Override
    protected void handle(InputStream manifestIS) throws IOException, XmlPullParserException {
        super.handle(manifestIS);
        declaredPermissions = retrieveDeclaredPermissions();
    }

    private Set<String> retrieveDeclaredPermissions() {
        List<AXmlNode> permissionNodes = this.axml.getNodesWithTag("uses-permission");
        return permissionNodes.stream().map(node -> (String) node.getAttribute("name").getValue())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> getDeclaredPermissions() {
        return declaredPermissions;
    }
}
