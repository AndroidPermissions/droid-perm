package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.scene.SceneUtil;
import soot.jimple.Stmt;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 11/1/2016.
 */
public class PrintUtil {

    /**
     * Print collection to standard output. First header, then the collection, one element per line.
     */
    public static <T> void printCollection(Collection<T> collection, String header) {
        System.out.println("\n\n" + header + " : " + collection.size() + "\n"
                + "========================================================================");
        collection.forEach(System.out::println);
    }

    /**
     * Print the elements of a collection to file, one per line.
     */
    public static <T> void printCollectionToFile(Collection<T> collection, File file) throws IOException {
        List<String> itemsAsStrings =
                collection.stream().map(Object::toString).collect(Collectors.toList());
        Files.write(file.toPath(), itemsAsStrings, Charset.defaultCharset());
    }

    public static String toLogString(Stmt stmt) {
        return stmt + " : " + stmt.getJavaSourceStartLineNumber();
    }

    /**
     * Print containing method and this statement line number.
     */
    public static String toMethodLogString(Stmt stmt) {
        return SceneUtil.getMethodOf(stmt) + " : " + stmt.getJavaSourceStartLineNumber();
    }
}
