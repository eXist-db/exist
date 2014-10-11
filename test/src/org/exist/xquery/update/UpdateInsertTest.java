package org.exist.xquery.update;

import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class UpdateInsertTest extends AbstractTestUpdate {

    @Test
    public void insertNamespacedAttribute() throws XMLDBException {

        final String docName = "pathNs2.xml";
        XQueryService service =
            storeXMLStringAndGetQueryService(docName, "<test/>");

        queryResource(service, docName, "//t[@xml:id]", 0);

        String update = "update insert <t xml:id=\"id1\"/> into /test";
        queryResource(service, docName, update, 0);

        queryResource(service, docName, "//t[@xml:id eq 'id1']", 1);
        queryResource(service, docName, "/test/id('id1')", 1);

        update = "update value //t/@xml:id with 'id2'";
        queryResource(service, docName, update, 0);

        queryResource(service, docName, "//t[@xml:id eq 'id2']", 1);
        queryResource(service, docName, "id('id2', /test)", 1);
    }
}
