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
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.rules.TemporaryFolder;
import org.xmldb.api.base.Resource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import org.exist.xmldb.EXistResource;
import org.exist.xmldb.XmldbURI;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * @author wolf
 *
 */
public class StoredModuleTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final static String MODULE =
            "module namespace itg-modules = \"http://localhost:80/itg/xquery\";\n" +
            "declare variable $itg-modules:colls as xs:string+ external;\n" +
            "declare variable $itg-modules:coll as xs:string external;\n" +
            "declare variable $itg-modules:ordinal as xs:integer external;\n" +
            "declare function itg-modules:check-coll() as xs:boolean {\n" +
            "   if (fn:empty($itg-modules:coll)) then fn:false()\n" +
            "   else fn:true()\n" +
            "};";

    private Collection createCollection(String collectionName) throws XMLDBException {
        Collection collection = existEmbeddedServer.getRoot().getChildCollection(collectionName);
        final CollectionManagementService cmService = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        if (collection == null) {
            //cmService.removeCollection(collectionName);
            cmService.createCollection(collectionName);
        }

        collection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + collectionName, "admin", "");
        assertNotNull(collection);
        return collection;
    }

    private void writeModule(Collection collection, String modulename, String module) throws XMLDBException {
        BinaryResource res = collection.createResource(modulename, BinaryResource.class);
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(module.getBytes());
        collection.storeResource(res);
        collection.close();
    }

    @Test
    public void testQuery() throws Exception {

        Collection c = createCollection("test");
        writeModule(c, "test.xqm", MODULE);

        String query = "import module namespace itg-modules = \"http://localhost:80/itg/xquery\" at " +
                "\"xmldb:exist://" + XmldbURI.ROOT_COLLECTION + "/test/test.xqm\"; itg-modules:check-coll()";

        String cols[] = {"one", "two", "three"};

        final XQueryService xqService = existEmbeddedServer.getRoot().getService(XQueryService.class);

        xqService.setNamespace("itg-modules", "http://localhost:80/itg/xquery");

        CompiledExpression compiledQuery = xqService.compile(query);
        for (String col : cols) {
            xqService.declareVariable("itg-modules:coll", col);
            ResourceSet result = xqService.execute(compiledQuery);
            result.getResource(0).getContent();
        }
    }

    @Test
    public void testModule1() throws Exception {
        String collectionName = "module1";
        String module = "module namespace mod1 = 'urn:module1';" +
                "declare function mod1:showMe() as xs:string {" +
                "'hi from module 1'" +
                "};";
        String query = "import module namespace mod1 = 'urn:module1' " +
                "at  'xmldb:exist:/" + collectionName + "/module1.xqm'; " +
                "mod1:showMe()";

        Collection c = createCollection(collectionName);
        writeModule(c, "module1.xqm", module);

        ResourceSet rs = existEmbeddedServer.executeQuery(query);
        String r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 1", r);

    }
    private static final String module2 = "module namespace mod2 = 'urn:module2'; " +
    "import module namespace mod3 = 'urn:module3' " +
    "at  'module3/module3.xqm'; " +
    "declare function mod2:showMe() as xs:string {" +
    " mod3:showMe() " +
    "};";

    private static final String module2b = "module namespace mod2 = 'urn:module2'; " +
    "import module namespace mod3 = 'urn:module3' " +
    "at  'module3.xqm'; " +
    "declare function mod2:showMe() as xs:string {" +
    " mod3:showMe() " +
    "};";

    private static final String module3a = "module namespace mod3 = 'urn:module3';" +
    "declare function mod3:showMe() as xs:string {" +
    "'hi from module 3a'" +
    "};";
    
    private static final String module3b = "module namespace mod3 = 'urn:module3';" +
    "import module namespace mod4 = 'urn:module4' " +
    "at  '../module2/module4.xqm'; " +
    "declare function mod3:showMe() as xs:string {" +
    "mod4:showMe()" +
    "};";

    private static final String module4 = "module namespace mod4 = 'urn:module4';" +
    "declare function mod4:showMe() as xs:string {" +
    "'hi from module 4'" +
    "};";

