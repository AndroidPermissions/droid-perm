Prerequisites
=============

-   JDK 7, JDK 8.

-   Android SDK 23.

-   Intellij Idea

-   Whatever toolsuite to build Android apps.

Setup instructions
==================

1.  Create directory DroidPerm for the project

2.  Inside DroidPerm clone the following projects:

<https://bitbucket.org/denis_bogdanas/droid-perm>

<https://github.com/denis-bogdanas/soot>

<https://github.com/denis-bogdanas/soot-infoflow>

<https://github.com/denis-bogdanas/soot-infoflow-android>

<https://github.com/denis-bogdanas/soot-infoflow-android-iccta>

1.  Create a new Intellij project inside DroidPerm.

2.  Import modules from existing sources for soot, soot-infoflow,
    soot-infoflow-android. The last one is not used at this point.

-   For soot project follow this guide:
    https://github.com/Sable/soot/wiki/Building-Soot-with-IntelliJ-IDEA

1.  Put the following files into soot/libs\_intellij if they are not there
    already:

    -   ant-1.9.6.jar

    -   heros-trunk.jar

    -   jasmin-2.2.1.jar

    -   slf4j-api-1.7.5.jar

    -   slf4j-api-1.7.5-sources.jar

    -   slf4j-simple-1.7.5.jar

2.  Open module settings -\> soot -\> dependencies. Add all the files above as
    JARs, if not there yet.

3.  Check the following files under column “Export”:

    -   AXMLPrinter2.jar

    -   jasmin-2.2.1.jar

    -   slf4j-api-1.7.5.jar

    -   slf4j-api-1.7.5-sources.jar

    -   slf4j-simple-1.7.5.jar

4.  Mark soot-infoflow dependent on soot, soot-infoflow-android dependent on
    soot and soot-infoflow.

5.  Import the project DroidPerm. Theoretically should link to existing modules
    and should not need any extra settings.

6.  If there are any module dependencies causing errors, such as ECLIPSE
    library, remove them.

7.  Build the whole project. At this point should work.

8.  Optional. Open heros-trunk.jar and remove anything related to slf4j. This
    will eliminate slf4j - related warnings when running.

### Optional steps

-   Set project output directories to match directories inside ant build files.

-   Also setup ant.settings files in all soot and FlowDroid-related projects.

These steps should not be necessary, as you can build and run DroidPerm from
Intellij without using ant scripts.

Running DroidPerm
=================

### Test app

<https://github.com/AndroidPermissions/perm-test>

-   current test application

To build apk file:

-   go to project home dir

-   execute “gradle assemble”

### Displaying help

Create a new run configuration with the following settings:

-   Main class: org.oregonstate.droidperm.DroidPermMain

-   VM options: -Xmx8g

-   working directory: droid-perm

-   classpath of module: droid-perm

This execution will display all DroidPerm command line options

### Executing DroidPerm with default settings

Add the following program arguments:

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
<path to the apk file to analyze>
<path to android-sdk\platforms dir>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

### Executing DroidPerm with current analysis settings

-   Copy android.jar from android-sdk/platforms/android-23 to some other
    location.

-   Replace java.\* and javax.\* packages with the respective content from JDK

-   Use the following program arguments:

    --pathalgo CONTEXTSENSITIVE --notaintwrapper

 

Updating DroidPerm inside DroidPerm plugin
==========================================

### Prerequisites

-   droid-perm project with all modules from my stick

 

### Steps

-   Build the droid-perm jar: Build -\> Build Artifacts -\> droid-perm:jar
    -\>Build

-   Build the project

-   Go to DroidPerm\\android-23-api-crafted\\out\\production

-   Also open DroidPerm\\run-dir\\lib\\android-23-cr-stubs.zip

-   Copy the contet of the production dir ito the zip

-   Copy droid-perm.jar and android-23-cr-stubs.zip to their respective
    locations in droid-perm plugin

-   Also copy all the config files from directly inside droid-perm.

 

### Troubleshooting

-   If runnign result is empty & droid-perm runs successfully, check the last
    modification date of jaxb classes, maybe the format has changed.

-   Also check the xml putput.

 
