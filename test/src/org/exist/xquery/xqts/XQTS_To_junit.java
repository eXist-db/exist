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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import junit.framework.Assert;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.xml.sax.InputSource;

/**
 * JUnit tests generator from XQTS Catalog.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XQTS_To_junit {

    private String sep = File.separator;

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
        
        broker = db.get(db.getSecurityManager().getSystemSubject());
        Assert.assertNotNull(broker);
        
        collection = broker.getOrCreateCollection(null, XQTS_case.XQTS_URI);
        Assert.assertNotNull(collection);
        broker.saveCollection(null, collection);
    }

    public void release() throws Exception {
        db.release(broker);
    }
        
    public void shutdown() throws Exception {
        release();
        
        db.shutdown();
        System.out.println("database was shutdownDB");
    }

    public void load() throws Exception {
        File folder = new File(XQTS_case.XQTS_folder);
        
        File[] files = folder.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().equals("CVS") 
                        || file.getName().equals("drivers")
                    )
                    continue; //ignore
                
                loadDirectory(file, collection);
            } else {
                if (file.getName().equals(".project"))
                    continue; //ignore
                
                loadFile(file, collection);
            }
        }
    }

    private void loadDirectory(File folder, Collection col) throws Exception {
        if (!(folder.exists() && folder.canRead()))
            return;
        
        Collection current = broker.getOrCreateCollection(null, col.getURI().append(folder.getName()));
        broker.saveCollection(null, current);
            
        File[] files = folder.listFiles();
        
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().equals("CVS"))
                    continue; //ignore
                
                loadDirectory(file, current);
            } else {
                loadFile(file, current);
            }
        }
    }

    private void loadFile(File file, Collection col) throws Exception {
        if (file.getName().endsWith(".html") 
                || file.getName().endsWith(".xsd")
//                || file.getName().equals("")
            )
            return;
        
        if (!(file.exists() && file.canRead()))
            return;
        
        TransactionManager txManager = db.getTransactionManager();
        try(final Txn txn = txManager.beginTransaction();
                final FileInputStream is = new FileInputStream(file)) {
            final MimeType mime = getMimeTable().getContentTypeFor( file.getName() );
            if (mime != null && mime.isXMLType()) {
                IndexInfo info = col.validateXMLResource(txn, broker, 
                        XmldbURI.create(file.getName()), 
                        new InputSource(new FileInputStream(file))
                    );
                //info.getDocument().getMetadata().setMimeType();
                col.store(txn, broker, info, new InputSource(is), false);
            } else {
                col.addBinaryResource(txn, broker,
                        XmldbURI.create(file.getName()),
                        is,
                        MimeType.BINARY_TYPE.getName(), file.length());
            }
            txManager.commit(txn);
        } catch (Exception e) {
            System.out.println("fail to load file "+file.getName());
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
        File file = ConfigurationHelper.getExistHome();
        File folder = new File(file.getAbsolutePath()+sep+"test"+sep+"src"+sep+"org"+sep+"exist"+sep+"xquery"+sep+"xqts"+sep);
        if (!folder.canRead()) {
            throw new IOException("XQTS junit tests folder unreadable.");
        }

        String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml') "+
            "return xs:string($XQTSCatalog/catalog:test-suite/@version)";

        XQuery xqs = broker.getXQueryService();
        
        Sequence results = xqs.execute(query, null, AccessContext.TEST);

        if (! results.isEmpty()) {
            String catalog = (String) results.itemAt(0).getStringValue();
            catalog = "XQTS_"+adoptString(catalog);
            File subfolder = new File(folder.getAbsolutePath()+sep+catalog);
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

    private boolean processGroups(String parentName, File folder, String _package_) throws Exception {

        String query = 
            "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')";

        if (parentName == null)
            query += "for $testGroup in $XQTSCatalog/catalog:test-suite/catalog:test-group";
        else
            query += "for $testGroup in $XQTSCatalog//catalog:test-group[@name = '"+parentName+"']/catalog:test-group";

        query += "\treturn xs:string($testGroup/@name)";

        XQuery xqs = broker.getXQueryService();
        Sequence results = xqs.execute(query, null, AccessContext.TEST);

        if (!results.isEmpty()) {
            File subfolder;
            String subPackage;

            //if (parentName == null) {
                //subfolder = folder;
                //subPackage = _package_;
            //} else {
                //subfolder = new File(folder.getAbsolutePath()+sep+parentName);
                //subPackage = _package_+"."+adoptString(parentName);
            //}

            BufferedWriter allTests = startAllTests(folder, _package_);

            boolean first = true;
            if (testCases(parentName, folder, _package_)) {
                if (!first)
                    allTests.write(",\n");
                else
                    first = false;
                allTests.write("\t\tC_"+adoptString(parentName)+".class");
            }

            for (int i = 0; i < results.getItemCount(); i++) {
                String groupName = results.itemAt(i).getStringValue();
                subfolder = new File(folder.getAbsolutePath()+sep+groupName);
                subPackage = _package_+"."+adoptString(groupName);

                if (processGroups(groupName, subfolder, subPackage)) { 
                    if (!first)
                        allTests.write(",\n");
                    else
                        first = false;
                    allTests.write("\t\torg.exist.xquery.xqts"+subPackage+".AllTests.class");
                } else if (testCases(groupName, folder, _package_)) {
                    if (!first)
                        allTests.write(",\n");
                    else
                        first = false;
                    allTests.write("\t\tC_"+adoptString(groupName)+".class");
                }
            }
            endAllTests(allTests);
            return true;
       	}
        return false;
    }

    private BufferedWriter startAllTests(File folder, String _package_) throws IOException {
        folder.mkdirs();
        File jTest = new File(folder.getAbsolutePath()+sep+"AllTests.java");
        FileWriter fstream = new FileWriter(jTest.getAbsoluteFile());
        BufferedWriter out = new BufferedWriter(fstream);

        out.write("package org.exist.xquery.xqts"+_package_+";\n\n" +
            "import org.junit.runner.RunWith;\n" +
            "import org.junit.runners.Suite;\n\n" +
            "@RunWith(Suite.class)\n" +
            "@Suite.SuiteClasses({\n");
        return out;
    }

    private void endAllTests(BufferedWriter out) throws IOException {
        out.write("\n})\n\n"+
            "public class AllTests {\n\n" +
        "}");
        out.close();
    }

    private boolean testCases(String testGroup, File folder, String _package_) throws Exception {

        String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')"+
            "for $testGroup in $XQTSCatalog//catalog:test-group[@name = '"+testGroup+"']/catalog:test-case"+
            "\treturn xs:string($testGroup/@name)";

        XQuery xqs = broker.getXQueryService();
        Sequence results = xqs.execute(query, null, AccessContext.TEST);

        if (!results.isEmpty()) {
            folder.mkdirs();
            File jTest = new File(folder.getAbsolutePath()+sep+"C_"+adoptString(testGroup)+".java");
            FileWriter fstream = new FileWriter(jTest.getAbsoluteFile());
            BufferedWriter out = new BufferedWriter(fstream);

            out.write("package org.exist.xquery.xqts"+_package_+";\n\n"+
                "import org.exist.xquery.xqts.XQTS_case;\n" +
                //"import static org.junit.Assert.*;\n" +
                "import org.junit.Test;\n\n" +
                "public class C_"+adoptString(testGroup)+" extends XQTS_case {\n" +
                "\tprivate String testGroup = \""+testGroup+"\";\n\n");

            for (int i = 0; i < results.getItemCount(); i++) {
                String caseName = results.itemAt(i).getStringValue();
                out.write("\t/* "+caseName+" */" +
                    "\t@Test\n" +
                    "\tpublic void test_"+adoptString(caseName)+"() {\n" +
                    "\tgroupCase(testGroup, \""+caseName+"\");"+
                    "\t}\n\n");
            }
            out.write("}");
            out.close();
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
