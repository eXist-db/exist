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
package org.exist.xquery.functions.util;

import java.util.Random;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class RandomFunction extends BasicFunction
{
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("random", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns a random number between 0.0 and 1.0",
            null,
            new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)),
        
        new FunctionSignature(
                new QName("random", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Returns a random number between 0 and $a",
                new SequenceType[] {
    					new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
    			},
                new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE))
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
    		return new DoubleValue(rndGen.nextDouble());
    	}
    	else
    	{
    		IntegerValue upper = (IntegerValue)args[0].convertTo(Type.INTEGER);
    		return new IntegerValue(rndGen.nextInt(upper.getInt()));
    		
    	}
    }
}



