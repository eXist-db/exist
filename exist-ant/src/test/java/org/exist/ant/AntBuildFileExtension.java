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
package org.exist.ant;

import org.apache.tools.ant.BuildFileRule;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 {@link org.junit.jupiter.api.extension.Extension} adapter for Ant's
 * {@link BuildFileRule}, which ships only as a JUnit 4 {@code TestRule} with no
 * upstream JUnit 5 equivalent.
 *
 * <p>{@code BuildFileRule} only overrides {@code after()} (its {@code before()} is
 * the inherited no-op from {@link org.junit.rules.ExternalResource}; setup instead
 * happens explicitly via {@link #configureProject(String)}), so exposing that one
 * protected lifecycle hook as {@link AfterEachCallback} is sufficient to use it as
 * an instance-level {@code @RegisterExtension} field without needing the JUnit 4
 * migration-support shim.</p>
 */
public class AntBuildFileExtension extends BuildFileRule implements AfterEachCallback {

    @Override
    public void afterEach(final ExtensionContext context) {
        after();
    }
}
