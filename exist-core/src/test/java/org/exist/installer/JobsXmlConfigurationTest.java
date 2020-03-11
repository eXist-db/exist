/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.installer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Make sure that the configuration inside <code>installer/jobs.xml</code>
 * references existing file names only
 * 
 * @author <a href="mailto:ohumbel@gmail.com">Otmar Humbel</a>
 */
public class JobsXmlConfigurationTest {

    private static final String INSTALL_PATH_VAR = "$INSTALL_PATH";

    @Test
    public void testIntegrityOfJobsXml() throws URISyntaxException, IOException {
        String repositoryRoot = getRepositoryRoot();
        Path jobsXmlPath = getJobsXmlPath(repositoryRoot);
        try(Stream<String> stream = Files.readAllLines(jobsXmlPath).stream()) {
            stream.forEach(line -> assertInstallationPath(line, repositoryRoot));
        }
    }

    private String getRepositoryRoot() throws URISyntaxException {
        Class<? extends JobsXmlConfigurationTest> thisClass = getClass();
        String resourceName = thisClass.getSimpleName().concat(".class");
        URL resourceURL = thisClass.getResource(resourceName);
        assertNotNull(resourceName + " not found", resourceURL);
        File file = new File(resourceURL.toURI());
        String resourcePath = file.getAbsolutePath().replace('\\', '/');
        int testRootIndex = resourcePath.indexOf("/exist-core/target/test-classes/");
        if (testRootIndex < 0) {
            testRootIndex = resourcePath.indexOf("/test/classes/"); // inside IDE
        }
        assertTrue("test root not found inside " + resourcePath, testRootIndex > 0);
        return resourcePath.substring(0, testRootIndex);
    }

    private Path getJobsXmlPath(String repositoryRoot) {
        Path jobsXmlPath = Paths.get(repositoryRoot, "installer", "jobs.xml");
        assertTrue(jobsXmlPath + " not found", Files.exists(jobsXmlPath));
        assertTrue("cannot read " + jobsXmlPath, Files.isReadable(jobsXmlPath));
        return jobsXmlPath;
    }

    private void assertInstallationPath(String line, String repoRoot) {
        if (line.contains(INSTALL_PATH_VAR)) {
            int startIndex = line.indexOf(INSTALL_PATH_VAR) + INSTALL_PATH_VAR.length();
            int closingIndex = line.indexOf("</", startIndex);
            if (closingIndex > startIndex) {
                String fileName = repoRoot.concat(line.substring(startIndex, closingIndex)).replace('\\', '/');
                Path filePath = Paths.get(fileName);
                // intentionally exclude conf.xslt which is not present in the repository
                if (!fileName.endsWith("conf.xslt")) {
                    assertTrue(filePath + " not found", Files.exists(filePath));
                }
            }
        }
    }

}
