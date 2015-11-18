/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
 *  http://exist-db.org
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
package org.exist.xslt;

import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.Optimizer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xslt.compiler.Factory;
import org.exist.xslt.compiler.XSLElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * <xsl:stylesheet version="1.0"
 *         xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
 * 
 * 	<xsl:import href="..."/>
 * 	<xsl:include href="..."/>
 * 	<xsl:strip-space elements="..."/>
 * 	<xsl:preserve-space elements="..."/>
 * 	<xsl:output method="..."/>
 * 	<xsl:key name="..." match="..." use="..."/>
 * 	<xsl:decimal-format name="..."/>
 * 	<xsl:namespace-alias stylesheet-prefix="..." result-prefix="..."/>
 * 	<xsl:attribute-set name="...">...</xsl:attribute-set>
 * 	<xsl:variable name="...">...</xsl:variable>
 * 	<xsl:param name="...">...</xsl:param>
 * 	<xsl:template match="...">...</xsl:template>
 * 	<xsl:template name="...">...</xsl:template>
 * </xsl:stylesheet>	
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSL {

    private final static Logger LOG = LogManager.getLogger(XSL.class);

    public XSL() {
	}
    
    protected static XSLStylesheet compile(Element source) throws XPathException {
    	try {
        	final BrokerPool pool = BrokerPool.getInstance();
			try(final DBBroker broker = pool.getBroker()) {
				return compile(source, broker);
			}
		} catch (EXistException e) {
			throw new XPathException(e);
		}
    }

    protected static XSLStylesheet compile(Element source, DBBroker broker) throws XPathException {
    	long start = System.currentTimeMillis();
    	
    	XSLElement stylesheet = new XSLElement(source);
    	
    	XSLContext context = new XSLContext(broker.getBrokerPool());
    	
    	context.setDefaultFunctionNamespace(Factory.namespaceURI);
    	
    	XSLStylesheet expr = (XSLStylesheet) stylesheet.compile(context);
    	AnalyzeContextInfo info = new AnalyzeContextInfo((XQueryContext)context);
    	info.setFlags(Expression.IN_NODE_CONSTRUCTOR);
        expr.analyze(info);

        if (context.optimizationsEnabled()) {
            Optimizer optimizer = new Optimizer((XQueryContext) context);
            expr.accept(optimizer);
            if (optimizer.hasOptimized()) {
                context.reset(true);
                expr.resetState(true);
                expr.analyze(new AnalyzeContextInfo());
            }
        }

        System.out.println(ExpressionDumper.dump(expr));
        // Log the query if it is not too large, but avoid
        // dumping huge queries to the log
        if (context.getExpressionCount() < 150) {
            LOG.debug("XSL diagnostics:\n" + ExpressionDumper.dump(expr));
        } else {
            LOG.debug("XSL diagnostics:\n" + "[skipped: more than 150 expressions]");
        }
		

        if (LOG.isDebugEnabled()) {
        	NumberFormat nf = NumberFormat.getNumberInstance();
        	LOG.debug("Compilation took "  +  nf.format(System.currentTimeMillis() - start) + " ms");
        }
        return expr;
	}

    protected static XSLStylesheet compile(InputStream source, DBBroker broker) throws XPathException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);	

			InputSource src = new InputSource(source);
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
			SAXAdapter adapter = new SAXAdapter();
			reader.setContentHandler(adapter);
			reader.parse(src);
		
			Document document = adapter.getDocument();
//			document.setContext(new XSLContext(broker));
			//return receiver.getDocument();
			return compile(document.getDocumentElement(), broker);
		} catch (ParserConfigurationException e) {
        	LOG.debug(e);
			throw new XPathException(e);
		} catch (SAXException e) {
        	LOG.debug(e);
			throw new XPathException(e);
		} catch (IOException e) {
        	LOG.debug(e);
			throw new XPathException(e);
		}
    }
}
