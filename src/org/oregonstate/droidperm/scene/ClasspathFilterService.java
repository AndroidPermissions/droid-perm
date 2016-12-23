package org.oregonstate.droidperm.scene;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 5/30/2016.
 */
public class ClasspathFilterService {

    private static final Logger logger = LoggerFactory.getLogger(ClasspathFilterService.class);

    private ScenePermissionDefService scenePermDef;

    public ClasspathFilterService(ScenePermissionDefService scenePermDef) {
        this.scenePermDef = scenePermDef;
    }

    public ClasspathFilter load(File file) throws IOException {
        List<String> methodSigs = Files.lines(file.toPath())
                .filter(line -> !(line.isEmpty() || line.startsWith("%")))
                .map(String::trim)
                .collect(Collectors.toList());
        Set<SootMethod> ignoreSet = SceneUtil.grabMethods(methodSigs);
        logger.info("Loaded classpath filter list with {} methods.", ignoreSet.size());

        //sensitives should be added to ignore set, to prevent their body from being analyzed
        ignoreSet.addAll(scenePermDef.getSceneMethodSensitives());
        ignoreSet.addAll(scenePermDef.getSceneParametricSensitives());

        return new ClasspathFilter(ignoreSet);
    }
}
