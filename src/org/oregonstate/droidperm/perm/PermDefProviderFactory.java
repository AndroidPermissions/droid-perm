package org.oregonstate.droidperm.perm;

import com.google.common.io.Files;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/27/2016.
 */
public class PermDefProviderFactory {

    public static IPermissionDefProvider create(File file) {
        String ext = Files.getFileExtension(file.getName());
        try {
            if ("txt".equals(ext)) {
                return new TxtPermissionDefProvider(file);
            } else if ("xml".equals(ext)) {
                return new XMLPermissionDefProvider(file);
            } else {
                throw new RuntimeException("Unsupported extension for: " + file);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static IPermissionDefProvider create(List<File> files, boolean useAnnoPermDef) {
        List<IPermissionDefProvider> providers
                = files.stream().map(PermDefProviderFactory::create).collect(Collectors.toList());
        if (useAnnoPermDef) {
            providers.add(AnnoPermissionDefProvider.getInstance());
        }
        return new AggregatePermDefProvider(providers);
    }
}
