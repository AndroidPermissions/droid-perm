/* ******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE. All rights reserved. This program and the
 * accompanying materials are made available under the terms of the GNU Lesser Public License v2.1 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p>
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric Bodden, and others.
 ******************************************************************************/
package org.oregonstate.droidperm.perm;

import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parser for the permissions to method map. Based on PermissionMethodParser from FlowDroid.
 *
 * @author Siegfried Rasthofer, Denis Bogdanas
 * @see soot.jimple.infoflow.android.data.parsers.PermissionMethodParser
 */
public class TxtPermissionDefProvider implements IPermissionDefProvider {

    public static final String CONST_PERM_CHECKER = "_PERM_CHECKER_";

    private Set<SootMethodAndClass> permCheckerDefs = new LinkedHashSet<>();
    private Set<AndroidMethod> sensitiveDefs = new LinkedHashSet<>();

    private List<String> lines;

    /**
     * Groups: 1 = defining class, 2 = return type, 3 = method name, 4 = parameters, 5 = either _PERM_CHECKER_ or a list
     * of comma-separated permissions
     */
    private static final String regex = "^<(.+):\\s*(.+)\\s+(.+)\\s*\\((.*)\\)>\\s+->\\s+(.+)$";
    private static final Pattern pattern = Pattern.compile(regex);

    /**
     * Creates a TxtPermissionDefProvider loading data from a file.
     */
    public TxtPermissionDefProvider(File file) throws IOException {
        readFile(file);
        parseLines();
    }

    private void readFile(File file) throws IOException {
        lines = new ArrayList<>();
        try (FileReader fr = new FileReader(file)) {
            try (BufferedReader br = new BufferedReader(fr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            }
        }
    }

    private void parseLines() {
        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("%")) {
                continue;
            }

            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                AndroidMethod parsedDef = parsePermissionDef(matcher);

                if (parsedDef.isSource()) {
                    permCheckerDefs.add(parsedDef);
                } else if (parsedDef.isSink()) {
                    sensitiveDefs.add(parsedDef);
                }
            } else {
                throw new RuntimeException("Line does not match: " + line);
            }
        }
    }

    private AndroidMethod parsePermissionDef(Matcher matcher) {
        String className = matcher.group(1).trim();
        String returnType = matcher.group(2).trim();
        String methodName = matcher.group(3).trim();
        String paramsSource = matcher.group(4).trim();
        List<String> methodParameters =
                Arrays.stream(paramsSource.split(",")).map(String::trim).collect(Collectors.toList());

        //either CONST_PERM_CHECKER, or a list of permission defs separated by ","
        String checkerOrPermList = matcher.group(5).trim();
        AndroidMethod androidMethodDef;

        if (checkerOrPermList.equals(CONST_PERM_CHECKER)) {
            androidMethodDef =
                    new AndroidMethod(methodName, methodParameters, returnType, className, Collections.emptySet());
            androidMethodDef.setSource(true);
        } else {
            Set<String> permissions = Arrays.stream(checkerOrPermList.split(","))
                    .map(String::trim)
                    .map(TxtPermissionDefProvider::parsePermission).collect(Collectors.toSet());
            androidMethodDef =
                    new AndroidMethod(methodName, methodParameters, returnType, className, permissions);
            androidMethodDef.setSink(true);
        }

        return androidMethodDef;
    }

    private static String parsePermission(String permDef) {
        if (permDef.isEmpty()) {
            throw new RuntimeException("Empty permission found!");
        }
        if (!permDef.contains(".")) {
            permDef = "android.permission." + permDef;
        }
        return permDef;
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
