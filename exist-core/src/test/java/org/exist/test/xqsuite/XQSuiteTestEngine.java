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
package org.exist.test.xqsuite;

import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.runner.AbstractTestRunner;
import org.exist.test.runner.TestRunners;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A JUnit Platform engine for XQSuite and XML tests.
 * <p>
 * Classes annotated with {@link XQSuite} are discovered; each is a container whose children are
 * the test files (containers) and the tests within them. The tests themselves are run by the
 * same XQuery runner as before, which reports outcomes through {@link org.exist.test.runner.TestEvents}.
 */
public final class XQSuiteTestEngine implements TestEngine {
    public static final String ENGINE_ID = "exist-xqsuite";

    /**
     * Set when a database could not be stopped: a test file that was given up on as hung is still running
     * and keeps the database alive, so no further suite can be run safely in this JVM.
     */
    private static volatile String unusableBecause = null;

    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(final EngineDiscoveryRequest request, final UniqueId uniqueId) {
        final EngineDescriptor engine = new EngineDescriptor(uniqueId, "eXist-db XQSuite");

        final Set<Class<?>> classes = new LinkedHashSet<>();
        for (final ClassSelector selector : request.getSelectorsByType(ClassSelector.class)) {
            classes.add(selector.getJavaClass());
        }
        for (final ClasspathRootSelector selector : request.getSelectorsByType(ClasspathRootSelector.class)) {
            classes.addAll(ReflectionSupport.findAllClassesInClasspathRoot(selector.getClasspathRoot(), c -> true, n -> true));
        }
        for (final PackageSelector selector : request.getSelectorsByType(PackageSelector.class)) {
            classes.addAll(ReflectionSupport.findAllClassesInPackage(selector.getPackageName(), c -> true, n -> true));
        }

        try (final DiscoveryDatabase discoveryDb = new DiscoveryDatabase()) {
            for (final Class<?> clazz : classes) {
                final XQSuite annotation = clazz.getAnnotation(XQSuite.class);
                if (annotation != null) {
                    engine.addChild(discoverSuite(engine.getUniqueId(), clazz, annotation, discoveryDb));
                }
            }
        }
        return engine;
    }

    /**
     * Test files are discovered once per file for the life of the JVM: the launcher discovers each
     * class several times (for example once to decide whether to run it and once to run it).
     */
    private static final Map<Path, AbstractTestRunner> RUNNERS = new ConcurrentHashMap<>();

    /**
     * An embedded database that is only started if some file needs it for discovery.
     * XQuery modules may import modules that need the database to compile, and the database is
     * how tests have always been discovered, so it is used rather than compiling on its own.
     */
    private static final class DiscoveryDatabase implements AutoCloseable {
        private ExistEmbeddedServer server;

        BrokerPool pool() throws Exception {
            if (server == null) {
                server = new ExistEmbeddedServer(true, true);
                server.startDb();
            }
            return server.getBrokerPool();
        }

        @Override
        public void close() {
            if (server != null) {
                server.stopDb();
                server = null;
            }
        }
    }

    private static SuiteDescriptor discoverSuite(final UniqueId engineId, final Class<?> clazz, final XQSuite annotation, final DiscoveryDatabase discoveryDb) {
        final UniqueId suiteId = engineId.append("suite", clazz.getName());
        final List<AbstractTestRunner> runners = new ArrayList<>();
        Throwable failure = null;
        try {
            for (final String suite : annotation.value()) {
                final Path path = Path.of(suite);
                if (!Files.exists(path)) {
                    throw new IOException("XQSuite does not exist: " + suite + ". path=" + path.toAbsolutePath());
                }
                if (Files.isDirectory(path)) {
                    try (final Stream<Path> children = Files.list(path)) {
                        final List<Path> sorted = children.filter(p -> !Files.isDirectory(p)).sorted(Comparator.comparing(Path::toString)).toList();
                        for (final Path child : sorted) {
                            addRunner(runners, child, discoveryDb);
                        }
                    }
                } else {
                    addRunner(runners, path, discoveryDb);
                }
            }
        } catch (final Throwable t) {
            failure = t;
        }

        final SuiteDescriptor suite = new SuiteDescriptor(suiteId, clazz, annotation.parallel(), failure);
        for (final AbstractTestRunner runner : runners) {
            final FileDescriptor file = new FileDescriptor(suiteId.append("file", runner.getSourcePath().toString()), runner);
            for (final String testName : runner.getTestNames()) {
                file.addTest(testName);
            }
            suite.addChild(file);
        }
        return suite;
    }

