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
package org.exist.xqdoc.ant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.exist.ant.AbstractXMLDBTask;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.util.FileUtils;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

public class XQDocTask extends AbstractXMLDBTask {

    private final static String XQUERY =
        "import module namespace xqdm=\"http://exist-db.org/xquery/xqdoc\";\n" +
        "import module namespace xdb=\"http://exist-db.org/xquery/xmldb\";\n" +
        "declare namespace xqdoc=\"http://www.xqdoc.org/1.0\"\n;" +
        "declare variable $uri external;\n" +
        "declare variable $name external;\n" +
        "declare variable $collection external;\n" +
        "declare variable $data external;\n" +
        "let $xml :=\n" +
            "if ($uri) then\n" +
            "   xqdm:scan(xs:anyURI($uri))\n" +
            "else\n" +
            "   xqdm:scan($data, $name)\n" +
        "let $moduleURI := $xml//xqdoc:module/xqdoc:uri\n" +
        "let $docName := concat(util:hash($moduleURI, 'MD5'), '.xml')\n" +
        "return\n" +
        "   xdb:store($collection, $docName, $xml, 'application/xml')";

    private String moduleURI = null;
    private boolean createCollection = false;
    private List<FileSet> fileSets = null;

    @Override
    public void execute() throws BuildException {
        registerDatabase();
        try {
            int p = uri.indexOf(XmldbURI.ROOT_COLLECTION);
            if (p == Constants.STRING_NOT_FOUND)
                throw new BuildException("invalid uri: '" + uri + "'");
            String baseURI = uri.substring(0, p);
            String path;
            if (p == uri.length() - 3)
                path = "";
            else
                path = uri.substring(p + 3);

            Collection root = null;
            if (createCollection)
            {
                root = DatabaseManager.getCollection(baseURI + XmldbURI.ROOT_COLLECTION, user, password);
                root = mkcol(root, baseURI, XmldbURI.ROOT_COLLECTION, path);
            } else
                root = DatabaseManager.getCollection(uri, user, password);

            EXistXQueryService service = root.getService(EXistXQueryService.class);
            Source source = new StringSource(XQUERY);
            service.declareVariable("collection", root.getName());
            service.declareVariable("uri", "");
            if (moduleURI != null) {
                service.declareVariable("uri", moduleURI);
                service.declareVariable("data", "");
                service.execute(source);
            } else {
                for(FileSet fileSet: fileSets) {
                    DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
                    scanner.scan();
                    String[] files = scanner.getIncludedFiles();
                    log("Found " + files.length + " files to upload.\n");

                    Path baseDir=scanner.getBasedir().toPath();
                    for (String s : files) {
                        Path file = baseDir.resolve(s);
                        log("Storing " + s + " ...\n");
                        byte[] data = read(file);
                        try {
                            service.declareVariable("name", FileUtils.fileName(file));
                            service.declareVariable("data", data);
                            service.execute(source);
                        } catch (XMLDBException e) {
                            String msg = "XMLDB exception caught: " + e.getMessage();
                            if (failonerror)
                                throw new BuildException(msg, e);
                            else
                                log(msg, e, Project.MSG_ERR);
                        }
                    }
                }
            }
        } catch (XMLDBException e) {
            String msg="XMLDB exception caught: " + e.getMessage();
            if(failonerror)
                throw new BuildException(msg,e);
            else
                log(msg,e, Project.MSG_ERR);
        }
    }

    public void setCreatecollection(boolean create) {
        this.createCollection = create;
    }

    public void setModuleuri(String uri) {
        this.moduleURI = uri;
    }

    public void addFileset(FileSet set) {
        if (fileSets == null)
            fileSets = new ArrayList<>();
        fileSets.add(set);
    }

    private byte[] read(Path file) throws BuildException {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BuildException("IO error while reading XQuery source: " + file.toAbsolutePath() +
                ": " + e.getMessage(), e);
        }
    }
}