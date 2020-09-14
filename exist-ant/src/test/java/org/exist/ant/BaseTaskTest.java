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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class BaseTaskTest extends AbstractTaskTest {

    @Parameters
    public static Iterable<? extends Object> data() {
        return Arrays.asList(
                UserTask.class.getSimpleName(),
                UserPasswordTask.class.getSimpleName(),
                AddUserTask.class.getSimpleName(),
                RemoveUserTask.class.getSimpleName(),
                AddGroupTask.class.getSimpleName(),
                RemoveGroupTask.class.getSimpleName(),
                ListUsersTask.class.getSimpleName(),
                ListGroupsTask.class.getSimpleName(),
                XMLDBCreateTask.class.getSimpleName(),
                XMLDBListTask.class.getSimpleName(),
                XMLDBExistTask.class.getSimpleName(),
                XMLDBStoreTask.class.getSimpleName(),
                XMLDBCopyTask.class.getSimpleName(),
                XMLDBMoveTask.class.getSimpleName(),
                XMLDBExtractTask.class.getSimpleName(),
                XMLDBRemoveTask.class.getSimpleName(),
                BackupTask.class.getSimpleName(),
                RestoreTask.class.getSimpleName(),
                ChmodTask.class.getSimpleName(),
                ChownTask.class.getSimpleName(),
                LockResourceTask.class.getSimpleName(),
                XMLDBXPathTask.class.getSimpleName(),
                XMLDBXQueryTask.class.getSimpleName(),
                XMLDBXUpdateTask.class.getSimpleName(),
                XMLDBShutdownTask.class.getSimpleName()
        );
    }

    @Parameter
    public String taskName;

    private static final String PROP_ANT_TEST_DATA_TASK_NAME  = "test.data.task.name";

    @Nullable
    @Override
    protected URL getBuildFile() {
        return getClass().getResource("base.xml");
    }

    @Test
    public void taskAvailable() {
        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_TASK_NAME, taskName);

        buildFileRule.executeTarget("taskAvailable");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertTrue(Boolean.parseBoolean(result));
    }
}