    private static void addRunner(final List<AbstractTestRunner> runners, final Path path, final DiscoveryDatabase discoveryDb) throws Exception {
        final Path key = path.toAbsolutePath().normalize();
        AbstractTestRunner runner = RUNNERS.get(key);
        if (runner == null) {
            runner = TestRunners.newTestRunner(path, TestRunners.isXQueryTestFile(path) ? discoveryDb.pool() : null);
            if (runner != null) {
                RUNNERS.put(key, runner);
            }
        }
        if (runner != null) {
            runners.add(runner);
        }
    }

    @Override
    public void execute(final ExecutionRequest request) {
        final EngineExecutionListener listener = request.getEngineExecutionListener();
        final TestDescriptor root = request.getRootTestDescriptor();
        listener.executionStarted(root);
        final XQSuiteSettings settings = new XQSuiteSettings(request.getConfigurationParameters());
        for (final TestDescriptor suite : new ArrayList<>(root.getChildren())) {
            runSuite((SuiteDescriptor) suite, listener, settings);
        }
        listener.executionFinished(root, TestExecutionResult.successful());
    }

    private static void runSuite(final SuiteDescriptor suite, final EngineExecutionListener listener, final XQSuiteSettings settings) {
        final String unusable = unusableBecause;
        if (unusable != null) {
            listener.executionSkipped(suite, "Not run: " + unusable);
            return;
        }
        listener.executionStarted(suite);
        Throwable failure = suite.discoveryFailure();
        ExistEmbeddedServer server = null;
        boolean started = false;
        try {
            if (failure == null) {
                server = new ExistEmbeddedServer(true, true);
                server.startDb();
                started = true;
                invokeStatic(suite.suiteClass(), BeforeAll.class);
                new SuiteRun(suite, listener, server.getBrokerPool(), settings).run();
            }
        } catch (final Throwable t) {
            failure = t;
        } finally {
            if (started) {
                try {
                    invokeStatic(suite.suiteClass(), AfterAll.class);
                } catch (final Throwable t) {
                    failure = add(failure, t);
                }
                try {
                    if (!stopWithin(server::stopDb, settings.hangGrace())) {
                        final String reason = "the embedded database of " + suite.suiteClass().getName() + " could not be stopped within "
                                + settings.hangGrace().toSeconds() + " seconds; a test file that was given up on as hung is probably still running";
                        unusableBecause = reason;
                        failure = add(failure, new IllegalStateException(reason));
                    }
                } catch (final Throwable t) {
                    failure = add(failure, t);
                }
            }
        }
        listener.executionFinished(suite, failure == null ? TestExecutionResult.successful() : TestExecutionResult.failed(failure));
    }

    private static void invokeStatic(final Class<?> clazz, final Class<? extends java.lang.annotation.Annotation> annotation) {
        for (final Method method : ReflectionSupport.findMethods(clazz,
                m -> Modifier.isStatic(m.getModifiers()) && m.isAnnotationPresent(annotation), HierarchyTraversalMode.TOP_DOWN)) {
            ReflectionSupport.invokeMethod(method, null);
        }
    }

    /**
     * Runs {@code stop} on a thread of its own and waits for it for at most {@code timeout}.
     *
     * @return false if it was still running when the time was up
     */
    static boolean stopWithin(final Runnable stop, final java.time.Duration timeout) throws InterruptedException {
        final java.util.concurrent.atomic.AtomicReference<Throwable> thrown = new java.util.concurrent.atomic.AtomicReference<>();
        final Thread thread = new Thread(() -> {
            try {
                stop.run();
            } catch (final Throwable t) {
                thrown.set(t);
            }
        }, "xqsuite-stop");
        thread.setDaemon(true);
        thread.start();
        thread.join(timeout.toMillis());
        if (thread.isAlive()) {
            return false;
        }
        if (thrown.get() != null) {
            throw new IllegalStateException("The embedded database failed to stop", thrown.get());
        }
        return true;
    }

    private static Throwable add(final Throwable existing, final Throwable additional) {
        if (existing == null) {
            return additional;
        }
        existing.addSuppressed(additional);
        return existing;
    }
}
