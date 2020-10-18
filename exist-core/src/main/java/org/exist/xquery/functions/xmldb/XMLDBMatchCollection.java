/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.xmldb;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.StringValue;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class XMLDBMatchCollection extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(XMLDBMatchCollection.class);

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("match-collection", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Looks for collection names in the collection index that match the provided regexp",
            new SequenceType[]{
                new FunctionParameterSequenceType("regexp", Type.STRING, Cardinality.EXACTLY_ONE, "The expression to use for matching collection names"),
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The names of the collections that match the expression"));

    
    public XMLDBMatchCollection(XQueryContext context) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        Sequence result = Sequence.EMPTY_SEQUENCE;
        
        final String regexp = args[0].getStringValue();
        
        final List<String> collectionNames = context.getBroker().findCollectionsMatching(regexp);
        if(!collectionNames.isEmpty()) {
            result = copyListToValueSequence(collectionNames);
        }
        
        return result;
    }

    private Sequence copyListToValueSequence(List<String> collectionNames) {
        final ValueSequence seq = new ValueSequence(collectionNames.size());
        
        for(final String collectionName : collectionNames) {
            seq.add(new StringValue(collectionName));
        }
        
        return seq;
    }
}