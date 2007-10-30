package org.exist.fluent;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:49:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class FolderTest extends DatabaseHelper {
    public void testCreateTop() {
        db.createFolder("/top");
        try {
            db.getFolder("/top");
        } catch (DatabaseException e) {
            fail(e.getMessage());
        }
    }

    public void testCreateNested() {
        db.createFolder("/top/nested");
        try {
            db.getFolder("/top/nested");
        } catch (DatabaseException e) {
            fail(e.getMessage());
        }
    }

    public void testGetRoot() {
        assertEquals("", db.getFolder("/").name());
    }

    public void testGetMissingTop1() {
        try {
            db.getFolder("/top");
            fail();
        } catch (DatabaseException e) {
        }
    }

    public void testGetMissingNested1() {
        try {
            db.getFolder("/top/nested");
            fail();
        } catch (DatabaseException e) {
        }
    }

    public void testDuplicate1() {
        Folder c1 = db.createFolder("/top");
        Folder c2 = c1.clone();
        assertEquals(c1.path(), c2.path());
    }

    public void testDuplicate2() {
        Folder c1 = db.createFolder("/top");
        c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
        Folder c2 = c1.clone();
        assertEquals("http://www.ideanest.com/", c2.namespaceBindings().get("foo"));
    }

    public void testGetName1() {
        assertEquals("top", db.createFolder("/top").name());
    }

    public void testGetName2() {
        assertEquals("nested", db.createFolder("/top/nested").name());
    }

    public void testGetPath1() {
        assertEquals("/top", db.createFolder("/top").path());
    }

    public void testGetPath2() {
        assertEquals("/top/nested", db.createFolder("/top/nested").path());
    }

    public void testGetPath3() {
        assertEquals("/", db.createFolder("/").path());
    }

    public void testGetParent1() {
        assertEquals("/top", db.createFolder("/top/nested").parent().path());
    }

    public void testGetParent2() {
        try {
            db.getFolder("/").parent();
            fail();
        } catch (DatabaseException e) {
        }
    }

    public void testGetChild1() {
        db.createFolder("/top/nested");
        db.getFolder("/top").children().get("nested");
    }

    public void testGetChild2() {
        db.createFolder("/top/nested/more");
        db.getFolder("/top").children().get("nested/more");
    }

    public void testGetChild3() {
        db.createFolder("/top/nested");
        try {
            db.getFolder("/top").children().get("/nested");
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    public void testGetChild4() {
        db.createFolder("/top/nested");
        db.getFolder("/").children().get("top");
    }

    public void testGetChild5() {
        db.createFolder("/top/nested");
        Folder c1 = db.getFolder("/top");
        c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
        Folder c2 = c1.children().get("nested");
        assertEquals("http://www.ideanest.com/", c2.namespaceBindings().get("foo"));
    }

    public void testNamespace1() {
        Folder c1 = db.getFolder("/");
        c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
        assertEquals("http://www.ideanest.com/", c1.namespaceBindings().get("foo"));
    }

    public void testNamespace2() {
        Folder c1 = db.getFolder("/");
        c1.namespaceBindings().put("", "http://www.ideanest.com/");
        assertEquals("http://www.ideanest.com/", c1.namespaceBindings().get(""));
    }

    public void testNamespace3() {
        Folder c1 = db.getFolder("/");
        c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
        c1.namespaceBindings().remove("foo");
        assertNull(c1.namespaceBindings().get("foo"));
    }

    public void testNamespace4() {
        Folder c1 = db.getFolder("/");
        c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
        c1.namespaceBindings().put("bar", "urn:blah");
        c1.namespaceBindings().remove("foo");
        assertNull(c1.namespaceBindings().get("foo"));
        assertEquals("urn:blah", c1.namespaceBindings().get("bar"));
    }

    public void testNamespace5() {
        Folder c1 = db.getFolder("/");
        c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
        c1.namespaceBindings().put("bar", "urn:blah");
        c1.namespaceBindings().clear();
        assertNull(c1.namespaceBindings().get("foo"));
        assertNull(c1.namespaceBindings().get("bar"));
    }

    public void testBuildDocument1() {
        Folder c1 = db.createFolder("/top");
        c1.documents().build(Name.create("doc1")).elem("test").end("test").commit();
        assertEquals(1, c1.documents().size());
    }

    public void testBuildDocument2() {
        Folder c1 = db.createFolder("/top");
        c1.documents().build(Name.create("doc1")).elem("test1").end("test1").commit();
        c1.documents().build(Name.overwrite("doc1")).elem("test2").end("test2").commit();
        assertEquals(1, c1.documents().size());
    }

    public void testBuildDocument3() {
        Folder c1 = db.createFolder("/top");
        c1.documents().build(Name.create("doc1")).elem("test1").end("test1").commit();
        try {
            c1.documents().build(Name.create("doc1")).elem("test2").end("test2").commit();
            fail();
        } catch (DatabaseException e) {
        }
    }

    public void testBuildDocument4() {
        Folder c1 = db.createFolder("/top");
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        assertEquals(1, c1.documents().size());
    }

    public void testBuildDocument5() {
        Folder c1 = db.createFolder("/top");
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        assertEquals(2, c1.documents().size());
    }

    public void testSize1() {
        assertEquals(0, db.getFolder("/").documents().size());
    }

    public void testSize2() {
        Folder c1 = db.createFolder("/top/nested");
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        c1.documents().build(Name.create("doc1")).elem("test").end("test").commit();
        assertEquals(3, c1.documents().size());
        assertEquals(0, db.getFolder("/top").documents().size());
    }

    public void testChildrenSize1() {
        Folder c1 = db.createFolder("/top");
        assertEquals(0, c1.children().size());
    }

    public void testChildrenSize2() {
        db.createFolder("/top/nested1");
        db.createFolder("/top/nested2");
        Folder c1 = db.getFolder("/top");
        assertEquals(2, c1.children().size());
    }

    public void testChildrenSize3() {
        db.createFolder("/top/nested1");
        db.createFolder("/top/nested2").documents().build(Name.generate()).elem("test").end("test").commit();
        Folder c1 = db.getFolder("/top");
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        assertEquals(2, c1.children().size());
    }

    public void testClear1() {
        Folder c1 = db.createFolder("/top");
        c1.clear();
        assertEquals(0, c1.documents().size());
    }

    public void testClear2() {
        Folder c1 = db.createFolder("/top");
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        c1.clear();
        assertEquals(0, c1.documents().size());
    }

    public void testClear3() {
        db.createFolder("/top/nested1");
        db.createFolder("/top/nested2");
        Folder c1 = db.getFolder("/top");
        c1.clear();
        assertEquals(0, c1.children().size());
    }

    public void testClear4() {
        db.createFolder("/top/nested1");
        db.createFolder("/top/nested1/more");
        db.createFolder("/top/nested2");
        Folder c1 = db.getFolder("/top");
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        c1.documents().build(Name.generate()).elem("test").end("test").commit();
        c1.clear();
        assertEquals(0, c1.documents().size());
        assertEquals(0, c1.children().size());
    }

    public void testDelete1() {
        Folder c1 = db.createFolder("/top/nested");
        db.getFolder("/top/nested");
        c1.delete();
        try {
            db.getFolder("/top/nested");
            fail();
        } catch (DatabaseException e) {
        }
    }

    public void testDelete2() {
        db.createFolder("/top/nested/more");
        Folder c1 = db.getFolder("/top/nested");
        db.getFolder("/top/nested/more");
        c1.delete();
        try {
            db.getFolder("/top/nested/more");
            fail();
        } catch (DatabaseException e) {
        }
    }

    public void testGetDocument() {
        Folder c1 = db.createFolder("/c1");
        c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        Document d = c1.documents().get("original");
        assertNotNull(d);
    }

    public void testQuery1() {
        Folder c1 = db.createFolder("/c1");
        c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        c1.query().single("/test");
    }

    public void testQuery2() {
        Folder c1 = db.createFolder("/c1");
        c1.namespaceBindings().put("", "http://example.com");
        c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        c1.query().single("/test");
    }

    public void testQueryBaseUri() {
        Folder c1 = db.createFolder("/c1");
        c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        assertTrue(c1.query().single("doc-available('original')").booleanValue());
    }

    public void testConvertToSequence() {
        Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
        c1.documents().build(Name.create("one")).elem("test").end("test").commit();
        c1.children().create("sub").documents().build(Name.create("another")).elem("test").end("test").commit();
        assertEquals(0, c2.query().all("/test").size());
        assertEquals(2, c1.query().all("/test").size());
        assertEquals(2, c2.query().all("$_1/test", new Object[] {c1}).size());
    }

    public void testConvertDocumentsToSequence() {
        Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
        c1.documents().build(Name.create("one")).elem("test").end("test").commit();
        c1.children().create("sub").documents().build(Name.create("another")).elem("test").end("test").commit();
        assertEquals(0, c2.query().all("/test").size());
        assertEquals(1, c2.query().all("$_1/test", new Object[] {c1.documents()}).size());
    }

    public void testMove1() {
        Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
        Folder f = c1.children().create("f");
        f.move(c2, Name.keepCreate());
        assertEquals("/c2/f", f.path());
        assertEquals(c2, f.parent());
    }

    public void testMove2() {
        Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
        Folder f = c1.children().create("f");
        f.move(c2, Name.create("g"));
        assertEquals("/c2/g", f.path());
        assertEquals(c2, f.parent());
    }

    public void testMove3() {
        Folder c1 = db.createFolder("/c1");
        Folder f = c1.children().create("f");
        f.move(f.parent(), Name.create("g"));
        assertEquals("/c1/g", f.path());
        assertEquals(c1, f.parent());
    }

    public void testCopy1() {
        Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
        Folder f1 = c1.children().create("f");
        Folder f2 = f1.copy(c2, Name.keepCreate());
        assertEquals("/c1/f", f1.path());
        assertEquals(c1, f1.parent());
        assertEquals("/c2/f", f2.path());
        assertEquals(c2, f2.parent());
    }

    public void testCopy2() {
        Folder c1 = db.createFolder("/c1");
        Folder f1 = c1.children().create("f1");
        Folder f2 = f1.copy(f1.parent(), Name.create("f2"));
        assertEquals("/c1/f1", f1.path());
        assertEquals(c1, f1.parent());
        assertEquals("/c1/f2", f2.path());
        assertEquals(c1, f2.parent());
    }
}
