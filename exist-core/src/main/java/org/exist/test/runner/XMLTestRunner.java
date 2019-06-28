/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.test.runner;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.source.ClassLoaderSource;
import org.exist.source.Source;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.ExistSAXParserFactory;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;
import javax.xml.parsers.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * A JUnit test runner which can run the XML formatter XQuery tests
 * of eXist-db using $EXIST_HOME/src/org/exist/xquery/lib/test.xq.
 *
 * @author Adam Retter
 */
public class XMLTestRunner extends AbstractTestRunner {

    private static final SAXParserFactory SAX_PARSER_FACTORY = ExistSAXParserFactory.getSAXParserFactory();
    static {
        SAX_PARSER_FACTORY.setNamespaceAware(true);
    }

    private final Document doc;
    private final XMLTestInfo info;

    /**
     * @param path The path to the XML file containing the tests.
     * @param parallel whether the tests should be run in parallel.
     *
     * @throws InitializationError if the test runner could not be constructed.
     */
    public XMLTestRunner(final Path path, final boolean parallel) throws InitializationError {
        super(path, parallel);
        try {
            this.doc = parse(path);
        } catch (final ParserConfigurationException | IOException | SAXException e) {
            throw new InitializationError(e);
        }
        this.info = extractTestInfo(doc);
    }

    private static XMLTestInfo extractTestInfo(final Document doc) throws InitializationError {
        String testName = null;
        String description = null;
        final List<String> childNames = new ArrayList<>();

        final Node docElement = doc.getFirstChild();
        if(docElement == null) {
            throw new InitializationError("Invalid XML test document");
        }

        final NodeList children = docElement.getChildNodes();
        for(int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if(child.getNodeType() == Node.ELEMENT_NODE && child.getNamespaceURI() == null) {
                switch (child.getLocalName()) {
                    case "testName":
                        testName = child.getTextContent().trim();
                        break;
                    case "description":
                        description = child.getTextContent().trim();
                        break;
                    case "test":
                        final NodeList testChildren = child.getChildNodes();
                        for (int j = 0; j < testChildren.getLength(); j++) {
                            final Node testChild = testChildren.item(j);
                            if (testChild.getNodeType() == Node.ELEMENT_NODE && testChild.getNamespaceURI() == null) {
                                if (testChild.getLocalName().equals("task")) {
                                    childNames.add(testChild.getTextContent().trim());
                                }
                            }
                        }
                        break;
                    default:
                        // ignored
                        break;
                }
            }
        }

        if(testName == null) {
            throw new InitializationError("Could not find <testName> in XML test document");
        }

        return new XMLTestInfo(testName, description, childNames);
    }

    private String getSuiteName() {
        return info.getName();
    }

    @Override
    public Description getDescription() {
        final Description description = Description.createSuiteDescription(getSuiteName(), (java.lang.annotation.Annotation[]) null);
        for (final String childName : info.getChildNames()) {
            description.addChild(Description.createTestDescription(getSuiteName(), childName, (java.lang.annotation.Annotation[]) null));
        }
        return description;
    }

    @Override
    public void run(final RunNotifier notifier) {
        try {
            final String pkgName = getClass().getPackage().getName().replace('.', '/');
            final Source query = new ClassLoaderSource(pkgName + "/xml-test-runner.xq");

            final List<java.util.function.Function<XQueryContext, Tuple2<String, Object>>> externalVariableDeclarations = Arrays.asList(
                context -> new Tuple2<>("doc", doc),
                context -> new Tuple2<>("id", Sequence.EMPTY_SEQUENCE),

                // set callback functions for notifying junit!
                context -> new Tuple2<>("test-ignored-function", new FunctionReference(new FunctionCall(context, new ExtTestIgnoredFunction(context, getSuiteName(), notifier)))),
                context -> new Tuple2<>("test-started-function", new FunctionReference(new FunctionCall(context, new ExtTestStartedFunction(context, getSuiteName(), notifier)))),
                context -> new Tuple2<>("test-failure-function", new FunctionReference(new FunctionCall(context, new ExtTestFailureFunction(context, getSuiteName(), notifier)))),
                context -> new Tuple2<>("test-assumption-failed-function", new FunctionReference(new FunctionCall(context, new ExtTestAssumptionFailedFunction(context, getSuiteName(), notifier)))),
                context -> new Tuple2<>("test-error-function", new FunctionReference(new FunctionCall(context, new ExtTestErrorFunction(context, getSuiteName(), notifier)))),
                context -> new Tuple2<>("test-finished-function", new FunctionReference(new FunctionCall(context, new ExtTestFinishedFunction(context, getSuiteName(), notifier))))
            );

            executeQuery(query, externalVariableDeclarations);


        } catch(final DatabaseConfigurationException | IOException | EXistException | PermissionDeniedException | XPathException e) {
            //TODO(AR) what to do here?
            throw new RuntimeException(e);
        }
    }

    private Document parse(final Path path) throws ParserConfigurationException, IOException, SAXException {
        final InputSource src = new InputSource(path.toUri().toASCIIString());
        final SAXParser parser = SAX_PARSER_FACTORY.newSAXParser();
        final XMLReader xr = parser.getXMLReader();

        xr.setFeature("http://xml.org/sax/features/external-general-entities", false);
        xr.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        xr.setFeature(FEATURE_SECURE_PROCESSING, true);

        // we have to use eXist-db's SAXAdapter, otherwise un-referenced namespaces as used by xpath assertions may be stripped by Xerces.
        final SAXAdapter adapter = new SAXAdapter();
        xr.setContentHandler(adapter);
        xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
        xr.parse(src);

        return adapter.getDocument();
    }

    private static class XMLTestInfo {
        @Nullable private final String name;
        @Nullable private final String description;
        private final List<String> childNames;

        private XMLTestInfo(@Nullable final String name, @Nullable final String description, final List<String> childNames) {
            this.name = name;
            this.description = description;
            this.childNames = childNames;
        }

        @Nullable
        public String getName() {
            return name;
        }

        @Nullable
        public String getDescription() {
            return description;
        }

        public List<String> getChildNames() {
            return childNames;
        }
    }
}
