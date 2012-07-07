/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery.util;

/**
 * Dutch language formatting of numbers and dates.
 *
 * @author Dannes Wessels
 */
public class NumberFormatter_nl extends NumberFormatter {

    public final static String[] MONTHS = { "Januari", "Februari", "Maart", "April", "Mei", "Juni", "Juli",
            "Augustus", "September", "Oktober", "November", "December" };
    
    public final static String[] DAYS = { "Zondag", "Maandag", "Dinsdag", "Woensdag", "Donderdag",
            "Vrijdag", "Zaterdag" };

    @Override
    public String getMonth(int month) {
        return MONTHS[month - 1];
    }

    @Override
    public String getDay(int day) {
        return DAYS[day - 1];
    }

    @Override
    public String getAmPm(int hour) {
        return "";
    }

    @Override
    public String getOrdinalSuffix(long number) {
        return ".";
    }
}
