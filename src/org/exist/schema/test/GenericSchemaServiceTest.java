/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 */
package org.exist.schema.test;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.exist.schema.SchemaService;
import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.XMLType;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author Sebastian Bossung, Technische Universitaet Hamburg-Harburg
 */
public class GenericSchemaServiceTest extends TestCase {
  private final static String URI = "xmldb:exist:///db";

  private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

  private Collection rootCollection = null;

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

  public GenericSchemaServiceTest(String name) {
    super(name);
  }

  /*
   * @see TestCase#setUp()
   */
  protected void setUp() throws Exception {
    Class cl = Class.forName(DRIVER);
    Database database = (Database) cl.newInstance();
    database.setProperty("create-database", "true");
    DatabaseManager.registerDatabase(database);
    rootCollection = DatabaseManager.getCollection(URI, "admin", null);
    if (rootCollection == null)
      throw new Exception("Could not connect to database.");

  }

  private SchemaService getSchemaService() throws XMLDBException {
    return (SchemaService) rootCollection.getService("SchemaService", "1.0");
  }

  protected void insertSchemas() throws XMLDBException {
    SchemaService service = getSchemaService();
    service.putSchema(ADDRESSBOOK_SCHEMA);
  }

  /*
   * @see TestCase#tearDown()
   */
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testGetName() throws Exception {
    getSchemaService().getName().equals("SchemaService");
  }

  public void testGetVersion() throws XMLDBException {
    getSchemaService().getVersion().equals("1.0");
  }

  public void testPutSchema() throws XMLDBException {
    // insert the new schema:
    getSchemaService().putSchema(ADDRESSBOOK_SCHEMA);
    // check it's there:
    assertNotNull(getSchemaService().getSchema("http://jmvanel.free.fr/xsd/addressBook"));
  }

  public void testGetSchema() throws XMLDBException {
    insertSchemas();
    XMLResource schema = getSchemaService().getSchema("http://jmvanel.free.fr/xsd/addressBook");
    assertNotNull(schema);
    String targetNamespace;
    Node domSchema = schema.getContentAsDOM();
    targetNamespace = domSchema.getAttributes().getNamedItem("targetNamespace").getNodeValue();
    assertEquals(targetNamespace, "http://jmvanel.free.fr/xsd/addressBook");
  }

  public void testValidateContents() throws XMLDBException {
    insertSchemas();
    assertTrue(getSchemaService().validateContents(ADDRESSBOOK_DOCUMENT));
    try {
      getSchemaService().validateContents(ADDRESSBOOK_DOCUMENT_INVALID);
      assertTrue(false);
    } catch (XMLDBException e) {
      assertTrue(true);
      String message = e.getMessage();
      System.out.println(message);
      assertTrue(message.indexOf("Invalid content") > -1);
      assertTrue(message.indexOf("element 'name'") > -1);
    }
  }

  public void testValidateResource() throws XMLDBException {
    Collection collection = DatabaseManager.getCollection(URI, "admin", null);
    {
      XMLResource resource = (XMLResource) collection.createResource("addressbook.xml", "XMLResource");
      resource.setContent(ADDRESSBOOK_DOCUMENT);
      collection.storeResource(resource);
    }
    {
      XMLResource resource = (XMLResource) collection.createResource("addressbook_invalid.xml", "XMLResource");
      resource.setContent(ADDRESSBOOK_DOCUMENT_INVALID);
      collection.storeResource(resource);
    }
    assertTrue(getSchemaService().validateResource("addressbook.xml"));
    try {
      getSchemaService().validateResource("addressbook_invalid.xml");
      assertTrue(false);
    } catch (XMLDBException e) {
      assertTrue(true);
      String message = e.getCause().getMessage();
      System.out.println(message);
      assertTrue(message.indexOf("Invalid content") > -1);
      assertTrue(message.indexOf("element 'name'") > -1);
    }

  }

  public void testGetAttribute() throws XMLDBException {
    insertSchemas();
    AttributeDecl attribute = getSchemaService().getAttribute(
        new QName("http://jmvanel.free.fr/xsd/addressBook", "uselessAttribute"));
    assertNotNull(attribute);
    assertEquals("uselessAttribute", attribute.getName());
  }

  public void testGetElement() throws XMLDBException {
    insertSchemas();
    ElementDecl element = getSchemaService().getElement(
        new QName("http://jmvanel.free.fr/xsd/addressBook", "addressBook"));
    assertNotNull(element);
    assertEquals("addressBook", element.getName());
  }

  public void testGetType() throws XMLDBException {
    insertSchemas();
    XMLType type = getSchemaService().getType(new QName("http://jmvanel.free.fr/xsd/addressBook", "record"));
    assertNotNull(type);
    assertEquals("record", type.getName());
  }

  public void testIsKnownNamespace() throws XMLDBException {
    insertSchemas();
    assertEquals(true, getSchemaService().isKnownNamespace("http://jmvanel.free.fr/xsd/addressBook"));
    assertEquals(false, getSchemaService().isKnownNamespace("http://dont.have.this.namespace"));
  }

  private final static String TRANSIENT_SCHEMA = "<?xml version='1.0'?>"
      + "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'"
      + "    targetNamespace='http://somewhere.invalid/transient'"
      + "    xmlns:t='http://somewhere.invalid/transient' elementFormDefault='qualified'>"
      + "  <xsd:element name='testElement' type='xsd:string'/>" + "</xsd:schema>";

  private final static String TRANSIENT_TEST_DOCUMENT = "<testElement xmlns='http://somewhere.invalid/transient'>test Strings can contain numbers: 123</testElement>";

  public void testRegisterTransientSchema() throws XMLDBException {
    SchemaService service = getSchemaService();
    service.registerTransientSchema(TRANSIENT_SCHEMA);
    assertTrue(service.validateContents(TRANSIENT_TEST_DOCUMENT));
  }

  public void testRebuildIndex() throws XMLDBException {
    insertSchemas();
    getSchemaService().rebuildIndex();
    getSchemaService().isKnownNamespace("http://jmvanel.free.fr/xsd/addressBook");
  }

}