/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

package org.exist.validation.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.validation.internal.DatabaseResources;

/**
 *
 * @author wessels
 */
public class GrammarAccessTest extends TestCase {
    
    
  private final static String ADDRESSBOOK_SCHEMA = "<?xml version='1.0'?>"
      + "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'"
      + "    targetNamespace='http://jmvanel.free.fr/xsd/addressBook'"
      + "    xmlns='http://jmvanel.free.fr/xsd/addressBook' elementFormDefault='qualified'>"
      + "  <xsd:attribute name='uselessAttribute' type='xsd:string'/>" + "  <xsd:complexType name='record'> "
      + "     <xsd:sequence> " + "        <xsd:element name='cname' type='xsd:string'/>"
      + "        <xsd:element name='email' type='xsd:string'/> " + "     </xsd:sequence> " + "  </xsd:complexType> "
      + "  <xsd:element name='addressBook'>" + "     <xsd:complexType> " + "        <xsd:sequence> "
      + "        <xsd:element name='owner' type='record'/>" + "        <xsd:element name='person' type='record'"
      + "                       minOccurs='0' maxOccurs='unbounded'/>"
      + "        </xsd:sequence>            </xsd:complexType> " + "  </xsd:element> " + "</xsd:schema> ";

  private final static String ADDRESSBOOK_DOCUMENT = "<?xml version='1.0'?> "
      + "<addressBook xmlns='http://jmvanel.free.fr/xsd/addressBook'> " + "     <owner> "
      + "        <cname>John Punin</cname>" + "        <email>puninj@cs.rpi.edu</email>" + "     </owner> "
      + "     <person> " + "        <cname>Harrison Ford</cname>" + "        <email>hford@famous.org</email> "
      + "     </person> " + "     <person> " + "        <cname>Julia Roberts</cname>"
      + "        <email>jr@pw.com</email> " + "     </person> " + "</addressBook> ";

  private final static String ADDRESSBOOK_DOCUMENT_INVALID = "<?xml version='1.0'?> "
      + "<addressBook xmlns='http://jmvanel.free.fr/xsd/addressBook'> " + "     <owner> "
      + "        <cname>John Punin</cname>" + "        <email>puninj@cs.rpi.edu</email>" + "     </owner> "
      + "     <person> " + "        <cname>Harrison Ford</cname>" + "        <email>hford@famous.org</email> "
      + "     </person> " + "     <person> " + "        <name>Julia Roberts</name>"
      + "        <email>jr@pw.com</email> " + "     </person> " + "</addressBook> ";
    
    
    private BrokerPool pool = null;
    
    public GrammarAccessTest(String testName) {
        super(testName);
    }

    protected void setUp() throws Exception {
        System.out.println(">>> setUp");
        pool = startDB();
        System.out.println("<<<\n");
    }


    public static Test suite() {
        TestSuite suite = new TestSuite(GrammarAccessTest.class);
        return suite;
    }
    
    protected BrokerPool startDB() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

    protected File writeToTempFile(String doc) throws IOException {
        File result = File.createTempFile("EXISTVALIDATE","tmp");
        StringReader sr = new StringReader(doc);
        FileWriter fw = new FileWriter(result);
        
        // Transfer bytes from in to out
        char[] buf = new char[1024];
        int len;
        while ((len = sr.read(buf, 0, 1024)) > 0) {
            fw.write(buf, 0, len);
        }
        sr.close();
        fw.close();
        result.deleteOnExit();
        return result;
    }
    
    protected File writeToTempFile(InputStream is) throws IOException {
        File result = File.createTempFile("EXISTVALIDATE","tmp");
        FileOutputStream fos = new FileOutputStream(result);
        
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            fos.write(buf, 0, len);
        }
        is.close();
        fos.close();
        
        result.deleteOnExit();
        return result;
    }
    
    protected void tearDown() throws Exception {
        System.out.println(">>> tearDown");
        BrokerPool.stopAll(false);
        System.out.println("<<<\n");
    }
    
    
    public void testInsertGrammar() throws IOException {
        
        System.out.println(">>> testInsertGrammar");
        
        DatabaseResources ga = new DatabaseResources(pool);
        Validator va = new Validator(pool);
        
        ga.insertGrammar( writeToTempFile(ADDRESSBOOK_SCHEMA) , DatabaseResources.GRAMMAR_XSD , "test.xsd");        
                          
        System.out.println("<<<");
    }
    
    public void testIsGrammarInDatabase(){
        System.out.println(">>> testIsGrammarInDatabase");
        DatabaseResources ga = new DatabaseResources(pool);
        Validator va = new Validator(pool);
        
        Assert.assertTrue( ga.hasGrammar( DatabaseResources.GRAMMAR_XSD, "http://jmvanel.free.fr/xsd/addressBook" ) );
        System.out.println("<<<");
    }
    
    
    public void testIsGrammarNotInDatabase(){
        System.out.println(">>> testIsGrammarNotInDatabase");
        DatabaseResources ga = new DatabaseResources(pool);
        Validator va = new Validator(pool);
        Assert.assertFalse( ga.hasGrammar( DatabaseResources.GRAMMAR_XSD, "http://jmvanel.free.fr/xsd/addressBooky" ) );
        System.out.println("<<<");
    }
    
    public void testValidateValidDocument() {
        System.out.println(">>> testValidateValidDocument");
//        DatabaseResources ga = new DatabaseResources(pool);
//        Validator va = new Validator(pool);
//        ValidationReport veh = va.validate( new StringReader(ADDRESSBOOK_DOCUMENT) );
//        Assert.assertFalse( veh.hasErrorsAndWarnings() );
//        System.out.println(veh.getErrorReport());
        System.out.println("<<<");
    }
    
    public void testValidateInValidDocument() {
        System.out.println(">>> testValidateInValidDocument");
//        DatabaseResources ga = new DatabaseResources(pool);
//        Validator va = new Validator(pool);
//        ValidationReport veh2 = va.validate( new StringReader(ADDRESSBOOK_DOCUMENT_INVALID) );
//        Assert.assertTrue( veh2.hasErrorsAndWarnings() );
        System.out.println("<<<");
        
    }
}
