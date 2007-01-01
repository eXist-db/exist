/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.performance.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.exist.performance.Runner;
import org.exist.performance.TestResultWriter;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class AntTask extends Task {

    private String source;
    private String outputFile;

    private String group = null;

    public void execute() throws BuildException {
        File src = new File(source);
        if (!(src.canRead() && src.isFile()))
            throw new BuildException("Cannot read input file: " + source);

        File outFile = new File(outputFile);

        Runner runner = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(src);

            TestResultWriter writer = new TestResultWriter(outFile.getAbsolutePath());
            runner = new Runner(doc.getDocumentElement(), writer);
            runner.run(group);
        } catch (Exception e) {
            throw new BuildException("ERROR: " + e.getMessage(), e);
        } finally {
            if (runner != null)
                runner.shutdown();
        }
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
