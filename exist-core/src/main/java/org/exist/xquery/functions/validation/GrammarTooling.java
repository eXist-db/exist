/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 * $Id$
 */

package org.exist.xquery.functions.validation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.xerces.parsers.XMLGrammarPreparser;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.parser.XMLInputSource;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.GrammarPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import org.xml.sax.helpers.AttributesImpl;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * TODO: please use named constants
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class GrammarTooling extends BasicFunction  {
    
    private static final String TYPE_DTD=Namespaces.DTD_NS;
    private static final String TYPE_XSD=Namespaces.SCHEMA_NS;
    
    private final Configuration config;


    public final static String cacheReport="<report>\n"+
            "\t<grammar type=\"...\"\n" +
            "\t\t<Namespace>....\n" +
            "\t\t<BaseSystemId>...\n" +
            "\t\t<LiteralSystemId>...\n" +
            "\t\t<ExpandedSystemId>....\n" +
            "\t</grammar>\n" +
            "\t...\n" +
            "\t...\n" +
            "</report>\n";
    
    // Setup function signature
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("clear-grammar-cache", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            "Remove all cached grammers.",
            null,
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE,
            "the number of deleted grammars.")
        ),
        
        new FunctionSignature(
            new QName("show-grammar-cache", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            "Show all cached grammars.",
            null,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                "an XML document containing details on all cached grammars.")
        ),
            
        new FunctionSignature(
            new QName("pre-parse-grammar", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            "Pre parse grammars and add to grammar cache. Only XML schemas (.xsd)" +
            " are supported.",
            new SequenceType[]{
                new FunctionParameterSequenceType("grammar", Type.ANY_URI, Cardinality.ZERO_OR_MORE,
                        "Reference to grammar.")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
             "sequence of namespaces of preparsed grammars.")
        ),
            
            
    };


    /**
     * Creates a new instance.
     *
     * @param context Xquery context.
     * @param signature Function signature.
     */
    public GrammarTooling(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        final BrokerPool brokerPool = context.getBroker().getBrokerPool();
        config = brokerPool.getConfiguration();
    }
    
    /** 
     * @see org.exist.xquery.BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
    throws XPathException {
        
        final GrammarPool grammarpool
            = (GrammarPool) config.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);
        
        if (isCalledAs("clear-grammar-cache")){
            
            final Sequence result = new ValueSequence();
            
            final int before = countTotalNumberOfGrammar(grammarpool);
            LOG.debug("Clearing "+before+" grammars");
            
            clearGrammarPool(grammarpool);
            
            final int after = countTotalNumberOfGrammar(grammarpool);
            LOG.debug("Remained "+after+" grammars");
            
            final int delta=before-after;
            
            result.add(new IntegerValue(delta));
            
            return result;
            
            
        } else if (isCalledAs("show-grammar-cache")){
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final NodeImpl result = writeReport(grammarpool, builder);
            return result;
            
        } else if (isCalledAs("pre-parse-grammar")){
            
            if (args[0].isEmpty())
                {return Sequence.EMPTY_SEQUENCE;}
            
            // Setup for XML schema support only
            final XMLGrammarPreparser parser = new XMLGrammarPreparser();
            parser.registerPreparser(TYPE_XSD , null);
            
           
            final List<Grammar> allGrammars = new ArrayList<>();
            
             // iterate through the argument sequence and parse url
            for (final SequenceIterator i = args[0].iterate(); i.hasNext();) {
                String url = i.nextItem().getStringValue();
                
                // Fix database urls
                if(url.startsWith("/")){
                    url="xmldb:exist://"+url;
                }
               
                LOG.debug("Parsing "+url);
                
                // parse XSD grammar
                try {
                    if(url.endsWith(".xsd")){
                        
                        final InputStream is = new URL(url).openStream();
                        final XMLInputSource xis = new XMLInputSource(null, url, url, is, null);
                        final Grammar schema = parser.preparseGrammar(TYPE_XSD, xis);
                        is.close();

                        allGrammars.add(schema);

                    } else {
                        throw new XPathException(this, "Only XMLSchemas can be preparsed.");
                    }

                } catch(final Exception ex) {
                    LOG.debug(ex);
                    throw new XPathException(this, ex);
                }
                
                
            }

            LOG.debug("Successfully parsed "+allGrammars.size()+" grammars.");
            
            // Send all XSD grammars to grammarpool
            Grammar grammars[] = new Grammar[allGrammars.size()];
            grammars = allGrammars.toArray(grammars);
            grammarpool.cacheGrammars(TYPE_XSD, grammars);
 
            // Construct result to end user
            final ValueSequence result = new ValueSequence();
            for(final Grammar one : grammars){
                result.add( new StringValue(one.getGrammarDescription().getNamespace()) );
            }
            

            return result;
            
        } else {
            // oh oh
            LOG.error("function not found error");
            throw new XPathException(this, "function not found");
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
        
        final int nodeNr = builder.startElement("", "report", "report",null);
        
        final Grammar xsds[] = grammarpool.retrieveInitialGrammarSet(TYPE_XSD);
        for (Grammar xsd : xsds) {
            writeGrammar(xsd, builder);
        }
        
        final Grammar dtds[] = grammarpool.retrieveInitialGrammarSet(TYPE_DTD);
        for (Grammar dtd : dtds) {
            writeGrammar(dtd, builder);
        }

        builder.endElement();
        
        return builder.getDocument().getNode(nodeNr);
    }
    
    private void writeGrammar(Grammar grammar,  MemTreeBuilder builder){
        
            final XMLGrammarDescription xgd = grammar.getGrammarDescription();
            
            final AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "type", "type", "CDATA", xgd.getGrammarType());
            
            builder.startElement("", "grammar", "grammar", attribs);
      
            final String namespace=xgd.getNamespace();
            if(namespace!=null){
                builder.startElement("", "Namespace", "Namespace", null);
                builder.characters(namespace);
                builder.endElement();
            }
            
            final String publicId=xgd.getPublicId();
            if(publicId!=null){
                builder.startElement("", "PublicId", "PublicId", null);
                builder.characters(publicId);
                builder.endElement();
            }
            final String baseSystemId=xgd.getBaseSystemId();
            if(baseSystemId!=null){
                builder.startElement("", "BaseSystemId", "BaseSystemId", null);
                builder.characters(baseSystemId);
                builder.endElement();
            }

            final String literalSystemId=xgd.getLiteralSystemId();
            if(literalSystemId!=null){
                builder.startElement("", "LiteralSystemId", "LiteralSystemId", null);
                builder.characters(literalSystemId);
                builder.endElement();
            }
            
            final String expandedSystemId=xgd.getExpandedSystemId();
            if(expandedSystemId!=null){
                builder.startElement("", "ExpandedSystemId", "ExpandedSystemId", null);
                builder.characters(expandedSystemId);
                builder.endElement();
            }

            builder.endElement();
            attribs.clear();
    }
}