package org.exist.xquery.modules.xslfo;

import java.io.OutputStream;
import java.util.Properties;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public interface ProcessorAdapter {

    public ContentHandler getContentHandler(DBBroker broker, NodeValue configFile, Properties parameters, String mimeType, OutputStream os) throws XPathException, SAXException;

    public void cleanup();
}
