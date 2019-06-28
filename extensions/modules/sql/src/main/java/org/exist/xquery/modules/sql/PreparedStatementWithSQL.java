/*
 *  Simple structure for SQLModule PreparedStatements
 *  Copyright (C) 2008-10 Adam Retter <adam@exist-db.org>
 *  www.adamretter.co.uk
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
package org.exist.xquery.modules.sql;

import java.sql.PreparedStatement;


/**
 * DOCUMENT ME!
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class PreparedStatementWithSQL
{
    private String            sql  = null;
    private PreparedStatement stmt = null;

    public PreparedStatementWithSQL( String sql, PreparedStatement stmt )
    {
        this.sql  = sql;
        this.stmt = stmt;
    }

    public String getSql()
    {
        return( sql );
    }


    public PreparedStatement getStmt()
    {
        return( stmt );
    }
}
