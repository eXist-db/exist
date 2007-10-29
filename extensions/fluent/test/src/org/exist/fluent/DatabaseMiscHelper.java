package org.exist.fluent;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:46:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class DatabaseMiscHelper extends DatabaseHelper {
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
