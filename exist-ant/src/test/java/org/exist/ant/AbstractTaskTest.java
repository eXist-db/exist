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
import org.apache.tools.ant.Project;
import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.Before;
import org.junit.Rule;

import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;

public abstract class AbstractTaskTest {

    protected static final String PROP_ANT_ADMIN_USER = "admin.user";
    protected static final String PROP_ANT_ADMIN_PASSWORD = "admin.password";

    protected static final String PROP_ANT_TEST_DATA_RESULT = "test.data.result";

    @Rule
    public BuildFileRule buildFileRule = new BuildFileRule();

    @Rule
    public final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Before
    public void setup() throws URISyntaxException {
        final URL buildFileUrl = getBuildFile();
        assertNotNull(buildFileUrl);

        final Path buildFile = Paths.get(buildFileUrl.toURI());
        buildFileRule.configureProject(buildFile.toAbsolutePath().toString());
        final Path path = Paths.get(getClass().getResource("user.xml").toURI());
        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_ADMIN_USER, TestUtils.ADMIN_DB_USER);
        project.setProperty(PROP_ANT_ADMIN_PASSWORD, TestUtils.ADMIN_DB_PWD);
    }

    protected abstract @Nullable URL getBuildFile();
}
