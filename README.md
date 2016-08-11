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

    \--pathalgo CONTEXTSENSITIVE --notaintwrapper

 

Updating DroidPerm inside DroidPermPlugin
==========================================

### Prerequisites

-  Latest version of IntelliJ IDEA IDE with JDK 1.7 or higher
-  Latest revision of Android SDK (API 23)
-  *Optional:* Latest revision of Android NDK (API 23); build requirement for some Android apps
-  Base version of `droid-perm` project with all sub-modules from Denis' USB stick
-  Latest version of `DroidPermPlugin` project

### Steps

1. git update modules `droid-perm` and `android-23-api-crafted` from the `droid-perm` project
    - *Note:* Additional modules within `droid-perm` might require updates; specifically `soot`, `soot-infoflow`, and `soot-infoflow-android`

2. Load `droid-perm` project into IntelliJ IDEA; if not open from previous step

3. Build the `droid-perm.jar` by selecting `Build -> Build Artifacts... -> droid-perm:jar -> Build`
    - *Note:* Selecting `Rebuild` instead of `Build` might be required in some instances 

4. Build the `droid-perm` project by selecting `Build -> Make Project`

5. Open the following locations from a file explorer application:
    - `.../DroidPerm/android-23-api-crafted/out/production/android-23-api-crafted/`
    - `.../DroidPerm/run-dir/lib/android-23-cr-stubs.zip`

6. Copy the `android`, `java`, and `org` folders from `.../android-23-api-crafted/` into the `android-23-cr-stubs.zip`

7. Locate the `droid-perm.jar` (built in step 3) at `.../DroidPerm/out/artifacts/droid_perm_jar/droid-perm.jar`

8. Copy both `droid-perm.jar` and `android-23-cr-stubs.zip` to `.../DroidPermPlugin/dp-lib/`

9. Rename the copied `android-23-cr-stubs.zip` to `android-23-cr+util_io.zip`; replacing the old version

10. Copy the following configuration files from `.../DroidPerm/run-dir/` to `.../DroidPermPlugin/dp-lib/`:
    - `AndroidCallbacks.txt`
    - `EasyTaintWrapperSource.txt`
    - `OutflowIgnoreList.txt`
    - `PermissionDefs.txt`
    - `SourcesAndSinks.txt`
    - Folder `sootOutput/`

### Troubleshooting

-   Check that all module dependencies are correct by selecting `File -> Project Structure -> Modules` and for each module, validate that none of the items on the `Dependencies` tab are red. Also, verify whether the `Module SDK` has been set properly.
-   Check that all libraries and modules within the `Artifact` are configured properly by selecting `File -> Project Structure -> Artifacts` and verifying that `droid-perm:jar` does not contain any items in red within the `Output Layout` tab.
-   If `droid-perm` ran successfully, but results were empty:
    -   Check the last modification date of jaxb classes; the format may haved changed.
    -   Check the XML output generated by `droid-perm`.
