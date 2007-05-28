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

import org.apache.xerces.xni.grammars.Grammar;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.GrammarPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * TODO: please use named constants
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class GrammarTooling extends BasicFunction  {
    
    private static final String TYPE_DTD="http://www.w3.org/TR/REC-xml";
    private static final String TYPE_XSD=Namespaces.SCHEMA_NS;
    
    private final Configuration config;
    
    // Setup function signature
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName("grammar-cache-clear", ValidationModule.NAMESPACE_URI, 
                                                 ValidationModule.PREFIX),
                "Remove all cached grammers.",
                null,
                new SequenceType(Type.BOOLEAN, Cardinality.EMPTY)
        ),
                
        new FunctionSignature(
                new QName("grammar-cache-show", ValidationModule.NAMESPACE_URI, 
                                                ValidationModule.PREFIX),
                "Show all cached grammars.",
                null,
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)
        )
    };
    
    
    
    /** Creates a new instance */
    public GrammarTooling(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        BrokerPool brokerPool = context.getBroker().getBrokerPool();
        config = brokerPool.getConfiguration();
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) 
                                                        throws XPathException {
       
       GrammarPool grammarpool 
           = (GrammarPool) config.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);
       
       // Create response
        Sequence result = new ValueSequence();
           
        if (isCalledAs("grammar-cache-clear")){

            int before = grammarpool.retrieveInitialGrammarSet(TYPE_XSD).length
                + grammarpool.retrieveInitialGrammarSet(TYPE_DTD).length;
            LOG.debug("Clearing "+before+" grammars");
            
            grammarpool.clear();
            
            int after = grammarpool.retrieveInitialGrammarSet(TYPE_XSD).length
                + grammarpool.retrieveInitialGrammarSet(TYPE_DTD).length;
            LOG.debug("Remained "+after+" grammars");

            result = Sequence.EMPTY_SEQUENCE;
            
        } else if (isCalledAs("grammar-cache-show")){
            
            // TODO ; refactor grammartype url
            Grammar xsds[] = grammarpool.retrieveInitialGrammarSet(TYPE_XSD);          
            for(int i=0; i<xsds.length; i++){
                result.add(new StringValue(xsds[i].getGrammarDescription()
                                                            .getNamespace()) );
            }
            
            // TODO ; refactor grammartype url
            Grammar dtds[] = grammarpool.retrieveInitialGrammarSet(TYPE_DTD);
            for(int i=0; i<dtds.length; i++){
                result.add(new StringValue(dtds[i].getGrammarDescription()
                                                             .getPublicId()) );
            }
            
        } else {
            // oh oh
            result = Sequence.EMPTY_SEQUENCE;
        }
        
        return result;
    }
}