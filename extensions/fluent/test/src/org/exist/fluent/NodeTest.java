package org.exist.fluent;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:55:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class NodeTest extends DatabaseHelper {
    public void testAppend1() {
        XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo")).elem("top").end("top").commit();
        Node node = doc.root().append().elem("child").end("child").commit();
        assertNotNull(node);
        assertEquals("child", node.name());
        assertEquals(1, doc.root().query().single("count(*)").intValue());
    }

    public void testAppend2() {
        XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo")).elem("top").end("top").commit();
        Node node = doc.root().append().elem("child").attr("blah","ick").elem("subchild").end("subchild").end("child").commit();
        assertNotNull(node);
        assertEquals("child", node.name());
        assertEquals(1, doc.root().query().single("count(*)").intValue());
        assertEquals("ick", node.query().single("@blah").value());
        assertEquals("subchild", node.query().single("*").node().name());
    }

    public void testAfterDelete1() {
        XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo")).elem("top").end("top").commit();
        Node node = doc.root().append().elem("child").end("child").commit();
        node.delete();
        doc.root().append().elem("newchild").end("newchild").commit();
        try {
            node.update().attr("foo", "bar").commit();
            fail("update on deleted and re-created node succeeded");
        } catch (DatabaseException e) {
        }
    }

    public void testAfterDelete2() {
        XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo")).elem("top").end("top").commit();
        Node node = doc.root();
        doc.delete();
        doc = null;
        db.createFolder("/test").documents().build(Name.create("bar")).elem("ack").end("ack").commit();
        try {
            node.update().attr("foo", "bar").commit();
            fail("update on node deleted with document succeeded");
        } catch (DatabaseException e) {
        }
    }

    public void testAfterDelete3() {
        Folder folder = db.createFolder("/test");
        XMLDocument doc = folder.documents().build(Name.create("foo")).elem("top").end("top").commit();
        Node node = doc.root();
        folder.delete();
        db.createFolder("/test").documents().build(Name.create("bar")).elem("ack").end("ack").commit();
        try {
            node.update().attr("foo", "bar").commit();
            fail("update on node deleted with folder succeeded");
        } catch (DatabaseException e) {
        }
    }
}
