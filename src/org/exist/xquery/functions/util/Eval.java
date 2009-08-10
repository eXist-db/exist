/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.SimpleTimeZone;

import javax.xml.datatype.Duration;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.Namespaces;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.ReferenceNode;
import org.exist.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.StringSource;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Dependency;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.LocalVariable;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.EmptySequence;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.TimeUtils;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @author wolf
 *
 */
public class Eval extends BasicFunction {
	



	protected static final FunctionParameterSequenceType EVAL_CONTEXT_ITEM = new FunctionParameterSequenceType("eval-context-item", Type.ITEM, Cardinality.ZERO_OR_ONE, "the context item against which the expression will be evaluated");

	private static final String evalArgumentText = "The expression to be evaluated.  If it is of type xs:string, the function " +
                "tries to execute this string as the query. If the first argument is of type xs:anyURI, " +
                "the function will try to load the query from the resource to which the URI resolves. " +
                "If the URI has no scheme, it is assumed that the query is stored in the db and the " +
                "URI is interpreted as a database path. This is the same as calling " +
                "util:eval(xs:anyURI('xmldb:exist:///db/test/test.xq')). " +
                //TODO : to be discussed ; until now, it's been used with a null context
				"The query inherits the current execution context, i.e. all " +
				"namespace declarations and variable declarations are visible from within the " +
				"inner expression. " +
                "The function returns an empty sequence if a whitespace string is passed.";
	
	private static final String contextArgumentText = "The query inherits the context described by the XML fragment in this parameter. " +
				"It should have the format:\n" +
				"<static-context>\n" +
				"\t<output-size-limit value=\"-1\">\n" +
				"\t<unbind-namespace uri=\"http://exist.sourceforge.net/NS/exist\"/>\n" +
				"\t<current-dateTime value=\"dateTime\"/>\n" +
				"\t<implicit-timezone value=\"duration\"/>\n" +
				"\t<variable name=\"qname\">variable value</variable>\n" +
				"\t<default-context>explicitly provide default context here</default-context>\n" +
                                "\t<mapModule namespace=\"uri\" uri=\"uri_to_module\"/>\n" +
				"</static-context>.\n";
	
