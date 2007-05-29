/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

package org.exist.xquery.functions.validation;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;

import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Validation extends BasicFunction  {
    
    private static final String simpleFunctionTxt=
        "Validate document specified by $a. The grammar files "+
        "are resolved using the global catalog file(s).";
    
    private static final String extendedFunctionTxt=
        "Validate document specified by $a using $b. "+
        "$b can point to an OASIS catalog file, a grammar (xml schema only) "+
        "or a collection (path ends with '/')";
    
    private final Validator validator;
    private final BrokerPool brokerPool;
    
    // Setup function signature
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                    new QName("validate", ValidationModule.NAMESPACE_URI, 
                                          ValidationModule.PREFIX),
                    simpleFunctionTxt,
                    new SequenceType[]{
                        new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
                    },
                    new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
                ),
        
//       new FunctionSignature(
//                new QName("validate", ValidationModule.NAMESPACE_URI, 
//                                          ValidationModule.PREFIX),
//                    "Validate document specified by $a. The grammar files "
//                    +"are searched inside the database.",
//                    new SequenceType[]{
//                        new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
//                    },
//                    new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
//                ),
                            
        new FunctionSignature(
                    new QName("validate", ValidationModule.NAMESPACE_URI, 
                                          ValidationModule.PREFIX),
                    extendedFunctionTxt,
                    new SequenceType[]{
                        new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
                        new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
                    },
                    new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
                ),
        
//        new FunctionSignature(
//                    new QName("validate", ValidationModule.NAMESPACE_URI, 
//                                          ValidationModule.PREFIX),
//                    "Validate document specified by $a using path $b. "
//                    +"$b can point a grammar, a collection containing "
//                    +"grammars (usefull for XSD) or a OASIS catalog file.",
//                    new SequenceType[]{
//                        new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
//                        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
//                    },
//                    new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
//                ),
                            
        new FunctionSignature(
                    new QName("validate-report", ValidationModule.NAMESPACE_URI, 
                                                 ValidationModule.PREFIX),
                    simpleFunctionTxt,
                    new SequenceType[]{
                        new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
                    },
                    new SequenceType(Type.STRING,  Cardinality.ZERO_OR_MORE)
        ),
                            
        new FunctionSignature(
                    new QName("validate-report", ValidationModule.NAMESPACE_URI, 
                                                 ValidationModule.PREFIX),
                    extendedFunctionTxt,
                    new SequenceType[]{
                        new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
                        new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
                    },
                    new SequenceType(Type.STRING,  Cardinality.ZERO_OR_MORE)
        )
    };
    

    public Validation(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
        validator = new Validator( brokerPool );
    }
    
    /** 
     * @see BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) 
                                                         throws XPathException {
        
        // Check input parameters
        if(args.length != 1 && args.length != 2){
            return Sequence.EMPTY_SEQUENCE;
        }
        
        // Get inputstream
        InputStream is;
        try {
            String url=args[0].getStringValue();
            if(url.startsWith("/")){
                url="xmldb:exist://"+url;
            }
            is = new URL(url).openStream();
            
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
            LOG.error(ex);
            throw new XPathException(getASTNode(),"Invalid resource URI",ex);
        } catch (IOException ex) {
            LOG.error(ex);
            ex.printStackTrace();
            throw new XPathException(getASTNode(),"IOexception",ex);
        } 

        ValidationReport vr = null;
        if(args.length==1){
            vr = validator.validate(is);
            
        } else {
            vr = validator.validate(is,args[1].getStringValue());
            
        }
        
        // Create response
        Sequence result = new ValueSequence();
        
        if(isCalledAs("validate")){
            result.add( new BooleanValue( vr.isValid() ) );
            
        } else if (isCalledAs("validate-report")) {
            String report[] = vr.getValidationReportArray();
            for(int i=0; i<report.length ; i++){
                result.add( new StringValue(report[i]) );
            }
        } else {
            // ohoh
            result = Sequence.EMPTY_SEQUENCE;
        }
        
        return result;
    }
}