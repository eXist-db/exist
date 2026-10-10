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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a suite of XQSuite and XML tests, to be run by the {@link XQSuiteTestEngine}.
 * <p>
 * The class itself is just a marker, for example:
 * <pre>{@code
 * @XQSuite("src/test/xquery/maps")
 * public class MapTests {
 * }
 * }</pre>
 * Static methods of the class annotated with {@code @BeforeAll} and {@code @AfterAll}
 * run around the whole suite, after the embedded database has started and before it stops.
 * <p>
 * The test files of a suite run one after the other unless {@link #parallel()} is set. In either
 * case a test file that stops reporting for too long is failed instead of hanging the build, see
 * {@link XQSuiteSettings#HANG_THRESHOLD_MINUTES}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface XQSuite {

    /**
     * @return the directories or files containing the tests, relative to the working directory
     */
    String[] value();

    /**
     * Run the test files of the suite concurrently, against the one embedded database, with at most
     * {@link XQSuiteSettings#PARALLELISM} files at a time. The tests within a file always run one
     * after the other.
     * <p>
     * Only set this if the files do not depend on or disturb each other's data:
     * <ul>
     * <li>A file that stores data uses a collection name that no other file uses, derived from its own
     * file name (for example {@code test-guest} for {@code guest.xql}), and does the same for any other
     * global name it creates, such as index fields or accounts. The name is declared once, as a variable
     * of the file, and used everywhere else; only the URI of a static import and the values of annotations
     * have to be literals.</li>
     * <li>It does not rely on database-wide state such as accounts or the contents of {@code /db}.</li>
     * <li>Its queries are scoped to its own collection. An index function such as {@code range:field-eq}
     * or {@code ft:query} called without a context searches every collection that has the index, so it
     * also finds the data of another file that happens to run at the same moment. A second collection
     * with the same index and other data in the setUp of the file (a "bystander") makes such a query
     * fail every time and not only occasionally.</li>
     * </ul>
     *
     * @return true to run the files of the suite concurrently
     */
    boolean parallel() default false;

    /**
     * Marks a suite that only exists to be run by a test of the engine itself (a file that hangs, for
     * example), with {@link XQSuiteSettings#FIXTURES} set. Any other run, however it selects classes (a
     * {@code -Dtest} wildcard that also matches nested classes, a package or classpath scan of an IDE),
     * skips it, so that it is not reported as a failing suite of the module.
     *
     * @return true for a suite that is only run by the test that sets it up
     */
    boolean fixture() default false;
}
