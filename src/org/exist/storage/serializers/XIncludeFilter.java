/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
*
*  This program is free software; you can redistribute it and/or
*  modify it under the terms of the GNU Lesser General Public License
*  as published by the Free Software Foundation; either version 2
*  of the License, or (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU Lesser General Public License for more details.
*
*  You should have received a copy of the GNU Lesser General Public License
*  along with this program; if not, write to the Free Software
*  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
* 
*  $Id$
*/
package org.exist.storage.serializers;

import org.apache.log4j.Logger;
import org.exist.dom.INodeHandle;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.XQueryPool;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * A filter that listens for XInclude elements in the stream
 * of events generated by the {@link org.exist.storage.serializers.Serializer}.
 * 
 * XInclude elements are expanded at the position where they were found.
 */
public class XIncludeFilter implements Receiver {

	private final static Logger LOG = Logger.getLogger(XIncludeFilter.class);

	public final static String XINCLUDE_NS = "http://www.w3.org/2001/XInclude";

    private final static QName HREF_ATTRIB = new QName("href", "");

    private static final QName XPOINTER_ATTRIB = new QName("xpointer", "");

    private static final String XI_INCLUDE = "include";

    private static final String XI_FALLBACK = "fallback";

    private static class ResourceError extends Exception {

		private static final long serialVersionUID = 6371228263379093678L;

		private ResourceError(String message, Throwable cause) {
            super(message, cause);
        }

        private ResourceError(String message) {
            super(message);
        }
    }

    private Receiver receiver;
    private Serializer serializer;
    private DocumentImpl document = null;
    private String moduleLoadPath = null;
    
    private HashMap<String, String> namespaces = new HashMap<String, String>(10);

    private boolean inFallback = false;

    private ResourceError error = null;

    public XIncludeFilter(Serializer serializer, Receiver receiver) {
		this.receiver = receiver;
		this.serializer = serializer;
	}

	public XIncludeFilter(Serializer serializer) {
		this(serializer, null);
	}

	public void setReceiver(Receiver handler) {
		this.receiver = handler;
	}

	public Receiver getReceiver() {
		return receiver;
	}

	public void setDocument(DocumentImpl doc) {
		this.document = doc;
        this.inFallback = false;
        this.error = null;
    }

    public void setModuleLoadPath(String path) {
        this.moduleLoadPath = path;
    }
    
    /* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#characters(java.lang.CharSequence)
	 */
	public void characters(CharSequence seq) throws SAXException {
        if (!inFallback || error != null)
            {receiver.characters(seq);}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#comment(char[], int, int)
	 */
	public void comment(char[] ch, int start, int length) throws SAXException {
        if (!inFallback || error != null)
            {receiver.comment(ch, start, length);}
	}
	
	/**
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {
		receiver.endDocument();
	}

	/**
	 * @see org.exist.util.serializer.Receiver#endElement(org.exist.dom.QName)
	 */
    public void endElement(QName qname) throws SAXException {
        if (XINCLUDE_NS.equals(qname.getNamespaceURI())) {
            if (XI_FALLBACK.equals(qname.getLocalPart())) {
                inFallback = false;
                // clear error
                error = null;
            } else if (XI_INCLUDE.equals(qname.getLocalPart()) && error != null) {
                // found an error, but there was no fallback element.
                // throw the exception now
                final Exception e = error;
                error = null;
                throw new SAXException(e.getMessage(), e);
            }
        } else if (!inFallback || error != null)
            {receiver.endElement(qname);}
    }
	
	public void endPrefixMapping(String prefix) throws SAXException {
		namespaces.remove(prefix);
		receiver.endPrefixMapping(prefix);
	}

	/**
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data) throws SAXException {
        if (!inFallback || error != null)
            {receiver.processingInstruction(target, data);}
	}

    /**
     * @see org.exist.util.serializer.Receiver#cdataSection(char[], int, int)
     */
    public void cdataSection(char[] ch, int start, int len) throws SAXException {
        if (!inFallback || error != null)
            {receiver.cdataSection(ch, start, len);}
    }
    
	/**
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException {
		receiver.startDocument();
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#attribute(org.exist.dom.QName, java.lang.String)
	 */
	public void attribute(QName qname, String value) throws SAXException {
        if (!inFallback || error != null)
            {receiver.attribute(qname, value);}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#startElement(org.exist.dom.QName, org.exist.util.serializer.AttrList)
	 */
	public void startElement(QName qname, AttrList attribs) throws SAXException {
		if (qname.getNamespaceURI() != null && qname.getNamespaceURI().equals(XINCLUDE_NS)) {
			if (qname.getLocalPart().equals(XI_INCLUDE)) {
                if (LOG.isDebugEnabled())
                    {LOG.debug("processing include ...");}
                try {
                    processXInclude(attribs.getValue(HREF_ATTRIB), attribs.getValue(XPOINTER_ATTRIB));
                } catch (ResourceError resourceError) {
                    if (LOG.isDebugEnabled())
                        {LOG.debug(resourceError.getMessage(), resourceError);}
                    error = resourceError;
				}
            } else if (qname.getLocalPart().equals(XI_FALLBACK)) {
                inFallback = true;
            }
        } else if (!inFallback || error != null) {
			//LOG.debug("start: " + qName);
			receiver.startElement(qname, attribs);
		}
	}
	
	public void documentType(String name, String publicId, String systemId) 
	throws SAXException {
		receiver.documentType(name, publicId, systemId);
	}

    public void highlightText(CharSequence seq) {
        // not supported with this receiver
    }

    protected void processXInclude(String href, String xpointer) throws SAXException, ResourceError {
        if(href == null)
            {throw new SAXException("No href attribute found in XInclude include element");}
        // save some settings
        DocumentImpl prevDoc = document;
        boolean createContainerElements = serializer.createContainerElements;
        serializer.createContainerElements = false;

        //The following comments are the basis for possible external documents
        XmldbURI docUri = null;
        try {
            docUri = XmldbURI.xmldbUriFor(href);
            /*
               if(!stylesheetUri.toCollectionPathURI().equals(stylesheetUri)) {
                   externalUri = stylesheetUri.getXmldbURI();
               }
               */
        } catch (final URISyntaxException e) {
            //could be an external URI!
        }

        // parse the href attribute
        LOG.debug("found href=\"" + href + "\"");
        //String xpointer = null;
        //String docName = href;

        Map<String, String> params = null;
        DocumentImpl doc = null;
        org.exist.dom.memtree.DocumentImpl memtreeDoc = null;
        boolean xqueryDoc = false;
        
        if (docUri != null) {
            final String fragment = docUri.getFragment();
            if (!(fragment == null || fragment.length() == 0))
                {throw new SAXException("Fragment identifiers must not be used in an xinclude href attribute. To specify an " +
                        "xpointer, use the xpointer attribute.");}

            // extract possible parameters in the URI
            params = null;
            final String paramStr = docUri.getQuery();
            if (paramStr != null) {
                params = processParameters(paramStr);
                // strip query part
                docUri = XmldbURI.create(docUri.getRawCollectionPath());
            }

            // if docName has no collection specified, assume
            // current collection

            // Patch 1520454 start
            if (!docUri.isAbsolute() && document != null) {
                final String base = document.getCollection().getURI() + "/";
                final String child = "./" + docUri.toString();

                final URI baseUri = URI.create(base);
                final URI childUri = URI.create(child);

                final URI uri = baseUri.resolve(childUri);
                docUri = XmldbURI.create(uri);
            }
            // Patch 1520454 end

            // retrieve the document
            doc = null;
            try {
                doc = serializer.broker.getResource(docUri, Permission.READ);

            } catch (final PermissionDeniedException e) {
                LOG.warn("permission denied", e);
                throw new ResourceError("Permission denied to read xincluded resource", e);
            }

            /* Check if the document is a stored XQuery */
            if (doc != null && doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                xqueryDoc = "application/xquery".equals(doc.getMetadata().getMimeType());
            }
        }
        // The document could not be found: check if it points to an external resource
        if (docUri == null || (doc == null && !docUri.isAbsolute())) {
            try {
                URI externalUri = new URI(href);
                final String scheme = externalUri.getScheme();
                // If the URI has no scheme is specified,
                // we have to check if it is a relative path, and if yes, try to
                // interpret it relative to the moduleLoadPath property of the current
                // XQuery context.
                if (scheme == null && moduleLoadPath != null) {
                    final String path = externalUri.getSchemeSpecificPart();
                    File f = new File(path);
                    if (!f.isAbsolute()) {
                        if (moduleLoadPath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
                            final XmldbURI parentUri = XmldbURI.create(moduleLoadPath);
                            docUri = parentUri.append(path);
                            try {
                                doc = (DocumentImpl) serializer.broker.getXMLResource(docUri);
                                if(doc != null && !doc.getPermissions().validate(serializer.broker.getSubject(), Permission.READ))
                                    {throw new ResourceError("Permission denied to read xincluded resource");}
                            } catch (final PermissionDeniedException e) {
                                LOG.warn("permission denied", e);
                                throw new ResourceError("Permission denied to read xincluded resource", e);
                            }
                        } else {
                            f = new File(moduleLoadPath, path);
                            externalUri = f.toURI();
                        }
                    }
                }
                if (doc == null)
                    {memtreeDoc = parseExternal(externalUri);}
            } catch (final IOException e) {
                throw new ResourceError("XInclude: failed to read document at URI: " + href +
                    ": " + e.getMessage(), e);
            } catch (final PermissionDeniedException e) {
                throw new ResourceError("XInclude: failed to read document at URI: " + href +
                    ": " + e.getMessage(), e);
            } catch (final ParserConfigurationException e) {
                throw new ResourceError("XInclude: failed to read document at URI: " + href +
                    ": " + e.getMessage(), e);
            } catch (final URISyntaxException e) {
                throw new ResourceError("XInclude: failed to read document at URI: " + href +
                    ": " + e.getMessage(), e);
            }
        }

        /* if document has not been found and xpointer is
               * null, throw an exception. If xpointer != null
               * we retry below and interpret docName as
               * a collection.
               */
        if (doc == null && memtreeDoc == null && xpointer == null)
            {throw new ResourceError("document " + docUri + " not found");}

        if (xpointer == null && !xqueryDoc) {
            // no xpointer found - just serialize the doc
            if (memtreeDoc == null)
                {serializer.serializeToReceiver(doc, false);}
            else
                {serializer.serializeToReceiver(memtreeDoc, false);}
        } else {
            // process the xpointer or the stored XQuery
            try {
                Source source;
                if (xpointer == null)
                    {source = new DBSource(serializer.broker, (BinaryDocument) doc, true);}
                else {
                    xpointer = checkNamespaces(xpointer);
                    source = new StringSource(xpointer);
                }
                final XQuery xquery = serializer.broker.getXQueryService();
                final XQueryPool pool = xquery.getXQueryPool();
                XQueryContext context;
                CompiledXQuery compiled = pool.borrowCompiledXQuery(serializer.broker, source);
                if (compiled != null)
                    {context = compiled.getContext();}
                else
                    {context = xquery.newContext(AccessContext.XINCLUDE);}
                context.declareNamespaces(namespaces);
                context.declareNamespace("xinclude", XINCLUDE_NS);
                
                //setup the http context if known
                if(serializer.httpContext != null)
                {
                	if(serializer.httpContext.getRequest() != null)
                		{context.declareVariable(RequestModule.PREFIX + ":request", serializer.httpContext.getRequest());}
                	
                	if(serializer.httpContext.getResponse() != null)
                		{context.declareVariable(ResponseModule.PREFIX + ":response", serializer.httpContext.getResponse());}
                	
                	if(serializer.httpContext.getSession() != null)
                		{context.declareVariable(SessionModule.PREFIX + ":session", serializer.httpContext.getSession());}
                }
                
                //TODO: change these to putting the XmldbURI in, but we need to warn users!
                if(document!=null){
                    context.declareVariable("xinclude:current-doc", document.getFileURI().toString());
                    context.declareVariable("xinclude:current-collection", document.getCollection().getURI().toString());
                }
                
                if (xpointer != null) {
                    if(doc != null)
                        {context.setStaticallyKnownDocuments(new XmldbURI[] { doc.getURI() } );}
                    else if (docUri != null)
                        {context.setStaticallyKnownDocuments(new XmldbURI[] { docUri });}
                }

                // pass parameters as variables
                if (params != null) {
                    for (final Map.Entry<String, String> entry : params.entrySet()) {
                        context.declareVariable(entry.getKey(), entry.getValue());
                    }
                }

                if(compiled == null) {
                    try {
                        compiled = xquery.compile(context, source, xpointer != null);
                    } catch (final IOException e) {
                        throw new SAXException("I/O error while reading query for xinclude: " + e.getMessage(), e);
                    }
                }
                LOG.info("xpointer query: " + ExpressionDumper.dump((Expression) compiled));
                Sequence contextSeq = null;
                if (memtreeDoc != null)
                    {contextSeq = memtreeDoc;}
                final Sequence seq = xquery.execute(compiled, contextSeq);

                if(Type.subTypeOf(seq.getItemType(), Type.NODE)) {
                    if (LOG.isDebugEnabled())
                        {LOG.debug("xpointer found: " + seq.getItemCount());}

                    NodeValue node;
                    for (final SequenceIterator i = seq.iterate(); i.hasNext();) {
                        node = (NodeValue) i.nextItem();
                        serializer.serializeToReceiver(node, false);
                    }
                } else {
                    String val;
                    for (int i = 0; i < seq.getItemCount(); i++) {
                        val = seq.itemAt(i).getStringValue();
                        characters(val);
                    }
                }

            } catch (final XPathException e) {
                LOG.warn("xpointer error", e);
                throw new SAXException("Error while processing XInclude expression: " + e.getMessage(), e);
            } catch (final PermissionDeniedException e) {
                LOG.warn("xpointer error", e);
                throw new SAXException("Error while processing XInclude expression: " + e.getMessage(), e);
			}
        }
        // restore settings
        document = prevDoc;
        serializer.createContainerElements = createContainerElements;
    }

    private org.exist.dom.memtree.DocumentImpl parseExternal(URI externalUri) throws IOException, ResourceError, PermissionDeniedException, ParserConfigurationException, SAXException {
        final URLConnection con = externalUri.toURL().openConnection();
        if(con instanceof HttpURLConnection)
        {
            final HttpURLConnection httpConnection = (HttpURLConnection)con;
            if(httpConnection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
            {
                // Special case: '404'
                throw new ResourceError("XInclude: no document found at URI: " + externalUri.toString());
            }
            else if(httpConnection.getResponseCode() != HttpURLConnection.HTTP_OK)
            {
                //TODO : return another type
                throw new PermissionDeniedException("Server returned code " + httpConnection.getResponseCode());
            }
        }

        // we use eXist's in-memory DOM implementation
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final InputSource src = new InputSource(con.getInputStream());
        final SAXParser parser = factory.newSAXParser();
        final XMLReader reader = parser.getXMLReader();
        final SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.parse(src);
        final org.exist.dom.memtree.DocumentImpl doc =
                (org.exist.dom.memtree.DocumentImpl)adapter.getDocument();
        doc.setDocumentURI(externalUri.toString());
        return doc;
    }

    /**
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		namespaces.put(prefix, uri);
		receiver.startPrefixMapping(prefix, uri);
	}

	/**
	 * Process xmlns() schema. We process these here, because namespace mappings should
	 * already been known when parsing the xpointer() expression.
	 */
	private String checkNamespaces(String xpointer) throws XPathException {
		int p0;
		while((p0 = xpointer.indexOf("xmlns(")) != Constants.STRING_NOT_FOUND) {
			if(p0 < 0)
				{return xpointer;}
			final int p1 = xpointer.indexOf(')', p0 + 6);
			if(p1 < 0)
				{throw new XPathException("expected ) for xmlns()");}
			final String mapping = xpointer.substring(p0 + 6, p1);
			xpointer = xpointer.substring(0, p0) + xpointer.substring(p1 + 1);
			final StringTokenizer tok = new StringTokenizer(mapping, "= \t\n");
			if(tok.countTokens() < 2)
				{throw new XPathException("expected prefix=namespace mapping in " + mapping);}
			final String prefix = tok.nextToken();
			final String namespaceURI = tok.nextToken();
			namespaces.put(prefix, namespaceURI);
		}
		return xpointer;
	}
    
    protected HashMap<String, String> processParameters(String args) {
        final HashMap<String, String> parameters = new HashMap<String, String>();
        String param;
        String value; 
        int start = 0;
        int end = 0;
        final int l = args.length();
        while ((start < l) && (end < l)) {
            while ((end < l) && (args.charAt(end++) != '='))
                ;
            if (end == l)
                {break;}
            param = args.substring(start, end - 1);
            start = end;
            while ((end < l) && (args.charAt(end++) != '&'))
                ;
            if (end == l)
                {value = args.substring(start);}
            else
                {value = args.substring(start, end - 1);}
            start = end;
            try {
                param = URLDecoder.decode(param, "UTF-8");
                value = URLDecoder.decode(value, "UTF-8");
                LOG.debug("parameter: " + param + " = " + value);
                parameters.put(param, value);
            } catch (final UnsupportedEncodingException e) {
                LOG.warn(e.getMessage(), e);
            }
        }
        return parameters;
    }

    public void setCurrentNode(INodeHandle node) {
        //ignored
    }
    
    public Document getDocument() {
    	//ignored
    	return null;
    }
}
