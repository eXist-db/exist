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
import org.apache.xerces.xni.grammars.XMLGrammarDescription;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.GrammarPool;
import org.exist.validation.ValidationReport;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
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
            new QName("clear-grammar-cache", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            "Remove all cached grammers.",
            null,
            new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
            ),
        
        new FunctionSignature(
            new QName("show-grammar-cache", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            "Show all cached grammars.",
            null,
            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
            )
    };
    
    
    
    /** Creates a new instance */
    public GrammarTooling(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        BrokerPool brokerPool = context.getBroker().getBrokerPool();
        config = brokerPool.getConfiguration();
    }
    
    /** 
     * @see org.exist.xquery.BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
    throws XPathException {
        
        GrammarPool grammarpool
            = (GrammarPool) config.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);
        
        if (isCalledAs("clear-grammar-cache")){
            
            Sequence result = new ValueSequence();
            
            int before = countTotalNumberOfGrammar(grammarpool);
            LOG.debug("Clearing "+before+" grammars");
            
            clearGrammarPool(grammarpool);
            
            int after = countTotalNumberOfGrammar(grammarpool);
            LOG.debug("Remained "+after+" grammars");
            
            int delta=before-after;
            
            result.add(new IntegerValue(delta));
            
            return result;
            
            
        } else if (isCalledAs("show-grammar-cache")){
            MemTreeBuilder builder = context.getDocumentBuilder();
            NodeImpl result = writeReport(grammarpool, builder);
            return result;
            
            
            
        } else {
            // oh oh
            LOG.error("function not found error");
            throw new XPathException("function not found");
        }

    }
    
    private int countTotalNumberOfGrammar( GrammarPool grammarpool){
        
        return (grammarpool.retrieveInitialGrammarSet(TYPE_XSD).length
            + grammarpool.retrieveInitialGrammarSet(TYPE_DTD).length);
        
    }
    
    
    private void clearGrammarPool(GrammarPool grammarpool){
        grammarpool.clear();
    }
    
    private NodeImpl writeReport(GrammarPool grammarpool, MemTreeBuilder builder) {
        
        int nodeNr = builder.startElement("", "report", "report",null);
        
        Grammar xsds[] = grammarpool.retrieveInitialGrammarSet(TYPE_XSD);
        for(int i=0; i<xsds.length; i++){
            writeGrammar(xsds[i], builder);
        }
        
        Grammar dtds[] = grammarpool.retrieveInitialGrammarSet(TYPE_DTD);
        for(int i=0; i<dtds.length; i++){
            writeGrammar(dtds[i], builder);
        }

        builder.endElement();
        
        return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
    }
    
    private void writeGrammar(Grammar grammar,  MemTreeBuilder builder){
        
            XMLGrammarDescription xgd = grammar.getGrammarDescription();
            
            builder.startElement("", "grammar", "grammar", null);
            
            String grammarType=xgd.getGrammarType();
            if(grammarType!=null){
                builder.startElement("", "Type", "Type", null);
                builder.characters(grammarType);
                builder.endElement();
            }
            
            String namespace=xgd.getNamespace();
            if(namespace!=null){
                builder.startElement("", "Namespace", "Namespace", null);
                builder.characters(namespace);
                builder.endElement();
            }
            
            String publicId=xgd.getPublicId();
            if(publicId!=null){
                builder.startElement("", "PublicId", "PublicId", null);
                builder.characters(publicId);
                builder.endElement();
            }
            String baseSystemId=xgd.getBaseSystemId();
            if(baseSystemId!=null){
                builder.startElement("", "BaseSystemId", "BaseSystemId", null);
                builder.characters(baseSystemId);
                builder.endElement();
            }

            String literalSystemId=xgd.getLiteralSystemId();
            if(literalSystemId!=null){
                builder.startElement("", "LiteralSystemId", "LiteralSystemId", null);
                builder.characters(literalSystemId);
                builder.endElement();
            }
            
            String expandedSystemId=xgd.getExpandedSystemId();
            if(expandedSystemId!=null){
                builder.startElement("", "ExpandedSystemId", "ExpandedSystemId", null);
                builder.characters(expandedSystemId);
                builder.endElement();
            }

            builder.endElement();
    }
}