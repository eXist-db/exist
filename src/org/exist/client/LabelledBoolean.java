/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
package org.exist.client;

/**
 * Simple Label and Boolean value
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class LabelledBoolean {  
    private final String label;
    private final boolean set;

    public LabelledBoolean(final String label, final boolean set) {
        this.label = label;
        this.set = set;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSet() {
        return set;
    }
    
    public LabelledBoolean copy(final boolean set) {
        return new LabelledBoolean(getLabel(), set);
    }
}
