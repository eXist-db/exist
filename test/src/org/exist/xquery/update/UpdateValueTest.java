package org.exist.xquery.update;

import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;


/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class UpdateValueTest extends AbstractTestUpdate {

    @Test
    public void updateNamespacedAttribute() throws XMLDBException {
        final String docName = "pathNs.xml";
        XQueryService service =
            storeXMLStringAndGetQueryService(docName, "<test><t xml:id=\"id1\"/></test>");

        queryResource(service, docName, "//t[@xml:id eq 'id1']", 1);

        queryResource(service, docName, "update value //t/@xml:id with 'id2'", 0);

        queryResource(service, docName, "//t[@xml:id eq 'id2']", 1);
        queryResource(service, docName, "id('id2', /test)", 1);
    }
}
