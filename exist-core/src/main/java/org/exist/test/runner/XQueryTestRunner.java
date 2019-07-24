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
import org.exist.security.PermissionDeniedException;
import org.exist.source.ClassLoaderSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Sequence;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A JUnit test runner which can run the XQuery tests (XQSuite)
 * of eXist-db using $EXIST_HOME/src/org/exist/xquery/lib/xqsuite/xqsuite.xql.
 *
 * @author Adam Retter
 */
public class XQueryTestRunner extends AbstractTestRunner {

    private final XQueryTestInfo info;

    /**
     * @param path The path to the XQuery file containing the XQSuite tests
     * @param parallel whether the tests should be run in parallel.
     *
     * @throws InitializationError if the test runner could not be constructed.
     */
    public XQueryTestRunner(final Path path, final boolean parallel) throws InitializationError {
        super(path, parallel);
        this.info = extractTestInfo(path);
    }

    private static XQueryTestInfo extractTestInfo(final Path path) throws InitializationError {
        try {
            final Source query = new StringSource("inspect:inspect-module(xs:anyURI(\"" + path.toAbsolutePath().toString() + "\"))");
            final Sequence inspectionResults = executeQuery(query, Collections.emptyList());

            // extract the details
            String prefix = null;
            String namespace = null;
            final List<XQueryTestInfo.TestFunctionDef> testFunctions = new ArrayList<>();

            if(inspectionResults != null && inspectionResults.hasOne()) {
                final Element moduleElement = (Element)inspectionResults.itemAt(0);

                prefix = moduleElement.getAttribute("prefix");
                namespace = moduleElement.getAttribute("uri");

                final NodeList children = moduleElement.getChildNodes();
                for(int i = 0; i < children.getLength(); i++) {
                    final Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE && child.getNamespaceURI() == null) {

                        if(child.getNodeName().equals("function")) {
                            boolean isTestFunction = false;
                            final NamedNodeMap functionAttributes = child.getAttributes();
                            final String name = functionAttributes.getNamedItem("name").getNodeValue();

                            String testFunctionAnnotatedName = null;
                            final NodeList functionChildren = child.getChildNodes();
                            for(int j = 0; j < functionChildren.getLength(); j++) {
                                final Node functionChild = functionChildren.item(j);
                                if (functionChild.getNodeType() == Node.ELEMENT_NODE && functionChild.getNamespaceURI() == null) {

                                    // filter functions by annotations... we only want the test:assert* annotated ones!
                                    if(functionChild.getNodeName().equals("annotation")) {
                                        final NamedNodeMap annotationAttributes = functionChild.getAttributes();
                                        final Node annotationAttributeName = annotationAttributes.getNamedItem("name");
                                        if(annotationAttributeName.getNodeValue().startsWith("test:assert")) {
                                            isTestFunction = true;
                                        } else if(annotationAttributeName.getNodeValue().equals("test:name")) {
                                            final NodeList annotationChildren = functionChild.getChildNodes();
                                            for(int k = 0; k < annotationChildren.getLength(); k++) {
                                                final Node annotationChild = annotationChildren.item(k);
                                                if(annotationChild.getNodeType() == Node.ELEMENT_NODE && annotationChild.getNamespaceURI() == null) {
                                                    if(annotationChild.getNodeName().equals("value")) {
                                                        testFunctionAnnotatedName = annotationChild.getTextContent();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if(isTestFunction) {
                                // strip module prefix from function name
                                String testFunctionLocalName = testFunctionAnnotatedName != null ? testFunctionAnnotatedName : name;
                                if(testFunctionLocalName.startsWith(prefix + ':')) {
                                    testFunctionLocalName = testFunctionLocalName.substring(testFunctionLocalName.indexOf(':') + 1);
                                    testFunctions.add(new XQueryTestInfo.TestFunctionDef(testFunctionLocalName));
                                }
                            }
                        }
                    }
                }
            }

            return new XQueryTestInfo(prefix, namespace, testFunctions);

        } catch(final DatabaseConfigurationException | IOException | EXistException | PermissionDeniedException | XPathException e) {
            throw new InitializationError(e);
        }
    }

    private String getSuiteName() {
         return info.getNamespace();
//        final String filename = path.getFileName().toString();
//        return filename.substring(0, filename.indexOf('.') - 1);
    }

    @Override
    public Description getDescription() {
        final Description description = Description.createSuiteDescription(getSuiteName(), (java.lang.annotation.Annotation[]) null);
        for (final XQueryTestInfo.TestFunctionDef testFunctionDef : info.getTestFunctions()) {
            description.addChild(Description.createTestDescription(getSuiteName(), testFunctionDef.getLocalName(), (java.lang.annotation.Annotation) null));
        }
        return description;
    }

    @Override
    public void run(final RunNotifier notifier) {
        try {
            final String pkgName = getClass().getPackage().getName().replace('.', '/');
            final Source query = new ClassLoaderSource(pkgName + "/xquery-test-runner.xq");
            final URI testModuleUri = path.toAbsolutePath().toUri();

            final List<java.util.function.Function<XQueryContext, Tuple2<String, Object>>> externalVariableDeclarations = Arrays.asList(
                    context -> new Tuple2<>("test-module-uri", new AnyURIValue(testModuleUri)),

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

    private static class XQueryTestInfo {
        private final String prefix;
        private final String namespace;
        private final List<TestFunctionDef> testFunctions;

        private XQueryTestInfo(final String prefix, final String namespace, final List<TestFunctionDef> testFunctions) {
            this.prefix = prefix;
            this.namespace = namespace;
            this.testFunctions = testFunctions;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getNamespace() {
            return namespace;
        }

        public List<TestFunctionDef> getTestFunctions() {
            return testFunctions;
        }

        private static class TestFunctionDef {
            private final String localName;

            private TestFunctionDef(final String localName) {
                this.localName = localName;
            }

            public String getLocalName() {
                return localName;
            }
        }
    }
}
