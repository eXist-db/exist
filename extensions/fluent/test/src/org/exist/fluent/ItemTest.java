package org.exist.fluent;

public class ItemTest extends DatabaseTestCase {
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
