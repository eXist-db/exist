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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.modules.datetime;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.1
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
        new FunctionDef(DateForFunction.signature, DateForFunction.class),
        new FunctionDef(DateRangeFunctions.signature[0], DateRangeFunctions.class),
        new FunctionDef(DateRangeFunctions.signature[1], DateRangeFunctions.class),
        new FunctionDef(DateRangeFunctions.signature[2], DateRangeFunctions.class)
    };


    public DateTimeModule()
    {
        super(functions);
    }

    @Override
    public String getNamespaceURI()
    {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix()
    {
        return PREFIX;
    }

    @Override
    public String getDescription()
    {
        return "A module for performing date and time operations";
    }
}