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

import org.apache.tools.ant.Project;
import org.exist.TestUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.net.URL;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class UserTaskTest extends AbstractTaskTest {

    private static final String PROP_ANT_TEST_DATA_USER =  "test.data.user";
    private static final String PROP_ANT_TEST_DATA_PASSWORD = "test.data.password";
    private static final String PROP_ANT_TEST_DATA_PASSWORD_CHANGED = "test.data.password.changed";

    @Nullable
    @Override
    protected URL getBuildFile() {
        return getClass().getResource("user.xml");
    }

    @Test
    public void addUser() {
        final String testUsername = "test-user-1";
        final String testPassword = "test-user-1-password";

        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_USER, testUsername);
        project.setProperty(PROP_ANT_TEST_DATA_PASSWORD, testPassword);

        buildFileRule.executeTarget("addUser");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertThat(result, containsString(testUsername));
    }

    @Test
    public void listUser() {
        final Project project = buildFileRule.getProject();

        buildFileRule.executeTarget("listUser");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertThat(result, containsString(TestUtils.ADMIN_DB_USER));
        assertThat(result, containsString(TestUtils.GUEST_DB_USER));
    }

    @Test
    public void changePassword() {
        final String testUsername = "test-user-1";
        final String testPassword = "test-user-1-password";
        final String testPasswordChanged = "test-user-1-password-changed";

        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_USER, testUsername);
        project.setProperty(PROP_ANT_TEST_DATA_PASSWORD, testPassword);
        project.setProperty(PROP_ANT_TEST_DATA_PASSWORD_CHANGED, testPasswordChanged);

        buildFileRule.executeTarget("changePassword");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertEquals("<changed>" + testPasswordChanged + "</changed>", result.trim());
    }

    @Test
    public void removeUser() {
        final String testUsername = "test-user-1";
        final String testPassword = "test-user-1-password";

        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_USER, testUsername);
        project.setProperty(PROP_ANT_TEST_DATA_PASSWORD, testPassword);

        buildFileRule.executeTarget("removeUser");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertThat(result, not(containsString(testUsername)));
    }
}
