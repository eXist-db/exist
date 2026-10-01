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
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface XQSuite {

    /**
     * @return the directories or files containing the tests, relative to the working directory
     */
    String[] value();
}
