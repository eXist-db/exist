package org.exist.xquery.modules.xslfo;

import com.renderx.xep.FOTarget;
import com.renderx.xep.FormatterImpl;
import com.renderx.xep.lib.FormatterException;
import java.io.OutputStream;
import java.util.Properties;
import javax.xml.transform.dom.DOMSource;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class RenderHouseXepProcessorAdapter implements ProcessorAdapter {

    private FormatterImpl formatter = null;


    @Override
    public ContentHandler getContentHandler(DBBroker broker, NodeValue configFile, Properties parameters, String mimeType, OutputStream os) throws XPathException, SAXException {

        if(configFile == null) {
            throw new XPathException("XEP requires a configuration file");
        }

        try {
            if(parameters == null) {
                formatter = new FormatterImpl(new DOMSource((Node)configFile));
            } else {
                formatter = new FormatterImpl(new DOMSource((Node)configFile), parameters);
            }
            String backendType = mimeType.substring(mimeType.indexOf("/")+1).toUpperCase();
            FOTarget foTarget = new FOTarget(os, backendType);

            return formatter.createContentHandler(null, foTarget);
        } catch (FormatterException fe) {
            throw new SAXException(fe.getMessage(), fe);
        }
    }

    @Override
    public void cleanup() {
        if(formatter != null) {
            formatter.cleanup();
        }
     }
}