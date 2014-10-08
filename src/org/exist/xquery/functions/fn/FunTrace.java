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
package org.exist.xquery.functions.fn;

import java.io.StringWriter;
import java.io.Writer;
import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.SAXException;

/**
 * @author Dannes Wessels
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunTrace extends BasicFunction {
    
    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("trace", Function.BUILTIN_FUNCTION_NS),
            "This function is intended to be used in debugging queries by "
            +"providing a trace of their execution. The input $value is "
            +"returned, unchanged, as the result of the function. "
            +"In addition, the inputs $value, converted to an xs:string, "
            +"and $label is directed to a trace data set in the eXist log files.",
            new SequenceType[] {
                new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value"),
                new FunctionParameterSequenceType("label", Type.STRING, Cardinality.EXACTLY_ONE, "The label in the log file")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the labelled $value in the log")
        );
    
    public FunTrace(XQueryContext context) {
        super(context, signature);
    }
    
/*
 * (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(Sequence[], Sequence)
 */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        // TODO Add TRACE log statements using log4j
        // TODO Figure out why XQTS still does not work
        // TODO Remove unneeded comments
        
        String label = args[1].getStringValue();
        if(label==null){
            label="";
        }
        
        final Serializer serializer= context.getBroker().getSerializer();
        
        Sequence result ;
        
        if(args[0].isEmpty()){
            result = Sequence.EMPTY_SEQUENCE;
            
        } else {
            // Copy all Items from input to output sequence
            result = new ValueSequence();
            
            int position = 0;

            for (final SequenceIterator i = args[0].iterate(); i.hasNext();) {

                // Get item
                final Item next = i.nextItem();

                // Only write if debug mode
                if (true) {
                    
                    String value = null;
                    position++;

                    final int type = next.getType();
                    
                    // Serialize an element type
                    if (Type.ELEMENT == type) {
                        final Writer sw = new StringWriter();
                        try {
                            serializer.serialize((NodeValue) next, sw);

                        } catch (final SAXException ex) {
                            LOG.error(ex.getMessage());
                        }
                        value = sw.toString();

                    // Get string value for other types
                    } else {
                        value = next.getStringValue();

                    }
                    
                    // Write to log
                    LOG.info(label + " [" + position + "]: " + Type.getTypeName(type) + ": " + value);
                }

                // Add to result
                result.add(next);
            }
        }

        return result;
    }
}
