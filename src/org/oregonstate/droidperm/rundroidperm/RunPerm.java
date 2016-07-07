package org.oregonstate.droidperm.rundroidperm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/5/2016.
 */
public class RunPerm {
    private static final String[] EXTRA_OPTS = new String[]{"--pathalgo", "CONTEXTSENSITIVE", "--notaintwrapper",
                                                            "--cgalgo", "GEOM", "--taint-analysis-enabled", "false"};


    /**
     * This main runs droid perm on a list of apk files that are found within a directory.
     * Command line arguments:
     * args[0] = path/to/directory/with/apks
     * args[1] = path/to/output/location
     * args[2] = path/to/droidperm/run/directory
     */
    public static void main(final String[] args) throws IOException, InterruptedException {

        batchRunner(args[0], args[1], args[2]);

    }

    private static void batchRunner(String apkPath, String outputPath, String droidPermHome) throws IOException {

        String droidPermClassPath = droidPermHome + "\\droid-perm.jar";
        String androidClassPath = droidPermHome + "\\android-23-cr+util_io.zip";
        List<String> processBuilderArgs = new ArrayList<>();

        List<File> apkFiles = getFiles(apkPath);
        List<String> apkNames = getAPKNames(apkPath);

        //todo The one-to-one relationship between the 2 iterators will only exist in the ideal case:
        // when there's exactly one apk for each directory inside apkPath.
        Iterator<File> apkIterator = apkFiles.iterator();
        Iterator<String> nameIterator = apkNames.iterator();

        while (apkIterator.hasNext()) {

            String name = nameIterator.next();
            File xmlFile = new File(outputPath + "\\" + name + ".xml");
            File logFile = new File(outputPath + "\\" + name + ".log");
            File errorFile = new File (outputPath + "\\" + name + ".error.log");

            processBuilderArgs.clear();

            processBuilderArgs.addAll(Arrays
                    .asList("java", "-jar", droidPermClassPath, apkIterator.next().getAbsolutePath(), androidClassPath,
                            "--xml-out", xmlFile.getAbsolutePath()));
            processBuilderArgs.addAll(Arrays.asList(EXTRA_OPTS));

            ProcessBuilder processBuilder = new ProcessBuilder(processBuilderArgs)
                    .directory(new File(droidPermHome))
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    .redirectError(ProcessBuilder.Redirect.appendTo(errorFile));

            System.out.println("Now analyzing app: " + name);

            long time = System.currentTimeMillis();

            Process process = processBuilder.start();
            try {
                process.waitFor();

                long newTime = System.currentTimeMillis();
                System.out.println("Analyzed " + name + " in " + (newTime - time) / 1E3 + " sec");

            } catch (InterruptedException e) {
                process.destroy();
            }
        }
    }


    public static List<File> getFiles(String absolutePath) {
        List<File> apkFiles = new ArrayList<>();
        File[] files = new File(absolutePath).listFiles();
        apkFiles.addAll(showFiles(files));

        return apkFiles;
    }

    private static List<File> showFiles(File[] files) {
        List<File> apkFiles = new ArrayList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                apkFiles.addAll(showFiles(file.listFiles()));
            } else {
                if(file.getName().endsWith("debug.apk")) {
                    apkFiles.add(file);
                }
            }
        }
        return apkFiles;
    }

    private static List<String> getAPKNames (String rootPath) {
        List<String> containerFiles = new ArrayList<>();

        File[] files = new File(rootPath).listFiles();

        for(File file : files) {
            if(file.isDirectory()) {
                containerFiles.add(file.getName());
            }
        }
        return containerFiles;
    }

}
