/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
package org.exist.xquery.functions.response;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class StreamBinary extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("stream-binary", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
            "Streams the binary data passed in $a to the current servlet response output stream. The ContentType " +
            "HTTP header is set to the value given in $b. The filename is set to the value given in $c, if no filename is specified then" +
            "that of the current request is used." +
            "This function only works within a servlet context, not within " +
            "Cocoon. Note: the servlet output stream will be closed afterwards and mime-type settings in the prolog " +
            "will not be passed.",
            new SequenceType[] {
                new SequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.ITEM, Cardinality.EMPTY),
            true
        );
    
    public final static FunctionSignature deprecated =
        new FunctionSignature(
            new QName("stream-binary", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
            "Streams the binary data passed in $a to the current servlet response output stream. The ContentType " +
            "HTTP header is set to the value given in $b. This function only works within a servlet context, not within " +
            "Cocoon. Note: the servlet output stream will be closed afterwards and mime-type settings in the prolog " +
            "will not be passed.",
            new SequenceType[] {
                new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.ITEM, Cardinality.EMPTY),
            true,
            "Renamed to response:stream-binary."
        );
    
    public StreamBinary(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if(args[0].isEmpty() || args[1].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        Base64Binary binary = (Base64Binary) args[0].itemAt(0);
        String contentType = args[1].getStringValue();
        String filename = null;
        if(args.length > 2 && !args[2].isEmpty())
        {
        	filename = args[2].getStringValue();
        }
        
        ResponseModule myModule = (ResponseModule)context.getModule(ResponseModule.NAMESPACE_URI);
        // request object is read from global variable $response
        Variable respVar = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
        if(respVar == null || respVar.getValue() == null)
            throw new XPathException(getASTNode(), "No request object found in the current XQuery context.");
        if(respVar.getValue().getItemType() != Type.JAVA_OBJECT)
            throw new XPathException(getASTNode(), "Variable $response is not bound to an Java object.");
        JavaObjectValue respValue = (JavaObjectValue)
            respVar.getValue().itemAt(0);
        if (!"org.exist.http.servlets.HttpResponseWrapper".equals(respValue.getObject().getClass().getName()))
            throw new XPathException(getASTNode(), signature.toString() + 
                    " can only be used within the EXistServlet or XQueryServlet");
        ResponseWrapper response = (ResponseWrapper) respValue.getObject();
        response.setHeader("Content-Type", contentType);
        if(filename != null)
        {
        	response.setHeader("Content-Disposition","inline; filename=" + filename);
        }
        try {
            OutputStream os = new BufferedOutputStream(response.getOutputStream());
            os.write(binary.getBinaryData());
            os.close();
            //commit the response
            response.flushBuffer();
        } catch (IOException e) {
            throw new XPathException(getASTNode(), "IO exception while streaming data: " + e.getMessage(), e);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

}
