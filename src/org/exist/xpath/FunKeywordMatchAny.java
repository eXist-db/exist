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
 */
package org.exist.xpath;

import org.exist.storage.BrokerPool;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    7. Oktober 2002
 */
public class FunKeywordMatchAny extends FunKeywordMatchAll {

    /**  Constructor for the FunKeywordMatchAny object */
    public FunKeywordMatchAny(BrokerPool pool) {
        super( pool, "match-any" );
    }


    /**
     *  Gets the operatorType attribute of the FunKeywordMatchAny object
     *
     *@return    The operatorType value
     */
    protected int getOperatorType() {
        return Constants.FULLTEXT_OR;
    }

}

