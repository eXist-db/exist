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

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;

/**
 * A class annotated with {@link XQSuite}: one embedded database instance serves all of its files.
 */
final class SuiteDescriptor extends AbstractTestDescriptor {
    private final Class<?> suiteClass;
    private final Throwable discoveryFailure;
    private final boolean parallel;

    SuiteDescriptor(final UniqueId id, final Class<?> suiteClass, final boolean parallel, final Throwable discoveryFailure) {
        super(id, suiteClass.getSimpleName(), ClassSource.from(suiteClass));
        this.suiteClass = suiteClass;
        this.parallel = parallel;
        this.discoveryFailure = discoveryFailure;
    }

    Class<?> suiteClass() {
        return suiteClass;
    }

    /**
     * @return true if the files of the suite run concurrently
     */
    boolean parallel() {
        return parallel;
    }

    /**
     * @return why the suite's files could not be discovered, or null
     */
    Throwable discoveryFailure() {
        return discoveryFailure;
    }

    /**
     * A suite whose files could not be discovered is itself a test, which fails when run,
     * so that the problem is visible instead of the suite silently disappearing.
     */
    @Override
    public Type getType() {
        return discoveryFailure != null ? Type.CONTAINER_AND_TEST : Type.CONTAINER;
    }
}
