package org.oregonstate.droidperm.util;

import com.google.common.collect.Multimap;
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
     * Print a multimap to standard output. First header, then the keys with a key prefix, then values with a value
     * prefix
     */
    public static <K, V> void printMultimap(Multimap<K, V> multimap, String header, String keyPrefix,
                                            String valuePrefix) {
        System.out.println("\n\n" + header + " : " + multimap.size() + "\n"
                + "========================================================================");
        for (K key : multimap.keySet()) {
            System.out.println(keyPrefix + key + " : " + multimap.get(key).size());
            for (V value : multimap.get(key)) {
                System.out.println(valuePrefix + value);
            }
        }
    }

    public static <K> void printMultimapOfStmtValues(Multimap<K, Stmt> multimap, String header, String keyPrefix,
                                                     String valueIndent, String valuePrefix, boolean printStmt) {
        System.out.println("\n\n" + header + " : " + multimap.size() + "\n"
                + "========================================================================");
        for (K key : multimap.keySet()) {
            System.out.println(keyPrefix + key + " : " + multimap.get(key).size());
            for (Stmt stmt : multimap.get(key)) {
                printStmt(stmt, valueIndent, valuePrefix, printStmt);
            }
        }
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
        return SceneUtil.getMethodOf(stmt) + " L: " + stmt.getJavaSourceStartLineNumber();
    }

    public static void printStmt(Stmt stmt, final String indent, String prefix, boolean printStmt) {
        System.out.println(indent + prefix + toMethodLogString(stmt));
        if (printStmt) {
            System.out.println("\t" + indent + stmt);
        }
    }
}
