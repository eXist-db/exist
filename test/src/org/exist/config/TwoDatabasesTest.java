/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
package org.exist.config;

import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.File;
import junit.framework.*;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.security.Subject;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.ShutdownListener;
import org.exist.xmldb.XmldbURI;

/**
 *
 * @author alex
 */
public class TwoDatabasesTest extends TestCase
{
   
   final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
   static class ShutdownListenerImpl implements ShutdownListener {

      public void shutdown(String dbname, int remainingInstances) {
         System.err.println("Shutdown of "+dbname+", remaining="+remainingInstances);
      }
   }
   BrokerPool pool1;
   Subject user1;
   
   BrokerPool pool2;
   Subject user2;
   
   public TwoDatabasesTest(String testName)
   {
      super(testName);
   }

   protected void setUp() throws Exception
   {
      // Setup the log4j configuration
      String log4j = System.getProperty("log4j.configuration");
      if (log4j == null) {
         File lf = new File("log4j.xml");
         if (lf.canRead()) {
            System.setProperty("log4j.configuration", lf.toURI().toASCIIString());
         }
      }
      
      int threads = 5;

      String packagePath = TwoDatabasesTest.class.getPackage().getName().replace('.','/');
		String existHome = System.getProperty("exist.home");
      if (existHome==null) {
         existHome = ".";
      }
      File config1File = new File(existHome+"/test/src/"+packagePath+"/conf1.xml");
      File data1Dir = new File(existHome+"/test/temp/"+packagePath+"/data1");
      assertTrue(data1Dir.exists() || data1Dir.mkdirs());

      File config2File = new File(existHome+"/test/src/"+packagePath+"/conf2.xml");
      File data2Dir = new File(existHome+"/test/temp/"+packagePath+"/data2");
      assertTrue(data2Dir.exists() || data2Dir.mkdirs());

      // Configure the database
      Configuration config1 = new Configuration(config1File.getAbsolutePath());
      BrokerPool.configure("db1", 1, threads, config1 );
      pool1 = BrokerPool.getInstance("db1");
      pool1.registerShutdownListener(new ShutdownListenerImpl());
      user1 = pool1.getSecurityManager().getSystemSubject();
      DBBroker broker1 = pool1.get(user1);
      

      Configuration config2 = new Configuration(config2File.getAbsolutePath());
      BrokerPool.configure("db2", 1, threads, config2 );
      pool2 = BrokerPool.getInstance("db2");
      pool2.registerShutdownListener(new ShutdownListenerImpl());
      user2 = pool1.getSecurityManager().getSystemSubject();
      DBBroker broker2 = pool2.get(user2);

      Collection top1 = broker1.getCollection(XmldbURI.create("xmldb:exist:///"));
      assertTrue(top1!=null);
      top1.getLock().release(Lock.READ_LOCK);
      pool1.release(broker1);
      
      Collection top2 = broker2.getCollection(XmldbURI.create("xmldb:exist:///"));
      assertTrue(top2!=null);
      top2.getLock().release(Lock.READ_LOCK);
      pool2.release(broker2);
         
   }

   protected void tearDown() throws Exception
   {
      pool1.shutdown();

      pool2.shutdown();
   }

   public void testPutGet() 
      throws Exception
   {
      put();
      get();
   }
   public void put()
      throws Exception
   {
      System.out.println("Putting documents.");
      DBBroker broker1 = pool1.get(user1);
      Txn transaction1 = pool1.getTransactionManager().beginTransaction();
      Collection top1 = storeBin(broker1,transaction1,"1");
      pool1.getTransactionManager().commit(transaction1);
      top1.release(Lock.READ_LOCK);
      pool1.release(broker1);
      
      DBBroker broker2 = pool2.get(user1);
      Txn transaction2 = pool2.getTransactionManager().beginTransaction();
      Collection top2 = storeBin(broker2,transaction2,"2");
      pool2.getTransactionManager().commit(transaction2);
      top2.release(Lock.READ_LOCK);
      pool2.release(broker2);
   }
   
   public void get()
      throws Exception
   {
      System.out.println("Getting documents.");
      DBBroker broker1 = null;
      try {
         broker1 = pool1.get(user1);
         assertTrue(getBin(broker1,"1"));
      } finally {
         pool1.release(broker1);
      }
      
      DBBroker broker2 = null;
      try {
         broker2 = pool2.get(user2);
         assertTrue(getBin(broker2,"2"));
      } finally {
         pool2.release(broker2);
      }
   }

   static String bin = "ABCDEFG";
   
   public Collection storeBin(DBBroker broker, Txn txn,String suffix)
      throws Exception
   {
      String data = bin+suffix;
      System.out.println("Stored: "+data);
      Collection top = broker.getCollection(XmldbURI.create("xmldb:exist:///"));
      top.addBinaryResource(txn,broker,XmldbURI.create("xmldb:exist:///bin"),data.getBytes(),"text/plain");
      return top;
   }
   
   public boolean getBin(DBBroker broker,String suffix)
      throws Exception
   {
      BinaryDocument binDoc = null;
      try {
         Collection top = broker.getCollection(XmldbURI.create("xmldb:exist:///"));
         System.out.println("count="+top.getDocumentCount(broker));
         MutableDocumentSet docs = new DefaultDocumentSet();
         top.getDocuments(broker,docs);
         XmldbURI [] uris = docs.getNames();
         for (int i=0; i<uris.length; i++) {
            System.out.println(i+": "+uris[i].toString());
         }
         //binDoc = (BinaryDocument)broker.getXMLResource(XmldbURI.create("xmldb:exist:///bin"),Lock.READ_LOCK);
         binDoc = (BinaryDocument)top.getDocument(broker,XmldbURI.create("xmldb:exist:///bin"));
         top.release(Lock.READ_LOCK);
         assertTrue(binDoc!=null);
         ByteArrayOutputStream os = new ByteArrayOutputStream();
         broker.readBinaryResource(binDoc,os);
         String comp = os.size()>0 ? new String(os.toByteArray()) : "";
         System.out.println("Got: "+comp);
         return comp.equals(bin+suffix);
      } finally {
         if (binDoc!=null) {
            binDoc.getUpdateLock().release(Lock.READ_LOCK);
         }
      }
   }

}