	protected static final FunctionParameterSequenceType EVAL_ARGUMENT = new FunctionParameterSequenceType("expression", Type.ITEM, Cardinality.EXACTLY_ONE, evalArgumentText); 
	protected static final FunctionParameterSequenceType INLINE_CONTEXT = new FunctionParameterSequenceType("inline-context", Type.ITEM, Cardinality.ZERO_OR_MORE, "The inline context");
	protected static final FunctionParameterSequenceType CONTEXT_ARGUMENT = new FunctionParameterSequenceType("context", Type.NODE, Cardinality.ZERO_OR_ONE, contextArgumentText); 
	protected static final FunctionParameterSequenceType CACHE_FLAG = new FunctionParameterSequenceType("cache-flag", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The flag for whether the compiled query should be cached.  The cached query will be globally available within the db instance.");

	protected static final FunctionReturnSequenceType RETURN_NODE_TYPE = new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the results of the evaluated XPath/XQuery expression");
	protected static final FunctionReturnSequenceType RETURN_ITEM_TYPE = new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the evaluated XPath/XQuery expression");

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("eval", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates an XPath/XQuery expression. ",
				new SequenceType[] { EVAL_ARGUMENT },
				RETURN_NODE_TYPE),
		new FunctionSignature(
				new QName("eval", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates an XPath/XQuery expression. ",
				new SequenceType[] { EVAL_ARGUMENT, CACHE_FLAG },
				RETURN_NODE_TYPE),
		new FunctionSignature(
				new QName("eval-with-context", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates an XPath/XQuery expression. " +
				"",
				new SequenceType[] { EVAL_ARGUMENT, CONTEXT_ARGUMENT, CACHE_FLAG },
				RETURN_NODE_TYPE),
		new FunctionSignature(
				new QName("eval-with-context", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates an XPath/XQuery expression.",
				new SequenceType[] {  EVAL_ARGUMENT, CONTEXT_ARGUMENT, CACHE_FLAG, EVAL_CONTEXT_ITEM },
				RETURN_NODE_TYPE),
		new FunctionSignature(
				new QName("eval-inline", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates an XPath/XQuery expression.",
				new SequenceType[] { INLINE_CONTEXT, EVAL_ARGUMENT },
				RETURN_ITEM_TYPE),
		new FunctionSignature(
				new QName("eval-inline", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates an XPath/XQuery expression.",
				new SequenceType[] { INLINE_CONTEXT, EVAL_ARGUMENT, CACHE_FLAG },
				RETURN_ITEM_TYPE)
	};
	
	/**
	 * @param context
	 * @param signature
	 */
	public Eval(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }

		int argCount = 0;
		Sequence exprContext = null;
                Sequence initContextSequence = null;

		if (isCalledAs("eval-inline")) {
			// the current expression context
			exprContext = args[argCount++];
		}
		// get the query expression
        Item expr = args[argCount++].itemAt(0);
        Source querySource;
        if (Type.subTypeOf(expr.getType(), Type.ANY_URI)) {
            querySource = loadQueryFromURI(expr);
        } else {
        	String queryStr = expr.getStringValue();
            if ("".equals(queryStr.trim()))
              return new EmptySequence();
            querySource = new StringSource(queryStr);
        }
		
		NodeValue contextInit = null;
		if (isCalledAs("eval-with-context")) {
			// set the context initialization param for later use
			contextInit = (NodeValue) args[argCount++].itemAt(0);
		}
		
		// should the compiled query be cached?
		boolean cache = false;
		if (argCount < getArgumentCount())
			cache = ((BooleanValue)args[argCount].itemAt(0)).effectiveBooleanValue();
		
		// save some context properties
        context.pushNamespaceContext();
        LocalVariable mark = context.markLocalVariables(false);

        DocumentSet oldDocs = context.getStaticallyKnownDocuments();
		if (exprContext != null)
			context.setStaticallyKnownDocuments(exprContext.getDocumentSet());
		
		if (context.isProfilingEnabled(2))
			context.getProfiler().start(this, "eval: " + expr);

        // fixme! - hook for debugger here /ljo
		
		Sequence sequence = null;

		XQuery xquery = context.getBroker().getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
		CompiledXQuery compiled = cache ? pool.borrowCompiledXQuery(context.getBroker(), querySource) : null;
		XQueryContext innerContext;
		if (contextInit != null) {
			// eval-with-context: initialize a new context
			innerContext = xquery.newContext(context.getAccessContext());
			initContextSequence = initContext(contextInit.getNode(), innerContext);
		} else {
			// use the existing outer context
            // TODO: check if copying the static context would be sufficient???                    
			innerContext = context.copyContext();
            innerContext.setShared(true);
            //innerContext = context;
        }
        // fixme! - hook for debugger here /ljo
        try {
			if(compiled == null) {
                compiled = xquery.compile(innerContext, querySource);
			} else {
                compiled.getContext().updateContext(innerContext);
			}
			
			if (this.getArgumentCount() == 4) {
				NodeValue contextItem = (NodeValue)args[3].itemAt(0);
				if (contextItem != null) {
					//TODO : sort this out
					if (exprContext != null)
						LOG.warn("exprContext and contextItem are not null");
					exprContext = contextItem.toSequence();

				}
			}	
				
            if (initContextSequence != null) {
                LOG.info("there now");
                exprContext=initContextSequence;                
            }

            sequence = xquery.execute(compiled, exprContext, false);
            ValueSequence newSeq = new ValueSequence();
            boolean hasSupplements = false;
            for (int i = 0;  i < sequence.getItemCount(); i++) {
                //if (sequence.itemAt(i) instanceof StringValue) {
                if (Type.subTypeOf(sequence.itemAt(i).getType(),Type.STRING)) {
                    newSeq.add(new StringValue(((StringValue) sequence.itemAt(i)).getStringValue(true)));
                    hasSupplements = true;
                } else {
                    newSeq.add(sequence.itemAt(i));
                }
            }
            if (hasSupplements) {
                sequence = newSeq;
            }
			return sequence;


		} catch (XPathException e) {
			try {
				e.prependMessage("Error while evaluating expression: " + querySource.getContent() + ". ");
			} catch (IOException e1) {
			}
            e.setLocation(line, column);
			throw e;
		} catch (IOException e) {
			throw new XPathException(this, e.getMessage(), e);
		} finally {
            if (innerContext != this.context)
                innerContext.reset();
            if (compiled != null) {
				if (cache)
					pool.returnCompiledXQuery(querySource, compiled);
				else
					compiled.reset();
			}
			if (oldDocs != null)
				context.setStaticallyKnownDocuments(oldDocs);
            context.popLocalVariables(mark);
            context.popNamespaceContext();
			if (context.isProfilingEnabled(2))
				context.getProfiler().end(this, "eval: " + expr, sequence);
		}
	}

    /**
     * @param expr
     * @return
     * @throws XPathException
     * @throws NullPointerException
     * @throws IllegalArgumentException
     */
    private Source loadQueryFromURI(Item expr) throws XPathException, NullPointerException, IllegalArgumentException {
        String location = expr.getStringValue();
        Source querySource = null;
        if (location.indexOf(':') < 0 || location.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            try {
                XmldbURI locationUri = XmldbURI.xmldbUriFor(location);
                
                // If location is relative (does not contain any / and does
                // not start with . or .. then the path of the module need to
                // be added.
                if(location.indexOf("/") <0 || location.startsWith(".")){
                    XmldbURI moduleLoadPathUri = XmldbURI.xmldbUriFor(context.getModuleLoadPath());
                    locationUri = moduleLoadPathUri.resolveCollectionPath(locationUri);
                }
                
                DocumentImpl sourceDoc = null;
                try {
                    sourceDoc = context.getBroker().getXMLResource(locationUri.toCollectionPathURI(), Lock.READ_LOCK);
                    if (sourceDoc == null)
                        throw new XPathException(this, "source for module " + location + " not found in database");
                    if (sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !sourceDoc.getMetadata().getMimeType().equals("application/xquery"))
                        throw new XPathException(this, "source for module " + location + " is not an XQuery or " +
                        "declares a wrong mime-type");
                    querySource = new DBSource(context.getBroker(), (BinaryDocument) sourceDoc, true);
                } catch (PermissionDeniedException e) {
                    throw new XPathException(this, "permission denied to read module source from " + location);
                } finally {
                    if(sourceDoc != null)
                        sourceDoc.getUpdateLock().release(Lock.READ_LOCK);
                }
            } catch(URISyntaxException e) {
                throw new XPathException(this, e.getMessage(), e);
            }
        } else {
            // No. Load from file or URL
            try {
                //TODO: use URIs to ensure proper resolution of relative locations
                querySource = SourceFactory.getSource(context.getBroker(), context.getModuleLoadPath(), location, true);
            } catch (MalformedURLException e) {
                throw new XPathException(this, "source location for query at " + location + " should be a valid URL: " +
                        e.getMessage());
            } catch (IOException e) {
                throw new XPathException(this, "source for query at " + location + " not found: " +
                        e.getMessage());
            } catch (PermissionDeniedException e) {
            	throw new XPathException(this, "Permission denied to access query at " + location + " : " +
                        e.getMessage());
            }
        }
        return querySource;
    }

	/**
	 * Read to optional static-context fragment to initialize
	 * the context.
	 * 
	 * @param root
	 * @param innerContext
	 * @throws XPathException
	 */
	private Sequence initContext(Node root, XQueryContext innerContext) throws XPathException {
		
                NodeList cl = root.getChildNodes();
                Sequence result = null;
                for (int i = 0; i < cl.getLength(); i++) {
			Node child = cl.item(i);
			//TODO : more check on attributes existence and on their values
			if (child.getNodeType() == Node.ELEMENT_NODE &&	"variable".equals(child.getLocalName())) {
				Element elem = (Element) child;
                                String qname = elem.getAttribute("name");
                                String source = elem.getAttribute("source");
                                NodeValue value;
                                if (source != null && source.length() > 0) {
                                    // load variable contents from URI
                                    value = loadVarFromURI(source);
                                } else {
                                    value = (NodeValue) elem.getFirstChild();
                                    if (value instanceof ReferenceNode)
                                        value = ((ReferenceNode) value).getReference();
                                }
                                String type = elem.getAttribute("type");
				if (type != null && Type.subTypeOf(Type.getType(type), Type.ATOMIC)) {
					innerContext.declareVariable(qname, value.atomize().convertTo(Type.getType(type)));
				} else {
					innerContext.declareVariable(qname, value);
				}				
			} else if (child.getNodeType() == Node.ELEMENT_NODE &&	"output-size-limit".equals(child.getLocalName())) {
				Element elem = (Element) child;
				//TODO : error check
				innerContext.getWatchDog().setMaxNodes(Integer.valueOf(elem.getAttribute("value")).intValue());
			} else if (child.getNodeType() == Node.ELEMENT_NODE &&	"current-dateTime".equals(child.getLocalName())) {
				Element elem = (Element) child;
				//TODO : error check
				DateTimeValue dtv = new DateTimeValue(elem.getAttribute("value"));
	        	innerContext.setCalendar(dtv.calendar);	        	
			} else if (child.getNodeType() == Node.ELEMENT_NODE &&	"implicit-timezone".equals(child.getLocalName())) {
				Element elem = (Element) child;
				//TODO : error check
				Duration duration = TimeUtils.getInstance().newDuration(elem.getAttribute("value")); 	        
	        	innerContext.setTimeZone(new SimpleTimeZone((int)duration.getTimeInMillis(new Date()), "XQuery context"));      	
			} else if (child.getNodeType() == Node.ELEMENT_NODE &&	"unbind-namespace".equals(child.getLocalName())) {
				Element elem = (Element) child;
				//TODO : error check
				if (elem.getAttribute("uri") != null) {		
					innerContext.removeNamespace(elem.getAttribute("uri"));
				}
			} else if (child.getNodeType() == Node.ELEMENT_NODE &&	"staticallyKnownDocuments".equals(child.getLocalName())) {
				Element elem = (Element) child;
				//TODO : iterate over the children
				NodeValue value = (NodeValue) elem.getFirstChild();
				if (value instanceof ReferenceNode)
					value = ((ReferenceNode) value).getReference();	
				XmldbURI[] pathes = new XmldbURI[1];
				//TODO : aggregate !
				//TODO : cleanly seperate the statically know docollection and documents
				pathes[0] = XmldbURI.create(value.getStringValue());
				innerContext.setStaticallyKnownDocuments(pathes);
			} else if (child.getNodeType() == Node.ELEMENT_NODE &&	"mapModule".equals(child.getLocalName())) {
				Element elem = (Element) child;
				//TODO : error check
				if (elem.getAttribute("namespace") != null && elem.getAttribute("uri") != null) {		
					innerContext.mapModule(elem.getAttribute("namespace"), 
							XmldbURI.create(elem.getAttribute("uri")));
				}				
			} else if (child.getNodeType() == Node.ELEMENT_NODE &&	"default-context".equals(child.getLocalName())) {
                        	Element elem = (Element) child;
                                NodeValue nodevalue = (NodeValue) elem;
                                result = nodevalue.toSequence();
                        }
		}
                
            return result;
	}

    private NodeImpl loadVarFromURI(String uri) throws XPathException {
		try {
			URL url = new URL(uri);
			InputStreamReader isr = new InputStreamReader(url.openStream(), "UTF-8");
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(isr);
            SAXParser parser = factory.newSAXParser();
            XMLReader xr = parser.getXMLReader();
            SAXAdapter adapter = new SAXAdapter(context);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);
            isr.close();
            return (NodeImpl) adapter.getDocument();
		} catch (MalformedURLException e) {
			throw new XPathException(this, e.getMessage());
		} catch (IOException e) {
			throw new XPathException(this, e.getMessage());
		} catch (SAXException e) {
            throw new XPathException(this, e.getMessage());
        } catch (ParserConfigurationException e) {
            throw new XPathException(this, e.getMessage());
        }
    }
}
