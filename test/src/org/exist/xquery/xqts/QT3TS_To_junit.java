/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012-2013 The eXist Project
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

import java.io.*;

import junit.framework.Assert;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.NodeProxy;
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * JUnit tests generator from QT3 test suite catalog.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class QT3TS_To_junit {

    private static final String sep = File.separator;
    
    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        QT3TS_To_junit convertor = new QT3TS_To_junit();
        try {
            convertor.startup();
//            convertor.create();
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
		
		collection = broker.getOrCreateCollection(null, QT3TS_case.QT3_URI);
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
        File folder = new File(QT3TS_case.FOLDER);
        
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
    	//System.out.println("******* loadDirectory "+folder.getName());
    	if (!(folder.exists() && folder.canRead()))
    		return;
    	
    	Collection current = broker.getOrCreateCollection(null, col.getURI().append(folder.getName()));
		broker.saveCollection(null, current);
    		
        File[] files = folder.listFiles();
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
    	//System.out.println("******* loadFile "+file.getName());
    	
    	if (file.getName().endsWith(".html") 
    			|| file.getName().endsWith(".xsd")
    			|| file.getName().equals("badxml.xml")
    			|| file.getName().equals("BCisInvalid.xml")
    			|| file.getName().equals("InvalidUmlaut.xml")
    			|| file.getName().equals("InvalidXMLId.xml")
    			|| file.getName().equals("invalid-xml.xml")
			)
    		return;
    	
    	if (!(file.exists() && file.canRead()))
    		return;
    	
        MimeType mime = getMimeTable().getContentTypeFor( file.getName() );
        if (mime != null && mime.isXMLType()) {
        	IndexInfo info = col.validateXMLResource(null, broker, 
            		XmldbURI.create(file.getName()), 
            		new InputSource(new FileInputStream(file))
            	);
            //info.getDocument().getMetadata().setMimeType();
        	
        	FileInputStream is = new FileInputStream(file);
        	try {
        	    col.store(null, broker, info, new InputSource(is), false);
        	} finally {
        	    is.close();
        	}
        } else {
        	TransactionManager txManager = db.getTransactionManager();
        	Txn txn = txManager.beginTransaction();

            try {
                FileInputStream is = new FileInputStream(file);
                try {
                    col.addBinaryResource(txn, broker,
                            XmldbURI.create(file.getName()),
                            is,
                            MimeType.BINARY_TYPE.getName(), file.length());
                } finally {
                    is.close();
                }
                txManager.commit(txn);
            } finally {
                txManager.close(txn);
            }
        }
    	//System.out.println(file);
    }

    private MimeTable mtable = null;
    private MimeTable getMimeTable() {
        if ( mtable == null ) {
        	mtable = MimeTable.getInstance();
        }
        return mtable;
    }
    
    String tsQuery = 
		"declare namespace qt='http://www.w3.org/2010/09/qt-fots-catalog'; " +
		"let $catalog := xmldb:document('/db/QT3/catalog.xml') " +
		"return $catalog//qt:test-set";

    public void create() throws Exception {
        File existHome = ConfigurationHelper.getExistHome();
        File src = new File(existHome.getAbsolutePath()+sep+"test"+sep+"src"+sep+"org"+sep+"exist"+sep+"xquery"+sep+"xqts"+sep+"qt3"+sep);
        if (!src.exists())
        	src.mkdirs();
        
        if (!src.canRead()) {
            throw new IOException("QT3 junit tests folder unreadable.");
        }

        XQuery xqs = broker.getXQueryService();
        
        Sequence results = xqs.execute(tsQuery, null, AccessContext.TEST);
        
        for (NodeProxy p : results.toNodeSet()) {
        	NamedNodeMap attrs = p.getNode().getAttributes();
        	String name = attrs.getNamedItem("name").getNodeValue();
        	String file = attrs.getNamedItem("file").getNodeValue();
        	
        	processSet(src, name, file);
        }
    }

    private void processSet(File src, String name, String file) throws Exception {
        String tsQuery = 
    		"declare namespace qt='http://www.w3.org/2010/09/qt-fots-catalog'; " +
    		"let $catalog := xmldb:document('/db/QT3/"+file+"') " +
    		"return $catalog//qt:test-case";

        XQuery xqs = broker.getXQueryService();
        Sequence results = xqs.execute(tsQuery, null, AccessContext.TEST);
        
        testCases(src, file, name, results);
	}
    
//<test-case name="fn-absint1args-2">
//<description>Test: absint1args-2 The "abs" function with the arguments set as follows: $arg = xs:int(mid range) </description>
//<created by="Carmelo Montanez" on="2004-12-13"/>
//<environment ref="empty"/>
//<test>fn:abs(xs:int("-1873914410"))</test>
//<result>
//   <all-of>
//      <assert-eq>1873914410</assert-eq>
//      <assert-type>xs:integer</assert-type>
//   </all-of>
//</result>
//</test-case>
    private void testCases(File src, String file, String group, Sequence results) throws Exception {
    	String className = adoptString(group);
    	
    	StringBuilder subPath = new StringBuilder(src.getAbsolutePath());
    	StringBuilder _package_ = new StringBuilder();//adoptString(group);
    	String[] strs = file.split("/");
    	for (int i = 0; i < strs.length - 1; i++) {
    		subPath.append(sep).append(strs[i]);
    		_package_.append(".").append(strs[i]);
    	}
    	File folder = new File(subPath.toString());
    	folder.mkdirs();
    	
        File jTest = new File(folder, className+".java");
        FileWriter fstream = new FileWriter(jTest.getAbsoluteFile());
        BufferedWriter out = new BufferedWriter(fstream);
        
        out.write("package org.exist.xquery.xqts.qt3"+_package_+";\n\n"+
            "import org.exist.xquery.xqts.QT3TS_case;\n" +
            "import org.junit.*;\n\n" +
            "public class "+className+" extends QT3TS_case {\n" +
        	"    private String file = \""+file+"\";\n\n");

        for (NodeProxy p : results.toNodeSet()) {
//        	System.out.println(p);

        	Node testSet = p.getNode();
        	NamedNodeMap attrs = testSet.getAttributes();
        	
        	String testName = attrs.getNamedItem("name").getNodeValue();
        	
        	String adoptTestName = adoptString(testName);
            out.write(
        		"    /* "+testName+" */\n" +
                "    @Test\n");
            if (adoptTestName.contains("fold_left_008") 
        		||adoptTestName.contains("fold_left_020")
        		||adoptTestName.contains("fn_deep_equal_node_args_3")
        		||adoptTestName.contains("fn_deep_equal_node_args_4")
        		||adoptTestName.contains("fn_deep_equal_node_args_5")
        		||adoptTestName.contains("fold_right_013")
            		) {
                out.write("    @Ignore\n");
            }
            out.write(
        		"    public void test_"+adoptTestName+"() {\n" +
                "        testCase(file, \""+testName+"\");\n"+
                "    }\n\n");
        }
        out.write("}");
        out.close();
    	
    }

    private String adoptString(String caseName) {
        String result = caseName.replace("-", "_");
        result = result.replace(".", "_");
        return result;
    }
}