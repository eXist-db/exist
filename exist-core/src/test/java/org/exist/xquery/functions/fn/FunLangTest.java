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
package org.exist.xquery.functions.fn;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.runner.RunWith;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;

/**
 *
 * @author ljo
 */
@RunWith(ParallelRunner.class)
public class FunLangTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    private static final String TEST_COLLECTION = "fun-lang-test";

    private static final String MODULE =
        """
        module namespace mod = "http://exist-db.org/test/fun-lang";
        
        declare variable $mod:data := <root xml:lang="en"><p>hello</p></root>;
        
        declare function mod:check-lang($lang as xs:string) as xs:boolean {
            lang($lang, $mod:data/p)
        };
        """;

    @BeforeClass
    public static void setUp() throws XMLDBException {
        final Collection testCollection = createCollection(TEST_COLLECTION);
        writeModule(testCollection, "mod.xqm", MODULE);
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService cmService = root.getService(CollectionManagementService.class);
        cmService.removeCollection(TEST_COLLECTION);
    }

    private static Collection createCollection(final String collectionName) throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService cmService = root.getService(CollectionManagementService.class);
        Collection collection = root.getChildCollection(collectionName);
        if (collection == null) {
            cmService.createCollection(collectionName);
        }
        collection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + collectionName, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        assertNotNull(collection);
        return collection;
    }

    private static void writeModule(final Collection collection, final String moduleName, final String module) throws XMLDBException {
        final BinaryResource res = collection.createResource(moduleName, BinaryResource.class);
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(module.getBytes());
        collection.storeResource(res);
        collection.close();
    }

    @Test
    public void fnLangInLibraryModuleWithVariable() throws XMLDBException {
        // Regression test for https://github.com/eXist-db/exist/issues/5103
        // fn:lang in analyze() compiles a sub-query that corrupts the XQueryContext,
        // causing module-level variables to fail with XPDY0002 on first access.
        final String query =
            "import module namespace mod = \"http://exist-db.org/test/fun-lang\"" +
            "    at \"xmldb:exist:///db/" + TEST_COLLECTION + "/mod.xqm\";\n" +
            "mod:check-lang(\"en\")";
        final ResourceSet resourceSet = existEmbeddedServer.executeQuery(query);
        assertEquals(1, resourceSet.getSize());
        assertEquals("true", resourceSet.getResource(0).getContent());
    }

    @Test
    public void testFnLangWithContext() throws XMLDBException {
        final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return $doc-frag//desc[lang(\"en-US\")]"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("<desc xml:lang=\"en-US\" n=\"1\">\n    <line>The first line of the description.</line>\n</desc>", resourceSet.getResource(0).getContent());
    }

        @Test
    public void testFnLangWithArgument() throws XMLDBException {
		final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return lang(\"en-US\", $doc-frag//desc[@n eq \"2\"])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("false", resourceSet.getResource(0).getContent());
    }
    
    @Test
    public void testFnLangWithAttributeArgument() throws XMLDBException {
		final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return lang(\"en-US\", $doc-frag//desc/@n[. eq \"1\"])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("true", resourceSet.getResource(0).getContent());
    }
}
