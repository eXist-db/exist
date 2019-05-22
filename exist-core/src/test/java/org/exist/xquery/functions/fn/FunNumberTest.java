package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author aretter
 */
public class FunNumberTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);
    
    @Test
    public void testFnNumberWithContext() throws XMLDBException {
        final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $errors := " +
                "<report>" +
                    "<message level=\"Error\" line=\"1191\" column=\"49\" repeat=\"96\"></message>" +
                    "<message level=\"Error\" line=\"161740\" column=\"25\"></message>" +
                    "<message level=\"Error\" line=\"162327\" column=\"92\" repeat=\"87\"></message>" +
                    "<message level=\"Error\" line=\"255090\" column=\"25\">c</message>" +
                    "<message level=\"Error\" line=\"255702\" column=\"414\" repeat=\"9\"></message>" +
                "</report>" +
            "return sum($errors//message/(@repeat/number(),1)[1])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("194", resourceSet.getResource(0).getContent());
    }
    
    @Test
    public void testFnNumberWithArgument() throws XMLDBException {
        final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $errors := " +
                "<report>" +
                    "<message level=\"Error\" line=\"1191\" column=\"49\" repeat=\"96\"></message>" +
                    "<message level=\"Error\" line=\"161740\" column=\"25\"></message>" +
                    "<message level=\"Error\" line=\"162327\" column=\"92\" repeat=\"87\"></message>" +
                    "<message level=\"Error\" line=\"255090\" column=\"25\">c</message>" +
                    "<message level=\"Error\" line=\"255702\" column=\"414\" repeat=\"9\"></message>" +
                "</report>" +
            "return sum($errors//message/(number(@repeat),1)[1])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("NaN", resourceSet.getResource(0).getContent());
    }
}
