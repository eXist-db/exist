package org.exist.xquery.update;

import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;


/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
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

    @Test
    public void updateAttributeInNamespacedElement() throws XMLDBException {
        final String docName = "docNs.xml";
        final XQueryService service =
            storeXMLStringAndGetQueryService(docName, "<test xmlns=\"http://test.com\" id=\"id1\"/>");

        queryResource(service, docName, "declare namespace t=\"http://test.com\"; update value /t:test/@id with " +
                "'id2'", 0);
        queryResource(service, docName, "declare namespace t=\"http://test.com\"; /t:test[@id = 'id2']", 1);
    }
}
