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
import org.apache.tools.ant.ProjectHelper;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;

/**
 * Loads an Ant build file for a test and runs its targets.
 * <p>
 * Ant's own test helper, {@code BuildFileRule}, only exists as a JUnit 4 rule, so this does
 * the part of it that the tests need: configure a {@link Project} from a build file, execute
 * targets, and afterwards run the build file's {@code tearDown} target if it has one.
 */
public class AntBuildFileExtension implements AfterEachCallback {
    private Project project;

    public void configureProject(final String buildFile) {
        final File antFile = new File(buildFile);
        project = new Project();
        project.init();
        project.setUserProperty("ant.file", antFile.getAbsolutePath());
        ProjectHelper.configureProject(project, antFile);
    }

    public void executeTarget(final String target) {
        project.executeTarget(target);
    }

    public Project getProject() {
        return project;
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        if (project != null && project.getTargets().containsKey("tearDown")) {
            project.executeTarget("tearDown");
        }
    }
}
