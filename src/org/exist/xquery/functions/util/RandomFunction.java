/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
package org.exist.xquery.functions.util;

import java.util.Random;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class RandomFunction extends BasicFunction
{
	protected static final Logger logger = Logger.getLogger(RandomFunction.class);
	
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("random", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns a random number between 0.0 and 1.0",
            null,
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "a random number between 0.0 and 1.0")),
        
        new FunctionSignature(
                new QName("random", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Returns a random number between 0 and $max",
                new SequenceType[] {
    					new FunctionParameterSequenceType("max", Type.INTEGER, Cardinality.EXACTLY_ONE, "The maximum value for the random number.")
    			},
                new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "a random number between 0 and $max"))
    };
    
    public RandomFunction(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
    	Random rndGen = new Random();
    	
    	if(getArgumentCount() == 0)
    	{
    		DoubleValue result = new DoubleValue(rndGen.nextDouble());
			return result;
    	}
    	else
    	{
    		IntegerValue upper = (IntegerValue)args[0].convertTo(Type.INTEGER);
    		IntegerValue result = new IntegerValue(rndGen.nextInt(upper.getInt()));
			return result;
    		
    	}
    }
}



