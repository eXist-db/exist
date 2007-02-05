package org.exist.performance.actions;

import org.exist.performance.AbstractAction;
import org.exist.performance.Runner;
import org.exist.performance.Connection;
import org.exist.EXistException;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.XUpdateQueryService;

import javax.xml.transform.TransformerException;
import java.io.StringWriter;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: 05.02.2007
 * Time: 12:21:01
 * To change this template use File | Settings | File Templates.
 */
public class XUpdateAction extends AbstractAction {

    private String collectionPath;
    private String resource = null;
    private String xupdate;
    private int modifications = 0;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        super.configure(runner, parent, config);
        if (!config.hasAttribute("collection"))
            throw new EXistException(StoreFromFile.class.getName() + " requires an attribute 'collection'");
        collectionPath = config.getAttribute("collection");
        if (config.hasAttribute("resource"))
            resource = config.getAttribute("resource");

        xupdate = getContent(config);
    }

    public void execute(Connection connection) throws XMLDBException, EXistException {
        Collection collection = connection.getCollection(collectionPath);
        if (collection == null)
            throw new EXistException("collection " + collectionPath + " not found");
        XUpdateQueryService service = (XUpdateQueryService) collection.getService("XUpdateQueryService", "1.0");

        if (resource == null) {
            modifications = (int) service.update(xupdate);
        } else {
            modifications = (int) service.updateResource(resource, xupdate);
        }
    }

    public String getLastResult() {
        return Integer.toString(modifications);
    }
}
