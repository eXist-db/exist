package org.exist.fluent;

public class ItemListTest extends DatabaseTestCase {
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
