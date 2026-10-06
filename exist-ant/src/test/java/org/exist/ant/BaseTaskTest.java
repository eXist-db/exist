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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URL;
import java.util.Arrays;

public class BaseTaskTest extends AbstractTaskTest {

    private static final String PROP_ANT_TEST_DATA_TASK_NAME  = "test.data.task.name";
    public String taskName;

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

    @Nullable
    @Override
    protected URL getBuildFile() {
        return getClass().getResource("base.xml");
    }

    @MethodSource("data") @ParameterizedTest
    void taskAvailable(String taskName) {
        initBaseTaskTest(taskName);
        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_TASK_NAME, taskName);

        buildFileRule.executeTarget("taskAvailable");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertTrue(Boolean.parseBoolean(result));
    }

    public void initBaseTaskTest(String taskName) {
        this.taskName = taskName;
    }
}
