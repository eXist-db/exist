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

import java.util.Locale;

/**
 * Swedish language formatting of numbers and dates.
 *
 * @author ljo
 */
public class NumberFormatter_sv extends NumberFormatter {

    public NumberFormatter_sv(Locale locale) {
        super(locale);
    }

    @Override
    public String getOrdinalSuffix(long number) {
	// Swedish date ordinals do not usually use suffices,
	// so this method is a bit coarse for both numbers *and* dates.
	// For dates it should preferrably be a switch with:
	//  return "";
        if (number > 10 && number < 20)
            {return ":e";}
        final long mod = number % 10;
        if (mod == 1 || mod == 2)
            {return ":a";}
        else
            {return ":e";}
    }
}
