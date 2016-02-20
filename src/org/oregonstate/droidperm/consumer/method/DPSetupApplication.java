/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p/>
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package org.oregonstate.droidperm.consumer.method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParserException;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.data.parsers.PermissionMethodParser;
import soot.jimple.infoflow.android.source.parsers.xml.XMLSourceSinkParser;
import soot.jimple.infoflow.rifl.RIFLSourceSinkDefinitionProvider;
import soot.jimple.infoflow.source.data.ISourceSinkDefinitionProvider;
import soot.jimple.infoflow.source.data.SourceSinkDefinition;

import javax.activation.UnsupportedDataTypeException;
import java.io.IOException;
import java.util.*;

public class DPSetupApplication {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ISourceSinkDefinitionProvider sourceSinkProvider;

    /**
     * Gets the set of sources loaded into FlowDroid. These are the sources as
     * they are defined through the SourceSinkManager.
     *
     * @return The set of sources loaded into FlowDroid
     */
    public Set<SourceSinkDefinition> getProducers() {
        return this.sourceSinkProvider == null ? null
                : this.sourceSinkProvider.getSources();
    }

    /**
     * Gets the set of sinks loaded into FlowDroid These are the sinks as
     * they are defined through the SourceSinkManager.
     *
     * @return The set of sinks loaded into FlowDroid
     */
    public Set<SourceSinkDefinition> getConsumers() {
        return this.sourceSinkProvider == null ? null
                : this.sourceSinkProvider.getSinks();
    }

    /**
     * Prints the list of sources registered with FlowDroud to stdout
     */
    public void printProducerDefs() {
        if (this.sourceSinkProvider == null) {
            System.err.println("Producers not calculated yet");
            return;
        }
        logger.info("\n\nMethod based permission producer definitions: \n====================================");
        for (SourceSinkDefinition am : getProducers()) {
            logger.info(am.toString());
        }
        logger.info("End of producers");
    }

    /**
     * Prints the list of sinks registered with FlowDroud to stdout
     */
    public void printConsumerDefs() {
        if (this.sourceSinkProvider == null) {
            System.err.println("Consumers not calculated yet");
            return;
        }
        logger.info("\n\nMethod based permission consumer definitions: \n====================================");
        for (SourceSinkDefinition am : getConsumers()) {
            logger.info(am.toString());
        }
        logger.info("End of consumers");
    }

    /**
     * Calculates the sets of sources, sinks, entry points, and callbacks methods for the given APK file.
     *
     * @param sources
     *            The methods that shall be considered as sources
     * @param sinks
     *            The methods that shall be considered as sinks
     * @throws IOException
     *             Thrown if the given source/sink file could not be read.
     * @throws XmlPullParserException
     *             Thrown if the Android manifest file could not be read.
     */
    public void calculateSourcesSinksEntrypoints(Set<AndroidMethod> sources,
                                                 Set<AndroidMethod> sinks) throws IOException, XmlPullParserException {
        final Set<SourceSinkDefinition> sourceDefs = new HashSet<>(sources.size());
        final Set<SourceSinkDefinition> sinkDefs = new HashSet<>(sinks.size());

        for (AndroidMethod am : sources)
            sourceDefs.add(new SourceSinkDefinition(am));
        for (AndroidMethod am : sinks)
            sinkDefs.add(new SourceSinkDefinition(am));

        ISourceSinkDefinitionProvider parser = new ISourceSinkDefinitionProvider() {

            @Override
            public Set<SourceSinkDefinition> getSources() {
                return sourceDefs;
            }

            @Override
            public Set<SourceSinkDefinition> getSinks() {
                return sinkDefs;
            }

            @Override
            public Set<SourceSinkDefinition> getAllMethods() {
                Set<SourceSinkDefinition> sourcesSinks = new HashSet<>(sourceDefs.size()
                        + sinkDefs.size());
                sourcesSinks.addAll(sourceDefs);
                sourcesSinks.addAll(sinkDefs);
                return sourcesSinks;
            }

        };

        calculateSourcesSinksEntrypoints(parser);
    }

    /**
     * Calculates the sets of sources, sinks, entry points, and callback methods
     * for the given APK file.
     *
     * @param sourceSinkFile
     *            The full path and file name of the file containing the sources and sinks
     * @throws IOException
     *             Thrown if the given source/sink file could not be read.
     * @throws XmlPullParserException
     *             Thrown if the Android manifest file could not be read.
     */
    public void calculateSourcesSinksEntrypoints(String sourceSinkFile)
            throws IOException, XmlPullParserException {
        ISourceSinkDefinitionProvider parser = null;

        String fileExtension = sourceSinkFile.substring(sourceSinkFile.lastIndexOf("."));
        fileExtension = fileExtension.toLowerCase();

        try {
            if (fileExtension.equals(".xml"))
                parser = XMLSourceSinkParser.fromFile(sourceSinkFile);
            else if (fileExtension.equals(".txt"))
                parser = PermissionMethodParser.fromFile(sourceSinkFile);
            else if (fileExtension.equals(".rifl"))
                parser = new RIFLSourceSinkDefinitionProvider(sourceSinkFile);
            else
                throw new UnsupportedDataTypeException("The Inputfile isn't a .txt or .xml file.");

            calculateSourcesSinksEntrypoints(parser);
        } catch (SAXException ex) {
            throw new IOException("Could not read XML file", ex);
        }
    }

    /**
     * Calculates the sets of sources, sinks, entry points, and callbacks methods for the given APK file.
     *
     * @param sourcesAndSinks
     *            A provider from which the analysis can obtain the list of
     *            sources and sinks
     * @throws IOException
     *             Thrown if the given source/sink file could not be read.
     * @throws XmlPullParserException
     *             Thrown if the Android manifest file could not be read.
     */
    public void calculateSourcesSinksEntrypoints(ISourceSinkDefinitionProvider sourcesAndSinks)
            throws IOException, XmlPullParserException {
        // To look for callbacks, we need to start somewhere. We use the Android
        // lifecycle methods for this purpose.
        this.sourceSinkProvider = sourcesAndSinks;
    }

}
