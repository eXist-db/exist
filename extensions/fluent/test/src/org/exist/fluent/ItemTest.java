package org.exist.fluent;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:50:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class ItemTest extends DatabaseHelper {
    public void testConvertToSequence() {
        XMLDocument doc = db.createFolder("/top").documents().build(Name.create("test"))
            .elem("a")
                .elem("b")
                    .elem("c")
                    .end("c")
                .end("b")
                .elem("d")
                    .elem("c")
                    .end("c")
                .end("d")
                .elem("c")
                .end("c")
            .end("a")
            .commit();
        assertEquals(3, doc.query().all("//c").size());
        Item res = doc.query().single("//b");
        assertEquals(1, doc.query().all("$_1//c", res).size());
    }
}
