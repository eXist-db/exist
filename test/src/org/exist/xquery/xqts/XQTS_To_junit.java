/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2013 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.xqts;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import junit.framework.Assert;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;

/**
 * JUnit tests generator from XQTS Catalog.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XQTS_To_junit {

    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        XQTS_To_junit convertor = new XQTS_To_junit();
        try {
            convertor.startup();
            convertor.load();
            convertor.create();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            convertor.shutdown();
        }
    }

    private BrokerPool db = null;
    private DBBroker broker = null;
    private Collection collection = null;

    public void startup() throws Exception {
        Configuration configuration = new Configuration();
        BrokerPool.configure(1, 10, configuration);
        init();
    }

    public void init() throws Exception {
        db = BrokerPool.getInstance();
        
        broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()));
        Assert.assertNotNull(broker);
        
        collection = broker.getOrCreateCollection(null, XQTS_case.XQTS_URI);
        Assert.assertNotNull(collection);
        broker.saveCollection(null, collection);
    }

    public void release() throws Exception {
        if(broker != null) {
            broker.close();
        }
    }
        
    public void shutdown() throws Exception {
        release();
        
        db.shutdown();
        System.out.println("database was shutdownDB");
    }

    public void load() throws Exception {
        final Path folder = Paths.get(XQTS_case.XQTS_folder);
        
        final List<Path> files = FileUtils.list(folder);
        for (final Path file : files) {
            final String fileName = FileUtils.fileName(file);
            if (Files.isDirectory(file)) {
                if (fileName.equals("CVS") || fileName.equals("drivers")) {
                    continue; //ignore
                }
                
                loadDirectory(file, collection);
            } else {
                if (fileName.equals(".project")) {
                    continue; //ignore
                }
                
                loadFile(file, collection);
            }
        }
    }

    private void loadDirectory(final Path folder, final Collection col) throws Exception {
        if (!(Files.exists(folder) && Files.isReadable(folder))) {
            return;
        }

        final Collection current = broker.getOrCreateCollection(null, col.getURI().append(FileUtils.fileName(folder)));
        broker.saveCollection(null, current);
            
        final List<Path> files = FileUtils.list(folder);
        
        if (files == null) {
            return;
        }
        
        for (final Path file : files) {
            if (Files.isDirectory(file)) {
                if (FileUtils.fileName(file).equals("CVS")) {
                    continue; //ignore
                }
                
                loadDirectory(file, current);
            } else {
                loadFile(file, current);
            }
        }
    }

    private void loadFile(final Path file, final Collection col) throws Exception {
        final String fileName = FileUtils.fileName(file);

        if (fileName.endsWith(".html") || fileName.endsWith(".xsd")) {
            return;
        }
        
        if (!(Files.exists(file) && Files.isReadable(file))) {
            return;
        }
        
        final TransactionManager txManager = db.getTransactionManager();
        try(final Txn txn = txManager.beginTransaction()) {
            final MimeType mime = getMimeTable().getContentTypeFor(FileUtils.fileName(file));
            if (mime != null && mime.isXMLType()) {
                final IndexInfo info = col.validateXMLResource(txn, broker,
                        XmldbURI.create(FileUtils.fileName(file)),
                        new FileInputSource(file)
                    );
                //info.getDocument().getMetadata().setMimeType();
                col.store(txn, broker, info, new FileInputSource(file));
            } else {
                try(final InputStream is = Files.newInputStream(file)) {
                    col.addBinaryResource(txn, broker,
                            XmldbURI.create(FileUtils.fileName(file)),
                            is,
                            MimeType.BINARY_TYPE.getName(), FileUtils.sizeQuietly(file));
                }
            }
            txManager.commit(txn);
        } catch (Exception e) {
            System.out.println("fail to load file "+ FileUtils.fileName(file));
            e.printStackTrace();
        }
    }

    private MimeTable mtable = null;
    private MimeTable getMimeTable() {
        if ( mtable == null ) {
            mtable = MimeTable.getInstance();
        }
        return mtable;
    }

    public void create() throws Exception {
        Optional<Path> file = ConfigurationHelper.getExistHome();
        Path folder = FileUtils.resolve(file, "test/src/org/exist/xquery/xqts");
        if (!Files.isReadable(folder)) {
            throw new IOException("XQTS junit tests folder unreadable.");
        }

        String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml') "+
            "return xs:string($XQTSCatalog/catalog:test-suite/@version)";

        XQuery xqs = db.getXQueryService();
        
        Sequence results = xqs.execute(broker, query, null);

        if (! results.isEmpty()) {
            String catalog = (String) results.itemAt(0).getStringValue();
            catalog = "XQTS_"+adoptString(catalog);
            Path subfolder = folder.resolve(catalog);
            processGroups(null, subfolder, "."+catalog);
        }
    }

//    private void loadXQTS() {
//        File buildFile = new File("webapp/xqts/build.xml");
//        //File xqtsFile = new File("webapp/xqts/build.xml");
//        Project p = new Project();
//        p.setUserProperty("ant.file", buildFile.getAbsolutePath());
//        p.setUserProperty("config.basedir", "../../"+XQTS_case.XQTS_folder);
//        DefaultLogger consoleLogger = new DefaultLogger();
//        consoleLogger.setErrorPrintStream(System.err);
//        consoleLogger.setOutputPrintStream(System.out);
//        consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
//        p.addBuildListener(consoleLogger);
//
//        try {
//            p.fireBuildStarted();
//            p.init();
//            ProjectHelper helper = ProjectHelper.getProjectHelper();
//            p.addReference("ant.projectHelper", helper);
//            helper.parse(p, buildFile);
//            p.executeTarget("store");
//            p.fireBuildFinished(null);
//            Thread.sleep(60*1000);
//        } catch (BuildException e) {
//            p.fireBuildFinished(e);
//        } catch (InterruptedException e) {
//            //Nothing to do
//        }
//    }

    private boolean processGroups(String parentName, Path folder, String _package_) throws Exception {

        String query = 
            "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')";

        if (parentName == null)
            query += "for $testGroup in $XQTSCatalog/catalog:test-suite/catalog:test-group";
        else
            query += "for $testGroup in $XQTSCatalog//catalog:test-group[@name = '"+parentName+"']/catalog:test-group";

        query += "\treturn xs:string($testGroup/@name)";

        XQuery xqs = db.getXQueryService();
        Sequence results = xqs.execute(broker, query, null);

        if (!results.isEmpty()) {
            Path subfolder;
            String subPackage;

            //if (parentName == null) {
                //subfolder = folder;
                //subPackage = _package_;
            //} else {
                //subfolder = new File(folder.getAbsolutePath()+sep+parentName);
                //subPackage = _package_+"."+adoptString(parentName);
            //}

            try(final Writer allTests = startAllTests(folder, _package_)) {
                boolean first = true;
                if (testCases(parentName, folder, _package_)) {
                    if (!first)
                        allTests.write(",\n");
                    else
                        first = false;
                    allTests.write("\t\tC_" + adoptString(parentName) + ".class");
                }

                for (int i = 0; i < results.getItemCount(); i++) {
                    String groupName = results.itemAt(i).getStringValue();
                    subfolder = folder.resolve(groupName);
                    subPackage = _package_ + "." + adoptString(groupName);

                    if (processGroups(groupName, subfolder, subPackage)) {
                        if (!first)
                            allTests.write(",\n");
                        else
                            first = false;
                        allTests.write("\t\torg.exist.xquery.xqts" + subPackage + ".AllTests.class");
                    } else if (testCases(groupName, folder, _package_)) {
                        if (!first)
                            allTests.write(",\n");
                        else
                            first = false;
                        allTests.write("\t\tC_" + adoptString(groupName) + ".class");
                    }
                }
                endAllTests(allTests);
            }
            return true;
       	}
        return false;
    }

    private Writer startAllTests(Path folder, String _package_) throws IOException {
        Files.createDirectories(folder);
        Path jTest = folder.resolve("AllTests.java");
        Writer out = Files.newBufferedWriter(jTest);

        out.write("package org.exist.xquery.xqts" + _package_ + ";\n\n" +
                "import org.junit.runner.RunWith;\n" +
                "import org.junit.runners.Suite;\n\n" +
                "@RunWith(Suite.class)\n" +
                "@Suite.SuiteClasses({\n");
        return out;
    }

    private void endAllTests(final Writer out) throws IOException {
        out.write("\n})\n\n"+
            "public class AllTests {\n\n" +
        "}");
    }

    private boolean testCases(String testGroup, Path folder, String _package_) throws Exception {

        String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')"+
            "for $testGroup in $XQTSCatalog//catalog:test-group[@name = '"+testGroup+"']/catalog:test-case"+
            "\treturn xs:string($testGroup/@name)";

        XQuery xqs = db.getXQueryService();
        Sequence results = xqs.execute(broker, query, null);

        if (!results.isEmpty()) {
            Files.createDirectories(folder);
            Path jTest = folder.resolve("C_"+adoptString(testGroup)+".java");
            try(final Writer out = Files.newBufferedWriter(jTest)) {

                out.write("package org.exist.xquery.xqts" + _package_ + ";\n\n" +
                        "import org.exist.xquery.xqts.XQTS_case;\n" +
                        //"import static org.junit.Assert.*;\n" +
                        "import org.junit.Test;\n\n" +
                        "public class C_" + adoptString(testGroup) + " extends XQTS_case {\n" +
                        "\tprivate String testGroup = \"" + testGroup + "\";\n\n");

                for (int i = 0; i < results.getItemCount(); i++) {
                    String caseName = results.itemAt(i).getStringValue();
                    out.write("\t/* " + caseName + " */" +
                            "\t@Test\n" +
                            "\tpublic void test_" + adoptString(caseName) + "() {\n" +
                            "\tgroupCase(testGroup, \"" + caseName + "\");" +
                            "\t}\n\n");
                }
                out.write("}");
            }
            return true;
        }
        return false;
    }

    private String adoptString(String caseName) {
        String result = caseName.replace("-", "_");
        result = result.replace(".", "_");
        return result;
    }
}
