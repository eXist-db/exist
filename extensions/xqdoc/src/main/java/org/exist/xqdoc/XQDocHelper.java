package org.exist.xqdoc;

import org.exist.source.Source;
import org.xqdoc.conversion.XQDocController;
import org.xqdoc.conversion.XQDocException;
import org.xqdoc.conversion.XQDocPayload;

import java.io.IOException;


public class XQDocHelper {

    private XQDocController controller;

    public XQDocHelper() throws XQDocException {
        controller = new XQDocController(XQDocController.JAN2007);
        controller.setEncodeURIs(false);
    }

    public String scan(Source source, String name) throws XQDocException, IOException {
        XQDocPayload payload = controller.process(source.getReader(), name);
        return payload.getXQDocXML();
    }
}
