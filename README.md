This repository contains:

-   DPSpec - the Android 6.0 permission specification produced from various
    documentation formats in Android SDK.

-   DroidPerm - a static analysis tool for recommending runtime
    permission check insertion points in Android apps.

 

DPSpec
======

All files are in droid-perm/config:

-   [perm-def-api-23.xml](config/perm-def-api-23.xml) - permissions from annotaton xml files supplied with
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

-   Java 8.

-   Android SDK 23.

-   Intellij Idea

Setup instructions
==================

-   Create directory DroidPerm

-   Inside DroidPerm clone the following:

<https://github.com/AndroidPermissions/droid-perm>

<https://github.com/denis-bogdanas/soot>

<https://github.com/denis-bogdanas/soot-infoflow>

<https://github.com/denis-bogdanas/soot-infoflow-android>

-   Create a new EMPTY Intellij project inside DroidPerm. It’s importnant to
    make it empty at this point, e.g. don’t create any module.

-   Import soot project using this guide:
    <https://github.com/Sable/soot/wiki/Building-Soot-with-IntelliJ-IDEA> With
    the following exceptions:

    -   Step 8: Use Java 8.

    -   Step 9: Remove dependencies for projects jasmin and heros. Add
        dependencies for directory “droid-perm\\lib\\lib_soot\\” Check the box
        “export” for this directory.

    -   Go to module settings -\> sources. Select language level 7.

-   Import modules from existing sources for soot-infoflow,
    soot-infoflow-android. For both use import from “eclipse model”, Java 8 SDK,
    language level 7, similar to above. After import remove red dependencies to
    heros/jasmin.

-   In soot-infoflow, if it creates a dependence to “ECLIPSE”, remove it.

-   In module settings -\> soot -\> dependencies -\> AXMLPrinter2.jar: Check the
    box “export”.

-   Import module droid-perm. For this open modulesettings -\> import -\>
    directory droid-perm -\> select droid-perm.iml.

-   Import the project DroidPerm. If you did the previous steps properly it
    should not require extra configurations.

-   At this point you should be able to build the project.

 

Running DroidPerm
=================

### Executing DroidPerm with default settings

Create a run configuration with following settings:

-   Main class: org.oregonstate.droidperm.main.DroidPermMain

-   working directory: DroidPerm\\droid-perm

-   classpath of module: droid-perm

-   program arguments:

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
<path to the apk file to analyze>
droid-perm/config/android-23-util+async.zip
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

 

### Displaying help

Same as above with no arguments.

### Test apps

<https://github.com/AndroidPermissions/perm-test>

-   this repository contains the collection of test apps developed alongside
    DroidPerm.

 

Interpreting the results
------------------------

Log file is divided into several sections. Each section starts with a header
followed by =======================

Sections, starting from the end:

-   Sensitives in context in the call graph - lists the sensitives

-   Checkers in context in the call graph - checkers found in the app

-   Requests in context in the call graph - permission requests

-   next 3 sections: same as above but for each entry the list of callbacks from
    which it is reached.

-   Next few sections are used for undetected permissions analysis.

-   “Output for droid-perm-plugin, required permissions for statements directly
    inside callbacks” - this section shows where permission checks should be
    inserted.

-   Paths from each callback to each sensitive - enumerates all paths from
    callbacks to sensitives, with points-to data for each virtual method call.

 

Updating DroidPerm inside DroidPermPlugin (outdated)
====================================================

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


DroidPerm evaluation
======
This page contains our evaluation corpus and results: [evaluation summary.md](eval/evaluation summary.md)
