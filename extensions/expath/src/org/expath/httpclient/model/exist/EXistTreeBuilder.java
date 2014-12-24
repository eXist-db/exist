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

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class EXistTreeBuilder implements TreeBuilder {

    final MemTreeBuilder builder;
    
    public EXistTreeBuilder(XQueryContext context) {
        builder = context.getDocumentBuilder();
        builder.startDocument();
    }
    
    //TODO this should NOT be in this interface! It should be in the EXPath Caller, otherwise we mix concerns and duplicate code.
    @Override
    public void outputHeaders(HeaderSet headers) throws HttpClientException {
        for (Header h : headers ) {
            assert h.getName() != null : "Header name cannot be null";
            startElem("header");
            attribute("name", h.getName().toLowerCase());
            attribute("value", h.getValue());
            startContent();
            endElem();
        }
    }

    //TODO EXPath Caller should send QName, otherwise we duplicate code and reduce reuse!
    @Override
    public void startElem(String localname) throws HttpClientException {
        
        final String prefix = HttpConstants.HTTP_CLIENT_NS_PREFIX;
        final String uri = HttpConstants.HTTP_CLIENT_NS_URI;
        
        builder.startElement(new QName(localname, uri, prefix), null);
    }

    @Override
    public void attribute(String localname, CharSequence value) throws HttpClientException {
        builder.addAttribute(new QName(localname), value.toString());
    }

    @Override
    public void startContent() throws HttpClientException {
        //TODO this is not needed in eXist-db, it is very saxon specific
    }

    @Override
    public void endElem() throws HttpClientException {
        builder.endElement();
    }
    
    public DocumentImpl close() {
        builder.endDocument();
        return builder.getDocument();
    }
}