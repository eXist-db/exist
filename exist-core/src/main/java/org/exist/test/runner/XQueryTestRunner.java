/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.test.runner;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.repo.ExistRepository;
import org.exist.security.PermissionDeniedException;
import org.exist.source.ClassLoaderSource;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReference;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A JUnit test runner which can run the XQuery tests (XQSuite)
 * of eXist-db using $EXIST_HOME/src/org/exist/xquery/lib/xqsuite/xqsuite.xql.
 *
 * @author Adam Retter
 */
public class XQueryTestRunner extends AbstractTestRunner {

    private static final String XQSUITE_NAMESPACE = "http://exist-db.org/xquery/xqsuite";

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

    private static Configuration getConfiguration() throws DatabaseConfigurationException {
        final Optional<Path> home = Optional.ofNullable(System.getProperty("exist.home", System.getProperty("user.dir"))).map(Paths::get);
        final Path confFile = ConfigurationHelper.lookup("conf.xml", home);

        if (confFile.isAbsolute() && Files.exists(confFile)) {
            return new Configuration(confFile.toAbsolutePath().toString());
        } else {
            return new Configuration(FileUtils.fileName(confFile), home);
        }
    }

    private static XQueryTestInfo extractTestInfo(final Path path) throws InitializationError {
        try {
            final Configuration config = getConfiguration();

            final ExistRepository expathRepo = new ExistRepository();
            try {
                expathRepo.configure(config);
                expathRepo.prepare(null);
            } catch (final BrokerPoolServiceException e) {
                throw new InitializationError(e);
            }

            final XQueryContext xqueryContext = new XQueryContext(config);
            try {
                xqueryContext.setTestRepository(Optional.of(expathRepo));

                final Source xquerySource = new FileSource(path, UTF_8, false);
                final XQuery xquery = new XQuery();

                final CompiledXQuery compiledXQuery = xquery.compile(xqueryContext, xquerySource);

                String moduleNsPrefix = null;
                String moduleNsUri = null;
                final List<XQueryTestInfo.TestFunctionDef> testFunctions = new ArrayList<>();

                final Iterator<UserDefinedFunction> localFunctions = compiledXQuery.getContext().localFunctions();
                while (localFunctions.hasNext()) {
                    final UserDefinedFunction localFunction = localFunctions.next();
                    final FunctionSignature localFunctionSignature = localFunction.getSignature();

                    String testName = null;
                    int testArity = 0;
                    boolean isTest = false;

                    final Annotation[] annotations = localFunctionSignature.getAnnotations();
                    if (annotations != null) {
                        for (final Annotation annotation : annotations) {
                            final QName annotationName = annotation.getName();
                            if (annotationName.getNamespaceURI().equals(XQSUITE_NAMESPACE)) {
                                if (annotationName.getLocalPart().startsWith("assert")) {
                                    isTest = true;
                                    if (testName != null) {
                                        break;
                                    }
                                } else if ("name".equals(annotationName.getLocalPart())) {
                                    final LiteralValue[] annotationValues = annotation.getValue();
                                    if (annotationValues != null && annotationValues.length > 0) {
                                        testName = annotationValues[0].getValue().getStringValue();
                                        if (isTest) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isTest) {
                        if (testName == null) {
                            testName = localFunctionSignature.getName().getLocalPart();
                            testArity = localFunctionSignature.getArgumentCount();
                        }

                        if (moduleNsPrefix == null) {
                            moduleNsPrefix = localFunctionSignature.getName().getPrefix();
                        }
                        if (moduleNsUri == null) {
                            moduleNsUri = localFunctionSignature.getName().getNamespaceURI();
                        }

                        testFunctions.add(new XQueryTestInfo.TestFunctionDef(testName, testArity));
                    }
                } // end while

                return new XQueryTestInfo(moduleNsPrefix, moduleNsUri, testFunctions);
            } finally {
                xqueryContext.runCleanupTasks();
                xqueryContext.reset();
            }

        } catch (final DatabaseConfigurationException | IOException | PermissionDeniedException | XPathException e) {
            throw new InitializationError(e);
        }
    }

    private String getSuiteName() {
        if (info.getNamespace() == null) {
            return path.getFileName().toString();
        }

        return namespaceToPackageName(info.getNamespace());
    }

    private String namespaceToPackageName(final String namespace) {
        try {
            final URI uri = new URI(namespace);
            final StringBuilder packageName = new StringBuilder();
            hostNameToPackageName(uri.getHost(), packageName);
            pathToPackageName(uri.getPath(), packageName);
            packageName.insert(0, "xqts.");  // add "xqts." prefix
            return packageName.toString();
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void hostNameToPackageName(String host, final StringBuilder buffer) {
        while (host != null && !host.isEmpty()) {
            if (buffer.length() > 0) {
                buffer.append('.');
            }

            final int idx = host.lastIndexOf('.');
            if (idx > -1) {
                buffer.append(host.substring(idx + 1));
                host = host.substring(0, idx);
            } else {
                buffer.append(host);
                host = null;
            }
        }
    }

    private void pathToPackageName(String path, final StringBuilder buffer) {
        path = path.replace('.', '_');
        path = path.replace('/', '.');
        buffer.append(path);
    }

    @Override
    public Description getDescription() {
        final String suiteName = checkDescription(this, getSuiteName());
        final Description description = Description.createSuiteDescription(suiteName);
        for (final XQueryTestInfo.TestFunctionDef testFunctionDef : info.getTestFunctions()) {
            description.addChild(Description.createTestDescription(suiteName, checkDescription(testFunctionDef, testFunctionDef.getLocalName())));
        }
        return description;
    }

    @Override
    public void run(final RunNotifier notifier) {
        try {
            final String pkgName = getClass().getPackage().getName().replace('.', '/');
            final Source query = new ClassLoaderSource(pkgName + "/xquery-test-runner.xq");
            final URI testModuleUri = path.toAbsolutePath().toUri();

            final String suiteName = getSuiteName();

            final List<java.util.function.Function<XQueryContext, Tuple2<String, Object>>> externalVariableDeclarations = Arrays.asList(
                    context -> new Tuple2<>("test-module-uri", new AnyURIValue(testModuleUri)),

                    // set callback functions for notifying junit!
                    context -> new Tuple2<>("test-ignored-function", new FunctionReference(new FunctionCall(context, new ExtTestIgnoredFunction(context, suiteName, notifier)))),
                    context -> new Tuple2<>("test-started-function", new FunctionReference(new FunctionCall(context, new ExtTestStartedFunction(context, suiteName, notifier)))),
                    context -> new Tuple2<>("test-failure-function", new FunctionReference(new FunctionCall(context, new ExtTestFailureFunction(context, suiteName, notifier)))),
                    context -> new Tuple2<>("test-assumption-failed-function", new FunctionReference(new FunctionCall(context, new ExtTestAssumptionFailedFunction(context, suiteName, notifier)))),
                    context -> new Tuple2<>("test-error-function", new FunctionReference(new FunctionCall(context, new ExtTestErrorFunction(context, suiteName, notifier)))),
                    context -> new Tuple2<>("test-finished-function", new FunctionReference(new FunctionCall(context, new ExtTestFinishedFunction(context, suiteName, notifier))))
            );

            // NOTE: at this stage EXIST_EMBEDDED_SERVER_CLASS_INSTANCE in XSuite will be usable
            final BrokerPool brokerPool = XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE.getBrokerPool();
            executeQuery(brokerPool, query, externalVariableDeclarations);

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
            private final int arity;

            private TestFunctionDef(final String localName, final int arity) {
                this.localName = localName;
                this.arity = arity;
            }

            public String getLocalName() {
                return localName;
            }

            public int getArity() {
                return arity;
            }
        }
    }
}
