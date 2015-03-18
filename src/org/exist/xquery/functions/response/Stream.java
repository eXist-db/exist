/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  $Id: Transform.java 13189 2010-11-12 11:05:05Z shabanovd $
 */
package org.exist.xquery.functions.response;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Option;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class Stream extends BasicFunction {
	
	private static final Logger logger = LogManager.getLogger(Stream.class);

	public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("stream", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
            "Stream can only be used within a servlet context. Itt directly streams its input to the servlet's output stream. " +
            "It should thus be the last statement in the XQuery.",
            new SequenceType[] {
				new FunctionParameterSequenceType("content", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence"),
                new FunctionParameterSequenceType("serialization-options", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization options")},
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
        );

	/**
	 * @param context
	 * @param signature
	 */
	public Stream(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		
		if(args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		final Sequence inputNode = args[0];
		
        final Properties serializeOptions = new Properties();
        final String serOpts = args[1].getStringValue();
        final String[] contents = Option.tokenize(serOpts);
        for (int i = 0; i < contents.length; i++) {
            final String[] pair = Option.parseKeyValuePair(contents[i]);
            if (pair == null)
                {throw new XPathException(this, "Found invalid serialization option: " + pair);}
            logger.info("Setting serialization property: " + pair[0] + " = " + pair[1]);
            serializeOptions.setProperty(pair[0], pair[1]);
        }

        final ResponseModule myModule = (ResponseModule)context.getModule(ResponseModule.NAMESPACE_URI);
        
        // response object is read from global variable $response
        final Variable respVar = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
        
        if(respVar == null)
            {throw new XPathException(this, "No response object found in the current XQuery context.");}
        
        if(respVar.getValue().getItemType() != Type.JAVA_OBJECT)
            {throw new XPathException(this, "Variable $response is not bound to an Java object.");}
        final JavaObjectValue respValue = (JavaObjectValue) respVar.getValue().itemAt(0);
        
        if (!"org.exist.http.servlets.HttpResponseWrapper".equals(respValue.getObject().getClass().getName()))
            {throw new XPathException(this, signature.toString() + " can only be used within the EXistServlet or XQueryServlet");}
        
        final ResponseWrapper response = (ResponseWrapper) respValue.getObject();
        
        final String mediaType = serializeOptions.getProperty("media-type", "application/xml");
        final String encoding = serializeOptions.getProperty("encoding", "UTF-8");
        if(mediaType != null) {
        		response.setContentType(mediaType + "; charset=" + encoding);
        }
        
        Serializer serializer = null;
        
        BrokerPool db = null;
        DBBroker broker = null;
        try {
        	db = BrokerPool.getInstance();
        	broker = db.get(null);
        	
            serializer = broker.getSerializer();
            serializer.reset();
            
            final OutputStream sout = response.getOutputStream();
            final PrintWriter output = new PrintWriter(new OutputStreamWriter(sout, encoding));

        	final SerializerPool serializerPool = SerializerPool.getInstance();

        	final SAXSerializer sax = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
        	try {
        		sax.setOutput(output, serializeOptions);

    	    	serializer.setProperties(serializeOptions);
    	    	serializer.setSAXHandlers(sax, sax);
            	serializer.toSAX(inputNode, 1, inputNode.getItemCount(), false, false);
            	
        	} catch (final SAXException e) {
        		e.printStackTrace();
        		throw new IOException(e);
        	} finally {
        		serializerPool.returnObject(sax);
        	}
        	output.flush();
        	output.close();
            
            //commit the response
            response.flushBuffer();
        } catch (final IOException e) {
            throw new XPathException(this, "IO exception while streaming node: " + e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XPathException(this, "Exception while streaming node: " + e.getMessage(), e);
		} finally {
			if (db != null)
				{db.release(broker);}
		}
        return Sequence.EMPTY_SEQUENCE;
	}
}