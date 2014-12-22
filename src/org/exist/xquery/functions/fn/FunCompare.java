/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2009 The eXist Project
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

import java.text.Collator;

import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author perig
 * @author ljo
 *
 */
public class FunCompare extends CollatingFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature (
            new QName("compare", Function.BUILTIN_FUNCTION_NS),
            "Returns the collatable comparison between $string-1 and $string-2, using $collation-uri. " +
            "-1 if $string-1 is inferior to $string-2, 0 if $string-1 is equal " +
            "to $string-2, 1 if $string-1 is superior to $string-2. " + 
            "If either comparand is the empty sequence, the empty sequence is " +
            "returned. " +
            "Please remember to specify the collation in the context or use, " +
            "the three argument version if you don't want the system default.",
            new SequenceType[] {
                new FunctionParameterSequenceType("string-1", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The first string"),
                new FunctionParameterSequenceType("string-2", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The second string")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE,
                "-1 if $string-1 is inferior to $string-2, " +
                "0 if $string-1 is equal to $string-2, " +
                "1 if $string-1 is superior to $string-2. " +
                "If either comparand is the empty sequence, the empty sequence is returned.")),
        new FunctionSignature (
            new QName("compare", Function.BUILTIN_FUNCTION_NS),
            "Returns the collatable comparison between $string-1 and $string-2, using $collation-uri. " +
            "-1 if $string-1 is inferior to $string-2, 0 if $string-1 is equal " +
            "to $string-2, 1 if $string-1 is superior to $string-2. " +
            "If either comparand is the empty sequence, the empty sequence is returned. " +
            THIRD_REL_COLLATION_ARG_EXAMPLE,
            new SequenceType[] {
                new FunctionParameterSequenceType("string-1", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The first string"),
                new FunctionParameterSequenceType("string-2", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The second string"),
                new FunctionParameterSequenceType("collation-uri", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The relative collation URI")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE,
                "-1 if $string-1 is inferior to $string-2, " +
                "0 if $string-1 is equal to $string-2, " +
                "1 if $string-1 is superior to $string-2. " +
                "If either comparand is the empty sequence, the empty sequence is returned."))
    };

    public FunCompare(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        final Sequence seq1 = getArgument(0).eval(contextSequence, contextItem);
        final Sequence seq2 =	getArgument(1).eval(contextSequence, contextItem);
        Sequence result;
        if (seq1.isEmpty() || seq2.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            final Collator collator = getCollator(contextSequence, contextItem, 3);		
            final int comparison = Collations.compare(collator, seq1.getStringValue(), seq2.getStringValue());
            if (comparison == Constants.EQUAL) 
                {result = new IntegerValue(Constants.EQUAL);}
            else if (comparison < 0)
                {result = new IntegerValue(Constants.INFERIOR);}
            else 
                {result = new IntegerValue(Constants.SUPERIOR);}
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        return result;
	}
}
