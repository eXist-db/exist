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

import org.exist.dom.QName;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

/**
 * @author Dannes Wessels
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
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

        String label = args[1].getStringValue();
        if(label==null){
            label="";
        }

        Sequence result ;
        
        if(args[0].isEmpty()){
            result = Sequence.EMPTY_SEQUENCE;
            
        } else {
            // Copy all Items from input to output sequence
            result = new ValueSequence();
            
            int position = 0;

            // Force adaptive serialization
            final Properties props = new Properties();
            props.setProperty(OutputKeys.METHOD, "adaptive");

            for (final SequenceIterator i = args[0].iterate(); i.hasNext();) {

                // Get item
                final Item next = i.nextItem();

                // Only write if logger is set to debug mode
                if (LOG.isDebugEnabled()) {

                    position++;

                    try (StringWriter writer = new StringWriter()) {
                        XQuerySerializer xqs = new XQuerySerializer(context.getBroker(), props, writer);
                        xqs.serialize(next.toSequence());

                        // Write to log
                        LOG.debug("{} [{}] [{}]: {}", label, position, Type.getTypeName(next.getType()), writer.toString());

                    } catch (IOException | SAXException e) {
                        throw new XPathException(this, e.getMessage());
                    }

                }

                // Add to result
                result.add(next);
            }
        }

        return result;
    }
}
