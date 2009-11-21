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

    public String scan(Source source) throws XQDocException, IOException {
        XQDocPayload payload = controller.process(source.getReader(), source.getKey().toString());
        return payload.getXQDocXML();
    }
}
