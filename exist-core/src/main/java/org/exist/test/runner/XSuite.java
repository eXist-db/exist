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

import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.XMLFilenameFilter;
import org.exist.util.XQueryFilenameFilter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Using <code>XSuite</code> as a runner allows you to manually
 * build a suite containing tests from both:
 *
 * 1. XQSuite - as defined in $EXIST_HOME/src/org/exist/xquery/lib/xqsuite/xqsuite.xql
 * 2. XML Test - as defined in $EXIST_HOME/src/org/exist/xquery/lib/test.xq
 *
 * To use it, annotate a class
 * with <code>@RunWith(XSuite.class)</code> and <code>@XSuiteClasses({"extensions/my-extension/src/test/xquery", ...})</code>.
 * When you run this class, it will run all the tests in all the suite classes.
 *
 * @author Adam Retter
 */
public class XSuite extends ParentRunner<Runner> {

    /**
     * Returns an empty suite.
     *
     * @return a runner for an empty suite.
     */
    public static Runner emptySuite() {
        try {
            return new XSuite((Class<?>) null, new String[0]);
        } catch (InitializationError e) {
            throw new RuntimeException("This shouldn't be possible");
        }
    }

    /**
     * The <code>XSuiteFiles</code> annotation specifies the directory/file containing the tests to be run when a class
     * annotated with <code>@RunWith(XSuite.class)</code> is run.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    public @interface XSuiteFiles {
        /**
         * @return the classes to be run
         */
        String[] value();
    }

    /**
     * The <code>XSuiteParallel</code> annotation specifies that the tests will be run in parallel when a class
     * annotated with <code>@RunWith(XSuite.class)</code> is run.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    public @interface XSuiteParallel {
    }

    private final List<Runner> runners;
    @Nullable
    private final ParallelFileScheduler parallelScheduler;

    private static String[] getAnnotatedDirectories(final Class<?> klass) throws InitializationError {
        final XSuite.XSuiteFiles annotation = klass.getAnnotation(XSuite.XSuiteFiles.class);
        if (annotation == null) {
            throw new InitializationError(String.format("class '%s' must have a XSuiteFiles annotation", klass.getName()));
        }
        return annotation.value();
    }

    private static boolean hasParallelAnnotation(@Nullable final Class<?> klass) {
        if (klass == null) {
            return false;
        }
        final XSuite.XSuiteParallel annotation = klass.getAnnotation(XSuite.XSuiteParallel.class);
        return annotation != null;
    }

    /**
     * Called reflectively on classes annotated with <code>@RunWith(XSuite.class)</code>
     *
     * @param klass the root class
     * @param builder builds runners for classes in the suite
     *
     * @throws InitializationError if the XSuite cannot be constructed
     */
    public XSuite(final Class<?> klass, final RunnerBuilder builder) throws InitializationError {
        this(builder, klass, getAnnotatedDirectories(klass));
    }

    /**
     * Call this when there is no single root class (for example, multiple class names
     * passed on the command line to {@link org.junit.runner.JUnitCore}
     *
     * @param builder builds runners for classes in the suite
     * @param suites the directories/files in the suite
     *
     * @throws InitializationError if the XSuite cannot be constructed
     */
    public XSuite(final RunnerBuilder builder, final String[] suites) throws InitializationError {
        this(builder, null, suites);
    }

    /**
     * Call this when the default builder is good enough. Left in for compatibility with JUnit 4.4.
     *
     * @param klass the root of the suite
     * @param suites the directories/files in the suite
     *
     * @throws InitializationError if the XSuite cannot be constructed
     */
    protected XSuite(final Class<?> klass, final String[] suites) throws InitializationError {
        this(null, klass, suites);
    }

    /**
     * Called by this class and subclasses once the classes making up the suite have been determined.
     *
     * @param builder builds runners for classes in the suite
     * @param klass the root of the suite
     * @param suites the directories/files in the suite
     *
     * @throws InitializationError if the XSuite cannot be constructed
     */
    protected XSuite(final RunnerBuilder builder, final Class<?> klass, final String[] suites) throws InitializationError {
        this(klass, getRunners(suites, hasParallelAnnotation(klass)));
    }

    /**
     * Called by this class and subclasses once the runners making up the suite have been determined.
     *
     * @param klass root of the suite
     * @param runners for each class in the suite, a {@link Runner}
     *
     * @throws InitializationError if the XSuite cannot be constructed
     */
    protected XSuite(final Class<?> klass, final List<Runner> runners) throws InitializationError {
        super(klass);
        this.runners = Collections.unmodifiableList(runners);
        if (hasParallelAnnotation(klass) && !runners.isEmpty()) {
            final ParallelFileScheduler scheduler = new ParallelFileScheduler();
            setScheduler(scheduler);
            this.parallelScheduler = scheduler;
        } else {
            this.parallelScheduler = null;
        }
    }

    /**
     * Returns true if any of the suite paths is an XQuery file or a directory containing at least one XQuery file.
     * Used to decide whether to start the DB before getRunners (so discovery can run for XQueryTestRunner).
     */
    private static boolean hasAnyXQueryFiles(final String[] suites) throws IOException {
        if (suites == null) {
            return false;
        }
        final java.util.function.Predicate<Path> isXQueryFile = XQueryFilenameFilter.asPredicate();
        for (final String suite : suites) {
            final Path path = Paths.get(suite);
            if (!Files.exists(path)) {
                continue;
            }
            if (Files.isDirectory(path)) {
                try (final Stream<Path> children = Files.list(path)) {
                    final boolean hasXq = children.anyMatch(p -> !Files.isDirectory(p) && isXQueryFile.test(p) && !"runTests.xql".equals(p.getFileName().toString()));
                    if (hasXq) {
                        return true;
                    }
                }
            } else if (isXQueryFile.test(path) && !"runTests.xql".equals(path.getFileName().toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts the embedded DB if not already started. Idempotent.
     * Called before getRunners when the suite contains XQuery files so discovery can use the DB.
     */
    private static void ensureExistDbStarted() throws Throwable {
        if (EXIST_EMBEDDED_SERVER_CLASS_INSTANCE == null) {
            EXIST_EMBEDDED_SERVER_CLASS_INSTANCE = newExistDbServer();
            EXIST_EMBEDDED_SERVER_CLASS_INSTANCE.startDb();
        }
    }

    /**
     * Get the runners for the suiteDirectories.
     *
     * @param suites/files the directories in the suite
     * @param parallel should a runner execute tests in parallel
     *
     * @throws InitializationError if the runners cannot be retrieved
     */
    private static List<Runner> getRunners(final String[] suites, final boolean parallel) throws InitializationError {
        if(suites == null) {
            return Collections.emptyList();
        }

        try {
            if (hasAnyXQueryFiles(suites)) {
                try {
                    ensureExistDbStarted();
                } catch (final Throwable t) {
                    throw new InitializationError(t);
                }
            }

            final List<Runner> runners = new ArrayList<>();
            for (final String suite : suites) {

                // if directory/file does not exist - throw an exception
                final Path path = Paths.get(suite);
                if (!Files.exists(path)) {
                    throw new InitializationError("XSuite does not exist: " + suite + ". path=" + path.toAbsolutePath());
                }

                if (Files.isDirectory(path)) {
                    // directory of files of test(s)
                    try (final Stream<Path> children = Files.list(path)) {
                        for(final Path child : children.collect(Collectors.toList())) {
                            if(!Files.isDirectory(child)) {
                                final Runner runner = getRunner(child, parallel);
                                if(runner != null) {
                                    runners.add(runner);
                                }
                            }
                        }
                    }
                } else {
                    // just a file of test(s)
                    runners.add(getRunner(path, parallel));
                }
            }

            return runners;
        } catch (final IOException e) {
            throw new InitializationError(e);
        }
    }

    private static @Nullable Runner getRunner(final Path path, final boolean parallel) throws InitializationError {
        if(XMLFilenameFilter.asPredicate().test(path)) {
            return new XMLTestRunner(path, parallel);
        } else if(XQueryFilenameFilter.asPredicate().test(path) && !"runTests.xql".equals(path.getFileName().toString())) {
            return new XQueryTestRunner(path, parallel);
        } else {
            return null;
        }
    }

    @Override
    protected List<Runner> getChildren() {
        return runners;
    }

    @Override
    protected Description describeChild(final Runner child) {
        return child.getDescription();
    }

    @Override
    protected void runChild(final Runner runner, final RunNotifier notifier) {
        if (parallelScheduler != null) {
            final ParallelFileScheduler parallel = parallelScheduler;
            parallel.prepareForRun(notifier);
            final Description runnerDesc = runner.getDescription();
            parallel.setCurrentRunner(runnerDesc);
            parallel.runnerStarted(runnerDesc, runner);
            try {
                runner.run(notifier);
            } finally {
                parallel.runnerFinished(runnerDesc);
                parallel.setCurrentRunner(null);
            }
        } else {
            runner.run(notifier);
        }
    }

    @Override
    protected Statement withBeforeClasses(final Statement statement) {
        // get @BeforeClass methods
        final List<FrameworkMethod> befores = getTestClass().getAnnotatedMethods(BeforeClass.class);

        // inject an eXist-db Server startup as though it were an @BeforeClass
        final Statement startExistDb = new StartExistDbStatement();

        return new RunXSuiteBefores(statement, startExistDb, befores, null);
    }

    @Override
    protected Statement withAfterClasses(final Statement statement) {
        // get @AfterClass methods
        final List<FrameworkMethod> afters = getTestClass().getAnnotatedMethods(AfterClass.class);

        // inject an eXist-db Server shutdown as though it were an @AfterClass
        final Statement stopExist = new StopExistDbStatement();

        return new RunXSuiteAfters(statement, stopExist, afters, null);
    }

    private static class RunXSuiteBefores extends Statement {
        private final Statement next;
        private final Object target;
        private final Statement beforeXSuite;
        private final List<FrameworkMethod> befores;

        public RunXSuiteBefores(final Statement next, final Statement beforeXSuite, final List<FrameworkMethod> befores, final Object target) {
            this.next = next;
            this.beforeXSuite = beforeXSuite;
            this.befores = befores;
            this.target = target;
        }

        @Override
        public void evaluate() throws Throwable {
            beforeXSuite.evaluate();
            for (final FrameworkMethod before : befores) {
                before.invokeExplosively(target);
            }
            next.evaluate();
        }
    }

    private static class RunXSuiteAfters extends Statement {
        private final Statement next;
        private final Object target;
        private final Statement afterXSuite;
        private final List<FrameworkMethod> afters;

        public RunXSuiteAfters(final Statement next, final Statement afterXSuite, final List<FrameworkMethod> afters, final Object target) {
            this.next = next;
            this.afterXSuite = afterXSuite;
            this.afters = afters;
            this.target = target;
        }

        @Override
        public void evaluate() throws Throwable {
            final List<Throwable> errors = new ArrayList<>();
            try {
                next.evaluate();
            } catch (final Throwable e) {
                errors.add(e);
            } finally {
                try {
                    afterXSuite.evaluate();
                } catch (final Throwable e) {
                    errors.add(e);
                }
                for (final FrameworkMethod after : afters) {
                    try {
                        after.invokeExplosively(target);
                    } catch (final Throwable e) {
                        errors.add(e);
                    }
                }
            }
            MultipleFailureException.assertEmpty(errors);
        }
    }

    private static class StartExistDbStatement extends Statement {
        @Override
        public void evaluate() throws Throwable {
            if (EXIST_EMBEDDED_SERVER_CLASS_INSTANCE != null) {
                return;
            }
            EXIST_EMBEDDED_SERVER_CLASS_INSTANCE = newExistDbServer();
            EXIST_EMBEDDED_SERVER_CLASS_INSTANCE.startDb();
        }
    }

    private static class StopExistDbStatement extends Statement {
        @Override
        public void evaluate() {
            if (EXIST_EMBEDDED_SERVER_CLASS_INSTANCE == null) {
                throw new IllegalStateException("EXIST_EMBEDDED_SERVER_CLASS_INSTANCE already stopped");
            }
            EXIST_EMBEDDED_SERVER_CLASS_INSTANCE.stopDb();
            EXIST_EMBEDDED_SERVER_CLASS_INSTANCE = null;
        }
    }

    static ExistEmbeddedServer EXIST_EMBEDDED_SERVER_CLASS_INSTANCE = null;

    static ExistEmbeddedServer newExistDbServer() {
        return new ExistEmbeddedServer(true, true);
    }

    /**
     * Runs child runners (test files) in parallel using a fixed thread pool.
     * Pool size is dynamic (processors and eXist broker count) with optional override.
     * Progress-based hang detection reports which file hung when no activity is seen.
     * All children share the same single eXist instance; tests must not rely on execution order.
     */
    private static final class ParallelFileScheduler implements RunnerScheduler {

        private static final String PARALLEL_THREADS_PROPERTY = "xsuite.parallel.threads";
        private static final String HANG_THRESHOLD_PROPERTY = "xsuite.hang.threshold.minutes";
        private static final String HANG_WATCHER_INTERVAL_PROPERTY = "xsuite.hang.watcher.interval.seconds";
        private static final int POOL_FLOOR = 2;
        private static final int POOL_CAP = 32;
        private static final double DEFAULT_HANG_THRESHOLD_MINUTES = 5.0;
        private static final int DEFAULT_WATCHER_INTERVAL_SECONDS = 30;

        private volatile ExecutorService executor;
        private final Object executorLock = new Object();

        private final ConcurrentHashMap<Description, Long> lastActivityByRunner = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Description, String> runnerLabelByDescription = new ConcurrentHashMap<>();
        private final ThreadLocal<Description> currentRunnerId = new ThreadLocal<>();
        private volatile RunNotifier notifier;
        private final AtomicBoolean notifierListenerAdded = new AtomicBoolean(false);
        private final AtomicBoolean hungReported = new AtomicBoolean(false);
        private volatile Thread watcherThread;

        private ExecutorService getOrCreateExecutor() {
            if (executor != null) {
                return executor;
            }
            synchronized (executorLock) {
                if (executor != null) {
                    return executor;
                }
                final int size = computePoolSize();
                executor = Executors.newFixedThreadPool(size);
                startWatcher();
                return executor;
            }
        }

        private static int computePoolSize() {
            final String override = System.getProperty(PARALLEL_THREADS_PROPERTY);
            if (override != null && !override.isEmpty()) {
                try {
                    return Math.max(POOL_FLOOR, Math.min(POOL_CAP, Integer.parseInt(override.trim())));
                } catch (final NumberFormatException ignored) {
                    // fall through to dynamic
                }
            }
            final int processors = Runtime.getRuntime().availableProcessors();
            int brokerMax = processors;
            if (EXIST_EMBEDDED_SERVER_CLASS_INSTANCE != null) {
                try {
                    final BrokerPool pool = EXIST_EMBEDDED_SERVER_CLASS_INSTANCE.getBrokerPool();
                    if (pool != null) {
                        brokerMax = pool.getMax();
                    }
                } catch (final Exception ignored) {
                    // use processors only
                }
            }
            return Math.max(POOL_FLOOR, Math.min(POOL_CAP, Math.min(processors, brokerMax)));
        }

        void prepareForRun(final RunNotifier notifier) {
            this.notifier = notifier;
            if (notifierListenerAdded.compareAndSet(false, true)) {
                notifier.addListener(new RunListener() {
                    @Override
                    public void testStarted(final Description description) {
                        recordActivity();
                    }

                    @Override
                    public void testFinished(final Description description) {
                        recordActivity();
                    }

                    @Override
                    public void testFailure(final Failure failure) {
                        recordActivity();
                    }

                    @Override
                    public void testAssumptionFailure(final Failure failure) {
                        recordActivity();
                    }

                    @Override
                    public void testIgnored(final Description description) {
                        recordActivity();
                    }

                    private void recordActivity() {
                        final Description runnerDesc = currentRunnerId.get();
                        if (runnerDesc != null) {
                            lastActivityByRunner.put(runnerDesc, System.currentTimeMillis());
                        }
                    }
                });
            }
        }

        void setCurrentRunner(final Description description) {
            if (description == null) {
                currentRunnerId.remove();
            } else {
                currentRunnerId.set(description);
            }
        }

        void runnerStarted(final Description runnerDesc, final Runner runner) {
            lastActivityByRunner.put(runnerDesc, System.currentTimeMillis());
            if (runner instanceof AbstractTestRunner) {
                runnerLabelByDescription.put(runnerDesc, ((AbstractTestRunner) runner).getSourcePath().toAbsolutePath().toString());
            } else {
                runnerLabelByDescription.put(runnerDesc, runnerDesc.getDisplayName());
            }
        }

        void runnerFinished(final Description runnerDesc) {
            lastActivityByRunner.remove(runnerDesc);
            runnerLabelByDescription.remove(runnerDesc);
        }

        private void startWatcher() {
            watcherThread = new Thread(this::runWatcher, "xsuite-hang-watcher");
            watcherThread.setDaemon(false);
            watcherThread.start();
        }

        private static double hangThresholdMinutes() {
            final String v = System.getProperty(HANG_THRESHOLD_PROPERTY);
            if (v == null || v.isEmpty()) {
                return DEFAULT_HANG_THRESHOLD_MINUTES;
            }
            try {
                return Math.max(0.001, Double.parseDouble(v.trim()));
            } catch (final NumberFormatException e) {
                return DEFAULT_HANG_THRESHOLD_MINUTES;
            }
        }

        private static int watcherIntervalSeconds() {
            final String v = System.getProperty(HANG_WATCHER_INTERVAL_PROPERTY);
            if (v == null || v.isEmpty()) {
                return DEFAULT_WATCHER_INTERVAL_SECONDS;
            }
            try {
                return Math.max(1, Integer.parseInt(v.trim()));
            } catch (final NumberFormatException e) {
                return DEFAULT_WATCHER_INTERVAL_SECONDS;
            }
        }

        private void runWatcher() {
            final long thresholdMs = (long) (hangThresholdMinutes() * 60 * 1000);
            final long intervalMs = watcherIntervalSeconds() * 1000L;
            while (!Thread.currentThread().isInterrupted() && executor != null && !executor.isShutdown()) {
                try {
                    Thread.sleep(intervalMs);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (hungReported.get()) {
                    break;
                }
                final long now = System.currentTimeMillis();
                for (final var e : lastActivityByRunner.entrySet()) {
                    if (now - e.getValue() > thresholdMs) {
                        if (hungReported.compareAndSet(false, true)) {
                            reportHung(e.getKey());
                            final ExecutorService exec = executor;
                            if (exec != null) {
                                exec.shutdownNow();
                            }
                        }
                        break;
                    }
                }
            }
        }

        private void reportHung(final Description runnerDesc) {
            final RunNotifier n = notifier;
            if (n == null) {
                return;
            }
            final String label = runnerLabelByDescription.getOrDefault(runnerDesc, runnerDesc.getDisplayName());
            final AssertionError err = new AssertionError(
                "Test file appears hung (no activity for " + hangThresholdMinutes() + " minutes): " + label);
            n.fireTestFailure(new Failure(runnerDesc, err));
        }

        @Override
        public void schedule(final Runnable childStatement) {
            getOrCreateExecutor().submit(childStatement);
        }

        @Override
        public void finished() {
            final ExecutorService exec = executor;
            if (exec == null) {
                return;
            }
            exec.shutdown();
            try {
                if (hungReported.get()) {
                    exec.awaitTermination(1, TimeUnit.MINUTES);
                } else {
                    exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                }
            } catch (final InterruptedException e) {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
            if (watcherThread != null) {
                watcherThread.interrupt();
            }
        }
    }
}
