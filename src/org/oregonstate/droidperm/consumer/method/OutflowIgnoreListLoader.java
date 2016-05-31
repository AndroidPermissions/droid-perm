package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.SceneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 5/30/2016.
 */
public class OutflowIgnoreListLoader {

    private static final Logger logger = LoggerFactory.getLogger(OutflowIgnoreListLoader.class);

    public static List<SootMethod> load(File file) throws IOException {
        List<String> methodSigs = Files.lines(file.toPath())
                .filter(line -> !(line.isEmpty() || line.startsWith("%")))
                .map(String::trim)
                .collect(Collectors.toList());
        List<SootMethod> sootMethods = SceneUtil.grabMethods(methodSigs);
        logger.info("Loaded outflow ignore list with {} methods.", sootMethods.size());

        return sootMethods;
    }
}
