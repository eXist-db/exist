/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Team
 *
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
 *  $Id: FunCodepointsToString.java 3457 2006-05-07 14:46:21Z wolfgang_m $
 */
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;

public class FunDateTime extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("dateTime", Function.BUILTIN_FUNCTION_NS, ModuleImpl.PREFIX),
				"Creates an xs:dateTime from an xs:date and an xs:time.",
				new SequenceType[] {
					new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE),
					new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE)
				},
				new SequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE)
			);
	
	public FunDateTime(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
        Sequence result;
		if (args[0].isEmpty() || args[1].isEmpty())
			result = Sequence.EMPTY_SEQUENCE;
		else if (args[0].hasMany())
			throw new XPathException("XPTY0004: expected at most one xs:date");
		else if (args[1].hasMany())
			throw new XPathException("XPTY0004: expected at most one xs:time");
        else {  
        	DateValue dv = (DateValue)args[0].itemAt(0);
        	TimeValue tv = (TimeValue)args[1].itemAt(0);
        	if (!dv.getTimezone().isEmpty()) {
        		if (!tv.getTimezone().isEmpty()) {
    				if (!((DayTimeDurationValue)dv.getTimezone().itemAt(0)).compareTo(null, Constants.EQ, ((DayTimeDurationValue)tv.getTimezone().itemAt(0)))) {
    					throw new XPathException("FORG0008: operands have different timezones");
    				}
    			} else {
    				if (!((DayTimeDurationValue)dv.getTimezone().itemAt(0)).getStringValue().equals("PT0S"))
    	        		throw new XPathException("FORG0008: operands have different timezones");
    			}
    		} else {
    			if (!tv.getTimezone().isEmpty()) {
    				if (!((DayTimeDurationValue)tv.getTimezone().itemAt(0)).getStringValue().equals("PT0S"))
    					throw new XPathException("FORG0008: operands have different timezones");
    			}
    		}
        	String dtv = dv.convertTo(Type.DATE_TIME).getStringValue();
        	if (dv.getTimezone().isEmpty()) {
        		dtv = dtv.substring(0, dtv.length() - 8);
        		result = new DateTimeValue(dtv + tv.getStringValue());
        	} else if (((DayTimeDurationValue)dv.getTimezone().itemAt(0)).getStringValue().equals("PT0S")) {
        		dtv = dtv.substring(0, dtv.length() - 9);
        		if (tv.getTimezone().isEmpty())
        			result = new DateTimeValue(dtv + tv.getStringValue() + "Z");
        		else
        			result = new DateTimeValue(dtv + tv.getStringValue());
        	} else {
        		dtv = dtv.substring(0, dtv.length() - 14);    
        		result = new DateTimeValue(dtv + tv.getStringValue());
        	}
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;
	}
}
