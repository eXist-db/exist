/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  $Id: ExampleModule.java 1173 2005-04-20 11:15:18Z wolfgang_m $
 */
package org.exist.xquery.modules.datetime;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class DateTimeModule extends AbstractInternalModule
{

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/datetime";
	
	public final static String PREFIX = "datetime";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(DateFromDateTimeFunction.signature, DateFromDateTimeFunction.class),
		new FunctionDef(TimeFromDateTimeFunction.signature, TimeFromDateTimeFunction.class),
		new FunctionDef(DayInWeekFunction.signature, DayInWeekFunction.class),
		new FunctionDef(WeekInMonthFunction.signature, WeekInMonthFunction.class),
		new FunctionDef(CountDayInMonthFunction.signature, CountDayInMonthFunction.class),
		new FunctionDef(DaysInMonthFunction.signature, DaysInMonthFunction.class),
		new FunctionDef(FormatDateTimeFunction.signature, FormatDateTimeFunction.class),
		new FunctionDef(FormatDateFunction.signature, FormatDateFunction.class),
		new FunctionDef(FormatTimeFunction.signature, FormatTimeFunction.class),
		new FunctionDef(DateForFunction.signature, DateForFunction.class)
	};
	
	public DateTimeModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for performing date and time operations";
	}
}
