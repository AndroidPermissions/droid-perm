package org.oregonstate.droidperm.perm.axplorer;

import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermTargetKind;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.sens.SensitiveCollectorService;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 1/29/2017.
 */
public class AxplorerPermMiner {

    /**
     * Groups:
     * <p>
     * 1 - class name
     * <p>
     * 2 - method name
     * <p>
     * 3 - method params
     * <p>
     * 4 - return type
     * <p>
     * 5 - permissions
     */
    public static final Pattern AXPLORER_PATTERN =
            Pattern.compile("^([\\w.$]+)\\.(\\w+)\\(([^)]*)\\)([^\\s]+)\\s+::\\s+([^\\s].*)");

    /**
     * arg 0: source file
     * <p>
     * arg 1: destination xml file
     * <p>
     * arg 2: boolean, dangerousPermOnly. List of dangerous files read through SensitiveCollectorService.
     */
    public static void main(String[] args) throws IOException, JAXBException {
        Path sourceFile = Paths.get(args[0]);
        Path destFile = Paths.get(args[1]);
        boolean dangerousPermOnly = Boolean.parseBoolean(args[2]);

        List<PermissionDef> permissionDefs = parsePermissionDefs(sourceFile);
        if (dangerousPermOnly) {
            Set<String> dangerousPerm = SensitiveCollectorService.getAllDangerousPerm();
            permissionDefs.removeIf(permDef -> Collections.disjoint(permDef.getPermissionNames(), dangerousPerm));
            permissionDefs.forEach(permDef -> permDef.getPermissions()
                    .removeIf(permItem -> !dangerousPerm.contains(permItem.getName())));
        }

        Files.createDirectories(destFile.toAbsolutePath().getParent());
        JaxbUtil.save(new PermissionDefList(permissionDefs), PermissionDefList.class, destFile.toFile());
    }

    private static List<PermissionDef> parsePermissionDefs(Path filePath) throws IOException {
        return Files.lines(filePath).map(AxplorerPermMiner::buildPermissionDef).collect(Collectors.toList());
    }

    private static PermissionDef buildPermissionDef(String line) {
        line = line.replaceAll("\\[byte", "byte[]");
        Matcher lineMatcher = AXPLORER_PATTERN.matcher(line);
        if (!lineMatcher.matches()) {
            throw new RuntimeException("Cannot parse line: " + line);
        }
        String className = lineMatcher.group(1);
        String methodName = lineMatcher.group(2);
        String methodParams = lineMatcher.group(3).replaceAll(",", ", ");
        String returnType = lineMatcher.group(4);
        String permissionsStr = lineMatcher.group(5);
        String target = returnType + " " + methodName + "(" + methodParams + ")";
        List<String> permissions = Arrays.asList(permissionsStr.split(",\\s"));
        List<Permission> permissionItems = permissions.stream().map(perm -> new Permission(perm, null))
                .collect(Collectors.toList());
        return new PermissionDef(className, target, PermTargetKind.Method, permissionItems);
    }
}
