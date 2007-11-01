package org.exist.fluent;

public class DatabaseMiscTest extends DatabaseTestCase {
    public void testQueryDocs1() {
        Folder c1 = db.createFolder("/c1");
        XMLDocument d1 = c1.documents().build(Name.generate()).elem("test1").end("test1").commit();
        XMLDocument d2 = c1.documents().build(Name.generate()).elem("test2").end("test2").commit();
        c1.documents().build(Name.generate()).elem("test3").end("test3").commit();
        assertTrue(db.query(d1, d2).exists("/test1"));
        assertTrue(db.query(d1, d2).exists("/test2"));
        assertFalse(db.query(d1, d2).exists("/test3"));
    }

    public void testQueryBaseUri() {
        Folder c1 = db.createFolder("/c1");
        c1.documents().build(Name.create("original")).elem("test").end("test").commit();
        assertFalse(db.query().single("doc-available('original')").booleanValue());
        assertTrue(db.query().single("doc-available('c1/original')").booleanValue());
    }
}
