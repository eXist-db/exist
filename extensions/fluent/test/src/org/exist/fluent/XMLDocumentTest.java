package org.exist.fluent;

public class XMLDocumentTest extends DatabaseTestCase {
    public void testQuery1() {
        Folder c1 = db.createFolder("/c1");
        XMLDocument doc = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        doc.query().single("/test");
    }

    public void testQuery2() {
        Folder c1 = db.createFolder("/c1");
        c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        XMLDocument doc = c1.documents().get("original").xml();
        doc.query().single("/test");
    }

    public void testQuery3() {
        Folder c1 = db.createFolder("/c1");
        XMLDocument doc = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        assertEquals(1, doc.query().all("/test").size());
    }

    public void testQuery4() {
        Folder c1 = db.createFolder("/c1");
        XMLDocument doc = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        c1.documents().build(Name.create("another")).elem("test").end("test").commit();
        doc.query().single("/test");
        assertEquals(2, c1.query().all("/test").size());
    }

    public void testCopy1() {
        Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
        XMLDocument original = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        XMLDocument copy = original.copy(c2, Name.keepCreate());
        assertEquals(1, c2.documents().size());
        copy.query().single("/test");
    }

    public void testConvertToSequence() {
        Folder c = db.createFolder("/top");
        c.documents().build(Name.create("one")).elem("test").end("test").commit();
        XMLDocument doc = c.documents().build(Name.create("two")).elem("test").end("test").commit();
        assertEquals(2, c.query().all("/test").size());
        assertEquals(1, c.query().all("$_1/test", new Object[] {doc}).size());
    }
}
