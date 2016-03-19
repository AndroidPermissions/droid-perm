Prerequisites
=============

-   JDK 7, JDK 8.

-   Android SDK 23.

-   Intellij Idea

-   Whatever toolsuite to build Android apps.

Setup instructions
==================

1. Create directory DroidPerm for the project

2. Inside DroidPerm clone the following projects:

<https://bitbucket.org/denis_bogdanas/droid-perm>

<https://github.com/denis-bogdanas/soot>

<https://github.com/denis-bogdanas/soot-infoflow>

<https://github.com/denis-bogdanas/soot-infoflow-android>

<https://github.com/denis-bogdanas/soot-infoflow-android-iccta>

 

3. Create a new Intellij project inside DroidPerm.

4. Import modules from existing sources for soot, soot-infoflow,
soot-infoflow-android. The last one is not used at this point.

-   For soot project follow this guide:
    https://github.com/Sable/soot/wiki/Building-Soot-with-IntelliJ-IDEA

5. Put the following files into soot/libs\_intellij if they are not there already:

    -   ant-1.9.6.jar
    -   heros-trunk.jar
    -   jasmin-2.2.1.jar
    -   slf4j-api-1.7.5.jar
    -   slf4j-api-1.7.5-sources.jar
    -   slf4j-simple-1.7.5.jar

6. Open module settings -\> soot -\> dependencies. Add all the files above as
JARs, if not there yet.

7. Check the following files under column “Export”:

    -   AXMLPrinter2.jar
    -   jasmin-2.2.1.jar
    -   slf4j-api-1.7.5.jar
    -   slf4j-api-1.7.5-sources.jar
    -   slf4j-simple-1.7.5.jar

8. Mark soot-infoflow dependent on soot, soot-infoflow-android dependent on soot
and soot-infoflow.

9. Import the project DroidPerm. Theoretically should link to existing modules
and should not need any extra settings.

10. If there are any module dependencies causing errors, such as ECLIPSE
library, remove them.

11. Build the whole project. At this point should work.

12. Optional. Open heros-trunk.jar and remove anything related to slf4j. This
will eliminate slf4j - related warnings when running.

 

### Optional steps

-   Set project output directories to match directories inside ant build files.

-   Also setup ant.settings files in all soot and FlowDroid-related projects.

These steps should not be necessary, as you can build and run DroidPerm from
Intellij without using ant scripts.

 

Running DroidPerm
=================

### Setup

-   Create a directory run-dir for as working directory

-   Copy inside the following files:

    -   SourcesAndSinks.txt
    -   producersConsumers.txt
    -   EasyTaintWrapperSource.txt
    -   AndroidCallbacks.txt
     

All files are inside misc project modules.

 

### Test app

<https://github.com/AndroidPermissions/perm-test>

-   current test application

 

To build apk file:

-   go to project home dir

-   execute “gradle assemble”

 

### Displaying help

Create a new run configuration with the following settings:

-   Main class: org.oregonstate.droidperm.FlowDroidMain

-   VM options: -Xmx8g

-   working directory: run-dir from above

-   classpath of module: droid-perm

 

This execution will display all DroidPerm command line options

 

### Executing DroidPerm with default settings

Add the following program arguments:

    <path to the apk file to analyze>
    <path to android-sdk\platforms dir>

 

### Executing DroidPerm with current analysis settings

-   Copy android.jar from android-sdk/platforms/android-23 to some other
    location.

-   Replace java.\* and javax.\* packages with the respective content from JDK
    7.

-   Use the following program arguments:


    <path to the apk file to analyze>
    <path to crafted android.jar>
    --pathalgo
    CONTEXTSENSITIVE
    --notaintwrapper