//    private static final String module5 = "module namespace mod5 = 'urn:module5';" +
//    "declare variable $mod5:testvar := 'variable works' ;"+
//    "declare function mod5:showMe() as xs:string {" +
//    "concat('hi from module 5: ',$mod5:testvar)" +
//    "};";

    @Test(expected=XMLDBException.class)
    public void testModule23_missingRelativeContext() throws XMLDBException {
        String collection2Name = "module2";
        String collection3Name = "module2/module3";

        String query = "import module namespace mod2 = 'urn:module2' " +
                "at  'module2/module2.xqm'; " +
                "mod2:showMe()";

        Collection c2 = createCollection(collection2Name);
        writeModule(c2, "module2.xqm", module2);

        Collection c3 = createCollection(collection3Name);
        writeModule(c3, "module3.xqm", module3a);

        ResourceSet rs = existEmbeddedServer.executeQuery(query);
        String r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 3a", r);
    }

    @Test 
    public void testRelativeImportDb() throws Exception {
        String collection2Name = "module2";
        String collection3Name = "module2/module3";

        String query = "import module namespace mod2 = 'urn:module2' " +
                "at  'xmldb:exist:/" + collection2Name + "/module2.xqm'; " +
                "mod2:showMe()";
        
        Collection c2 = createCollection(collection2Name);
        writeModule(c2, "module2.xqm", module2);

        writeModule(c2, "module3.xqm", module3b);
        
        writeModule(c2, "module4.xqm", module4);

        Collection c3 = createCollection(collection3Name);
        writeModule(c3, "module3.xqm", module3a);

        // test relative module import in subfolder
        ResourceSet rs = existEmbeddedServer.executeQuery(query);
        String r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 3a", r);

        // test relative module import in same folder, and using ".."
        writeModule(c2, "module2.xqm", module2b);

        rs = existEmbeddedServer.executeQuery(query);
        r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 4", r);
    }

    @Test 
    public void testRelativeImportFile() throws Exception {
        final String collection2Name = "module2";
        final String collection3Name = "module3";

        final Path tempDir = temporaryFolder.newFolder("testRelativeImportFile").toPath();
        final Path c2 = tempDir.resolve(collection2Name);
        Files.createDirectories(c2);
        // note c3 is a sub-directory of c2, i.e. module2/module3
        final Path c3 = c2.resolve(collection3Name);
        Files.createDirectories(c3);

        String query = "import module namespace mod2 = 'urn:module2' " +
                "at  '" + c2.resolve("module2.xqm").toAbsolutePath() + "'; " +
                "mod2:showMe()";


        writeFile(c2.resolve("module2.xqm"), module2);
        writeFile(c2.resolve("module3.xqm"), module3b);
        writeFile(c2.resolve("module4.xqm"), module4);

        writeFile(c3.resolve("module3.xqm"), module3a);

        // test relative module import in subfolder
        ResourceSet rs = existEmbeddedServer.executeQuery(query);
        String r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 3a", r);
        
        // test relative module import in same folder, and using ".."
        writeFile(c2.resolve("module2.xqm"), module2b);

        rs = existEmbeddedServer.executeQuery(query);
        r = (String) rs.getResource(0).getContent();
        assertEquals("hi from module 4", r);
    }

    @Test
    public void testCircularImports() throws XMLDBException {
        
        final String index_module = 
                "import module namespace module1 = \"http://test/module1\" at \"xmldb:exist:///db/testCircular/module1.xqy\";" +
                "module1:func()";

        final String module1_module =
            "module namespace module1 = \"http://test/module1\";" +
            "import module namespace processor = \"http://test/processor\" at \"processor.xqy\";" +
            "declare function module1:func()" +
            "{" +
            "   processor:execute()" +
            "};" +
            "declare function module1:hello($name as xs:string)" +
            "{" +
            "    <hello>{$name}</hello>" +
            "};";

        final String processor_module =
            "module namespace processor = \"http://test/processor\";" +
            "import module namespace impl = \"http://test/processor/impl/exist-db\" at \"impl.xqy\";" +
            "declare function processor:execute()" +
            "{" +
            "    impl:execute()" +
            "};";

        final String impl_module =
            "module namespace impl = \"http://test/processor/impl/exist-db\";" +
            "import module namespace controller = \"http://test/controller\" at \"controller.xqy\";" +
            "declare function impl:execute()" +
            "{" +
            "    controller:index()" +
            "};";

        final String controller_module =
            "module namespace controller = \"http://test/controller\";" +
            "import module namespace module1 = \"http://test/module1\" at \"module1.xqy\";" +
            "declare function controller:index() as item()*" +
            "{" +
            "    module1:hello(\"world\")" +
            "};";

        Collection testHome = createCollection("testCircular");
        writeModule(testHome, "module1.xqy", module1_module);
        writeModule(testHome, "processor.xqy", processor_module);
        writeModule(testHome, "impl.xqy", impl_module);
        writeModule(testHome, "controller.xqy", controller_module);

        existEmbeddedServer.executeQuery(index_module);
    }

    @Test
    public void testLocalVariableDeclarationCallsLocalFunction() throws XMLDBException {
        final String index_module =
            "xquery version \"1.0\";" +
            "import module namespace xqmvc = \"http://scholarsportal.info/xqmvc/core\" at \"xmldb:exist:///db/testLocalVariableDeclaration/module1.xqm\";" +
            "xqmvc:function1()";

        final String module1_module =
            "xquery version \"1.0\";" +
            "module namespace xqmvc = \"http://scholarsportal.info/xqmvc/core\";" +
            "declare variable $xqmvc:plugin-resource-dir as xs:string := fn:concat('/plugins/', xqmvc:current-plugin(), '/resources');" +
            "declare function xqmvc:current-plugin() {" +
                "\"somePlugin\"" +
            "};" +
            "declare function xqmvc:function1() {" +
                "\"hello world\"" +
            "};";

        Collection testHome = createCollection("testLocalVariableDeclaration");
        writeModule(testHome, "module1.xqm", module1_module);

        existEmbeddedServer.executeQuery(index_module);
    }
    
    @Test
    public void dyanmicModuleImport_for_same_namespace() throws XMLDBException {
        
        Collection testHome = createCollection("testDynamicModuleImport");
        
        final String module1 =
                "xquery version \"1.0\";" +
                "module namespace modA = \"http://moda\";" +
                "declare function modA:hello() {" +
                "\t<hello-from>module1</hello-from>" +
                "};";
        
        
        final String module2 =
                "xquery version \"1.0\";" +
                "module namespace modA = \"http://moda\";" +
                "declare function modA:hello() {" +
                "\t<hello-from>module2</hello-from>" +
                "};";
        
        final String implModule = 
                "xquery version \"1.0\";" +
                "module namespace impl = \"http://impl\";" +
                "declare function impl:execute-module-function($module-namespace as xs:anyURI, $controller-file as xs:anyURI, $function-name as xs:string) {" +
                "\tlet $mod := util:import-module($module-namespace, 'pfx', xs:anyURI(fn:concat('xmldb:exist://', $controller-file))) return" +
                "\t\tutil:eval(fn:concat('pfx:', $function-name, '()'), false())" +
                "};";
        
        final String processorModule = 
                "xquery version \"1.0\";" +
                "module namespace processor = \"http://processor\";" +
                "import module namespace impl = \"http://impl\" at \"xmldb:exist://" + testHome.getName() + "/impl.xqm\";" +
                "declare function processor:execute-module-function($module-namespace as xs:anyURI, $controller-file as xs:anyURI, $function-name as xs:string) {" +
                "\timpl:execute-module-function($module-namespace, $controller-file, $function-name)" +
                "};";
        
        writeModule(testHome, "module1.xqm", module1);
        writeModule(testHome, "module2.xqm", module2);
        writeModule(testHome, "impl.xqm", implModule);
        writeModule(testHome, "processor.xqm", processorModule);
        
        final String query1 = 
                "xquery version \"1.0\";" +
                "import module namespace processor = \"http://processor\" at \"xmldb:exist://" + testHome.getName() + "/processor.xqm\";" +
                "\tprocessor:execute-module-function(xs:anyURI('http://moda'), xs:anyURI('" + testHome.getName() + "/module1.xqm'), 'hello')";

        final ResourceSet rs1 = existEmbeddedServer.executeQuery(query1);
        
        assertEquals(1, rs1.getSize());
        Resource r1 = rs1.getIterator().nextResource();
        assertEquals("<hello-from>module1</hello-from>", r1.getContent());
        
        final String query2 = 
                "xquery version \"1.0\";" +
                "import module namespace processor = \"http://processor\" at \"xmldb:exist://" + testHome.getName() + "/processor.xqm\";" +
                "\tprocessor:execute-module-function(xs:anyURI('http://moda'), xs:anyURI('" + testHome.getName() + "/module2.xqm'), 'hello')";
        

        final ResourceSet rs2 = existEmbeddedServer.executeQuery(query2);
        
        assertEquals(1, rs2.getSize());
        Resource r2 = rs2.getIterator().nextResource();
        assertEquals("<hello-from>module2</hello-from>", r2.getContent());
        
    }

    private void writeFile(final Path path, final String module) throws IOException {
        //assertTrue(Files.isWritable(path));
        try (final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
            writer.print(module);
        }
    }
}
