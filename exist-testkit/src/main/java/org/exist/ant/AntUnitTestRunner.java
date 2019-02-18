/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009-2012 The eXist-db Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.ant;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author jimfuller
 */
public class AntUnitTestRunner {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    @Test
    public void testAntUnit() throws BuildException {
        final Path buildFile = Paths.get("exist-core/src/test/resources/ant/build.xml");
        final Project p = new  Project();
        p.setUserProperty("ant.file", buildFile.toAbsolutePath().toString());
        final DefaultLogger consoleLogger = new DefaultLogger();
        consoleLogger.setErrorPrintStream(System.err);
        consoleLogger.setOutputPrintStream(System.out);
        consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
        p.addBuildListener(consoleLogger);
        try {
            p.fireBuildStarted();
            p.init();
            ProjectHelper helper = ProjectHelper.getProjectHelper();
            p.addReference("ant.projectHelper", helper);
            helper.parse(p, buildFile.toFile());
            p.executeTarget(p.getDefaultTarget());
            p.fireBuildFinished(null);
        } catch (final BuildException e) {
            p.fireBuildFinished(e);
            throw e;
        }
    }

}
