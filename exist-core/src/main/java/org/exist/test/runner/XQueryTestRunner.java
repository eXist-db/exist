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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.runner.Description;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        this(path, parallel, XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE != null ? XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE.getBrokerPool() : null);
    }

    /**
     * @param path The path to the XQuery file containing the XQSuite tests
     * @param parallel whether the tests should be run in parallel.
     * @param discoveryPool a running database to discover the tests with, or null to discover by compiling the module
     *
     * @throws InitializationError if the test runner could not be constructed.
     */
    public XQueryTestRunner(final Path path, final boolean parallel, @Nullable final BrokerPool discoveryPool) throws InitializationError {
        super(path, parallel);
        this.info = discoverOrExtractTestInfo(path, discoveryPool);
    }

    /**
     * Obtain test metadata by discovery when possible, otherwise by compiling the module.
     * When the DB is already started (e.g. by XSuite), try runDiscovery first so we run a single
     * discovery XQuery instead of compiling the module twice. Fall back to extractTestInfo in two
     * cases: (1) the DB is not started, or (2) the DB is started but runDiscovery returns null
     * (e.g. discovery failed, empty result, or wrong XML shape).
     *
     * @param path the path to the XQuery file containing the XQSuite tests
     * @return test info (from discovery or from compiling the module)
     * @throws InitializationError if the runner could not be constructed
     */
    private static XQueryTestInfo discoverOrExtractTestInfo(final Path path, @Nullable final BrokerPool pool) throws InitializationError {
        if (pool != null) {
            final XQueryTestInfo discovered = runDiscovery(pool, path);
            if (discovered != null) {
                return discovered;
            }
        }
        return extractTestInfo(path);
    }

    /**
     * The name under which the XQSuite runtime reports a test that has no explicit {@code %test:name}
     * (see {@code test:get-test-name} in xqsuite.xql): the lexical name of the function, with a prefix
     * dropped only if it consists solely of word characters. XPath's {@code \w} excludes punctuation,
     * so a prefix such as {@code my-tests} is kept.
     */
    static String runtimeTestName(final String prefix, final String localName) {
        if (prefix == null || prefix.isEmpty() || prefix.codePoints().allMatch(XQueryTestRunner::isXPathWordChar)) {
            return localName;
        }
        return prefix + ":" + localName;
    }

    private static boolean isXPathWordChar(final int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION, Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
                 Character.PARAGRAPH_SEPARATOR, Character.CONTROL, Character.FORMAT, Character.SURROGATE,
                 Character.PRIVATE_USE, Character.UNASSIGNED -> false;
            default -> true;
        };
    }

    private static Configuration getConfiguration() throws DatabaseConfigurationException {
        final Optional<Path> home = Optional.ofNullable(System.getProperty("exist.home", System.getProperty("user.dir"))).map(Path::of);
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
                            testName = runtimeTestName(localFunctionSignature.getName().getPrefix(), localFunctionSignature.getName().getLocalPart());
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

    /**
     * Runs the discovery XQuery for the given path (single XQuery per file).
     * Used so callers can avoid compiling the module twice (one discovery run vs full compile in extractTestInfo).
     *
     * @param brokerPool the broker pool (DB must be started)
     * @param path the path to the XQuery file
     * @return test info from the discovery result, or null if discovery fails or returns no usable result
     */
    @Nullable
    static XQueryTestInfo runDiscovery(final BrokerPool brokerPool, final Path path) {
        try {
            final String pkgName = XQueryTestRunner.class.getPackage().getName().replace('.', '/');
            final Source discoverySource = new ClassLoaderSource(pkgName + "/xquery-discovery.xq");
            final List<java.util.function.Function<XQueryContext, Tuple2<String, Object>>> bindings = Collections.singletonList(
                context -> new Tuple2<>("test-module-uri", new AnyURIValue(path.toAbsolutePath().toUri()))
            );
            final Sequence result = executeQuery(brokerPool, discoverySource, bindings, path.getParent());
            if (result == null || result.getItemCount() < 1) {
                return null;
            }
            final Item first = result.itemAt(0);
            if (!(first instanceof NodeValue)) {
                return null;
            }
            final Node root = ((NodeValue) first).getNode();
            if (root.getNodeType() != Node.ELEMENT_NODE || !"discovery".equals(root.getLocalName())) {
                return null;
            }
            final Element discovery = (Element) root;
            final String namespace = discovery.getAttribute("namespace");
            final String prefix = discovery.getAttribute("prefix");
            final NodeList fList = discovery.getElementsByTagName("f");
            final List<XQueryTestInfo.TestFunctionDef> testFunctions = new ArrayList<>(fList.getLength());
            for (int i = 0; i < fList.getLength(); i++) {
                final Element f = (Element) fList.item(i);
                final String name = f.getAttribute("name");
                final int arity = Integer.parseInt(f.getAttribute("arity"));
                testFunctions.add(new XQueryTestInfo.TestFunctionDef(name, arity));
            }
            return new XQueryTestInfo(prefix, namespace, testFunctions);
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public String getSuiteName() {
        if (info.namespace() == null) {
            return path.getFileName().toString();
        }

        return namespaceToPackageName(info.namespace());
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
            if (!buffer.isEmpty()) {
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
        for (final XQueryTestInfo.TestFunctionDef testFunctionDef : info.testFunctions()) {
            description.addChild(Description.createTestDescription(suiteName, checkDescription(testFunctionDef, testFunctionDef.localName())));
        }
        return description;
    }

    @Override
    public List<String> getTestNames() {
        return info.testFunctions().stream().map(XQueryTestInfo.TestFunctionDef::localName).toList();
    }

    @Override
    public void run(final RunNotifier notifier) {
        // NOTE: at this stage EXIST_EMBEDDED_SERVER_CLASS_INSTANCE in XSuite will be usable
        run(new RunNotifierTestEvents(getSuiteName(), notifier), XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE.getBrokerPool());
    }

    @Override
    public void run(final TestEvents events, final BrokerPool brokerPool) {
        try {
            final String pkgName = getClass().getPackage().getName().replace('.', '/');
            final Source query = new ClassLoaderSource(pkgName + "/xquery-test-runner.xq");
            final URI testModuleUri = path.toAbsolutePath().toUri();

            final List<java.util.function.Function<XQueryContext, Tuple2<String, Object>>> externalVariableDeclarations = Arrays.asList(
                    context -> new Tuple2<>("test-module-uri", new AnyURIValue(testModuleUri)),

                    // set callback functions for reporting test outcomes!
                    context -> new Tuple2<>("test-ignored-function", new FunctionReference(new FunctionCall(context, new ExtTestIgnoredFunction(context, getSuiteName(), events)))),
                    context -> new Tuple2<>("test-started-function", new FunctionReference(new FunctionCall(context, new ExtTestStartedFunction(context, getSuiteName(), events)))),
                    context -> new Tuple2<>("test-failure-function", new FunctionReference(new FunctionCall(context, new ExtTestFailureFunction(context, getSuiteName(), events, path)))),
                    context -> new Tuple2<>("test-assumption-failed-function", new FunctionReference(new FunctionCall(context, new ExtTestAssumptionFailedFunction(context, getSuiteName(), events)))),
                    context -> new Tuple2<>("test-error-function", new FunctionReference(new FunctionCall(context, new ExtTestErrorFunction(context, getSuiteName(), events)))),
                    context -> new Tuple2<>("test-finished-function", new FunctionReference(new FunctionCall(context, new ExtTestFinishedFunction(context, getSuiteName(), events))))
            );

            executeQuery(brokerPool, query, externalVariableDeclarations);
        } catch(final DatabaseConfigurationException | IOException | EXistException | PermissionDeniedException | XPathException e) {
            //TODO(AR) what to do here?
            throw new RuntimeException(e);
        }
    }

    record XQueryTestInfo(String prefix, String namespace, List<TestFunctionDef> testFunctions) {
        record TestFunctionDef(String localName, int arity) { }
    }
}
