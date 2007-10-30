package org.exist.fluent;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:50:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class ItemListTest extends DatabaseHelper {
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
        ItemList res = doc.query().all("//(b|d)");
        assertEquals(2, doc.query().all("$_1//c", new Object[] {res}).size());
    }
}
