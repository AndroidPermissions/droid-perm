This repository contains:

-   DPSpec - the Android 6.0 permission specification produced from various
    documentation formats in Android SDK.

-   DroidPerm (also DPerm) - a static analysis tool for recommending runtime
    permission check insertion points in Android apps.

Both are described in this paper: <https://arxiv.org/abs/1706.05042>

 

DPSpec
======

All files are in droid-perm/config:

-   perm-def-api-23.xml - permissions from annotaton xml files supplied with
    AndroidSDK, up to API 23. If you install Android SDK, these annotations are
    in android-sdk\\platform-tools\\api\\annotations.zip

-   perm-def-api-25.xml - same specification for API 25.

-   perm-def-play-services.xml - permissions from google play API.

-   javadoc-perm-def-API-23.xml - permissions mined from Android SDK Javadoc.

-   perm-def-manual.xml - permissions collected manually by inspecting apps from
    f-droid.org

Additionally:

-   checker-param-sens-def.xml - Other permission-related configs used by
    DroidPerm: permission checkers, requesters, parametric sensitives.

 

To use DPSpec in your project
-----------------------------

The easiest way is to copy the code from
`org.oregonstate.droidperm.perm.miner.jaxb_out` and load the classes through
JAXB. In DroidPerm this is done in `JaxbUtil.load()`.

 

Installing DroidPerm
====================

Prerequisites
-------------

-   JDK 7, JDK 8.

-   Android SDK 23.

-   Intellij Idea

Setup instructions
==================

1.  Create directory DroidPerm

2.  Inside DroidPerm clone the following:

<https://github.com/AndroidPermissions/droid-perm>

<https://github.com/AndroidPermissions/android-23-api-crafted>

<https://github.com/denis-bogdanas/soot>

<https://github.com/denis-bogdanas/soot-infoflow>

<https://github.com/denis-bogdanas/soot-infoflow-android>

-   Create a new EMPTY Intellij project inside DroidPerm. It’s importnant to
    make it empty at this point, e.g. don’t create any module.

-   Import soot project using this guide:
    <https://github.com/Sable/soot/wiki/Building-Soot-with-IntelliJ-IDEA> Use
    Java 8 as module SDK.

-    

1.  Import modules from existing sources for soot, soot-infoflow,
    soot-infoflow-android.

-   For soot project follow this guide:
    https://github.com/Sable/soot/wiki/Building-Soot-with-IntelliJ-IDEA

1.  Put the following files into soot/libs_intellij if they are not there
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

    \--pathalgo CONTEXTSENSITIVE --notaintwrapper

 

Updating DroidPerm inside DroidPermPlugin
=========================================

### Prerequisites

-   Latest version of IntelliJ IDEA IDE with JDK 1.7 or higher

-   Latest revision of Android SDK (API 23)

-   *Optional:* Latest revision of Android NDK (API 23); build requirement for
    some Android apps

-   Base version of `droid-perm` project with all sub-modules from Denis' USB
    stick

-   Latest version of `DroidPermPlugin` project

### Steps

1.  git update modules `droid-perm` and `android-23-api-crafted` from the
    `droid-perm` project

    -   *Note:* Additional modules within `droid-perm` might require updates;
        specifically `soot`, `soot-infoflow`, and `soot-infoflow-android`

2.  Load `droid-perm` project into IntelliJ IDEA; if not open from previous step

3.  Build the `droid-perm.jar` by selecting `Build -> Build Artifacts... ->
    droid-perm:jar -> Build`

    -   *Note:* Selecting `Rebuild` instead of `Build` might be required in some
        instances

4.  Build the `droid-perm` project by selecting `Build -> Make Project`

5.  Open the following locations from a file explorer application:

    -   `.../DroidPerm/android-23-api-crafted/out/production/android-23-api-crafted/`

    -   `.../DroidPerm/run-dir/lib/android-23-cr-stubs.zip`

6.  Copy the `android`, `java`, and `org` folders from
    `.../android-23-api-crafted/` into the `android-23-cr-stubs.zip`

7.  Locate the `droid-perm.jar` (built in step 3) at
    `.../DroidPerm/out/artifacts/droid_perm_jar/droid-perm.jar`

8.  Copy both `droid-perm.jar` and `android-23-cr-stubs.zip` to
    `.../DroidPermPlugin/dp-lib/`

9.  Rename the copied `android-23-cr-stubs.zip` to `android-23-cr+util_io.zip`;
    replacing the old version

10. Copy the directory config from `.../DroidPerm/droid-perm/` to
    `.../DroidPermPlugin/dp-lib/`.

### Troubleshooting

-   Check that all module dependencies are correct by selecting `File -> Project
    Structure -> Modules` and for each module, validate that none of the items
    on the `Dependencies` tab are red. Also, verify whether the `Module SDK` has
    been set properly.

-   Check that all libraries and modules within the `Artifact` are configured
    properly by selecting `File -> Project Structure -> Artifacts` and verifying
    that `droid-perm:jar` does not contain any items in red within the `Output
    Layout` tab.

-   If `droid-perm` ran successfully, but results were empty:

    -   Check the last modification date of jaxb classes; the format may haved
        changed.

    -   Check the XML output generated by `droid-perm`.
