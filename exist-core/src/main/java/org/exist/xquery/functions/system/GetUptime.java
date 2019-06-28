/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 eXist-db team
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions.system;

import java.util.Calendar;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Return the duration since eXist was started
 * 
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class GetUptime extends BasicFunction {

    protected final static Logger logger = LogManager.getLogger(GetUptime.class);
    
    public final static FunctionSignature signature = new FunctionSignature(
        new QName("get-uptime", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "Returns the time since eXist-db was started. The value is stable over the lifetime of a query.",
        FunctionSignature.NO_ARGS,
        new FunctionReturnSequenceType(Type.DAY_TIME_DURATION, Cardinality.EXACTLY_ONE, "the duration since eXist-db was started")
    );

    public GetUptime(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final Calendar startupTime = context.getBroker().getBrokerPool().getStartupTime();

        final XMLGregorianCalendar xmlNow = context.getCalendar();
        final Calendar now = xmlNow.toGregorianCalendar();
        final long duration = now.getTimeInMillis() - startupTime.getTimeInMillis();
        return new DayTimeDurationValue(duration);

    }
}
