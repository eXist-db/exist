package org.expath.httpclient.model.exist;

import org.apache.http.Header;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.XQueryContext;
import org.expath.httpclient.HeaderSet;
import org.expath.httpclient.HttpClientException;
import org.expath.httpclient.HttpConstants;
import org.expath.httpclient.model.TreeBuilder;
import org.expath.tools.ToolsException;

import javax.xml.XMLConstants;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class EXistTreeBuilder implements TreeBuilder {

    final MemTreeBuilder builder;
    
    public EXistTreeBuilder(final XQueryContext context) {
        builder = context.getDocumentBuilder();
        builder.startDocument();
    }

    //TODO EXPath Caller should send QName, otherwise we duplicate code and reduce reuse!
    @Override
    public void startElem(final String localname) throws ToolsException {
        final String prefix = HttpConstants.HTTP_CLIENT_NS_PREFIX;
        final String uri = HttpConstants.HTTP_CLIENT_NS_URI;
        
        builder.startElement(new QName(localname, uri, prefix), null);
    }

    @Override
    public void attribute(final String localname, final CharSequence value) throws ToolsException {
        builder.addAttribute(new QName(localname, XMLConstants.NULL_NS_URI), value.toString());
    }

    @Override
    public void startContent() throws ToolsException {
        //TODO this is not needed in eXist-db, it is very saxon specific
    }

    @Override
    public void endElem() throws ToolsException {
        builder.endElement();
    }
    
    public DocumentImpl close() {
        builder.endDocument();
        return builder.getDocument();
    }

    @Override
    public void outputHeaders(HeaderSet headers)
            throws HttpClientException
    {
        for ( Header h : headers ) {
            assert h.getName() != null : "Header name cannot be null";
            String name = h.getName().toLowerCase();
            try {
                startElem("header");
                attribute("name", name);
                attribute("value", h.getValue());
                //startContent();
                endElem();
            }
            catch ( ToolsException ex ) {
                throw new HttpClientException("Error building the header " + name, ex);
            }
        }
    }

}