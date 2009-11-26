package org.exist.xqdoc.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.exist.ant.AbstractXMLDBTask;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XQueryService;
import org.exist.xquery.Constants;
import org.exist.xquery.value.Base64Binary;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        "   xdb:store($collection, $docName, $xml, 'text/xml')";

    private String moduleURI = null;
    private boolean createCollection = false;
    private List<FileSet> fileSets = null;

    @Override
    public void execute() throws BuildException {
        registerDatabase();
        try {
            int p = uri.indexOf(DBBroker.ROOT_COLLECTION);
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
                root = DatabaseManager.getCollection(baseURI + DBBroker.ROOT_COLLECTION, user, password);
                root = mkcol(root, baseURI, DBBroker.ROOT_COLLECTION, path);
            } else
                root = DatabaseManager.getCollection(uri, user, password);

            XQueryService service = (XQueryService) root.getService("XQueryService", "1.0");
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

                    File baseDir=scanner.getBasedir();
                    for (int i = 0; i < files.length; i++) {
                        File file = new File(baseDir , files[i]);
                        log("Storing " + files[i] + " ...\n");
                        byte[] data = read(file);
                        try {
                            service.declareVariable("name", file.getName());
                            service.declareVariable("data", data);
                            service.execute(source);
                        } catch (XMLDBException e) {
                            String msg="XMLDB exception caught: " + e.getMessage();
                            if(failonerror)
                                throw new BuildException(msg,e);
                            else
                                log(msg,e, Project.MSG_ERR);
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
            fileSets = new ArrayList<FileSet>();
        fileSets.add(set);
    }

    private byte[] read(File file) throws BuildException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[4096];
            int l;
            while ((l = fis.read(buf)) > -1) {
                bos.write(buf, 0, l);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BuildException("IO error while reading XQuery source: " + file.getAbsolutePath() +
                ": " + e.getMessage(), e);
        }
    }
}