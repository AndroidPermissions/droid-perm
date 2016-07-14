package org.oregonstate.droidperm.unused;

import java.io.File;

/**
 * Get resources availableto Java. Source: http://stackoverflow.com/a/12807848/4182868
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/14/2016.
 */
public class SizeOfMainMemory {
    public static void main(String[] args) {
        /* Total number of processors or cores available to the JVM */
        System.out.println("Available processors (cores): " +
                Runtime.getRuntime().availableProcessors());

        /* Total amount of free memory available to the JVM */
        System.out.println("Free memory (MB): " +
                Runtime.getRuntime().freeMemory() / 1024 / 1024);

        /* This will return Long.MAX_VALUE if there is no preset limit */
        long maxMemory = Runtime.getRuntime().maxMemory();
        /* Maximum amount of memory the JVM will attempt to use */
        System.out.println("Maximum memory (MB): " +
                (maxMemory == Long.MAX_VALUE ? "no limit" : maxMemory / 1024 / 1024));

        /* Total memory currently in use by the JVM */
        System.out.println("Total memory (MB): " +
                Runtime.getRuntime().totalMemory() / 1024 / 1024);

        /* Get a list of all filesystem roots on this system */
        File[] roots = File.listRoots();
        System.out.println();

        /* For each filesystem root, print some info */
        for (File root : roots) {
            System.out.println("File system root: " + root.getAbsolutePath());
            System.out.println("Total space (MB): " + root.getTotalSpace() / 1024 / 1024);
            System.out.println("Free space (MB): " + root.getFreeSpace() / 1024 / 1024);
            System.out.println("Usable space (MB): " + root.getUsableSpace() / 1024 / 1024);
        }
    }
}
