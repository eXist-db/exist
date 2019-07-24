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

import org.exist.EXistException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLFilenameFilter;
import org.exist.util.XQueryFilenameFilter;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private final List<Runner> runners;

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

            final List<Runner> runners = new ArrayList<>();
            for (final String suite : suites) {

                // if directory/file does not exist - throw an exception
                final Path path = Paths.get(suite);
                if (!Files.exists(path)) {
                    throw new InitializationError("XSuite does not exist: " + suite + ". path=" + path.toAbsolutePath().toString());
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
        } else if(XQueryFilenameFilter.asPredicate().test(path) && !path.getFileName().toString().equals("runTests.xql")) {
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
        runner.run(notifier);
    }

    @Override
    public void run(final RunNotifier notifier) {
        try {
            super.run(notifier);
        } finally {
            ExistServer.stopServer();
        }
    }

    public static class ExistServer {
        private static final ExistEmbeddedServer EXIST_EMBEDDED_SERVER = new ExistEmbeddedServer(true, true);
        private static boolean running = false;

        public static ExistEmbeddedServer getRunningServer() throws EXistException, IOException, DatabaseConfigurationException {
            if(!running) {
                EXIST_EMBEDDED_SERVER.startDb();
                running = true;
            }
            return EXIST_EMBEDDED_SERVER;
        }

        public static void stopServer() {
            if(running) {
                EXIST_EMBEDDED_SERVER.stopDb();
                running = false;
            }
        }
    }
}
