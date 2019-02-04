/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
package org.exist.backup.xquery;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;


/**
 * Module function definitions for backup module.
 *
 * @author  wolf
 * @author  ljo
 */
public class BackupModule extends AbstractInternalModule
{
    public static final String        NAMESPACE_URI       = "http://exist-db.org/xquery/backups";

    public static final String        PREFIX              = "backups";
    public final static String        INCLUSION_DATE      = "2009-02-02";
    public final static String        RELEASED_IN_VERSION = "eXist-1.2.6";

    public static final FunctionDef[] functions           = {
        new FunctionDef( ListBackups.signature, ListBackups.class ),
        new FunctionDef( RetrieveBackup.signature, RetrieveBackup.class )
    };

    public BackupModule(final Map<String, List<?>> parameters)
    {
        super( functions, parameters, true );
    }

    public String getNamespaceURI()
    {
        return( NAMESPACE_URI );
    }


    public String getDefaultPrefix()
    {
        return( PREFIX );
    }


    public String getDescription()
    {
        return( "A module for access to database backups available on the server file system" );
    }


    public String getReleaseVersion()
    {
        return( RELEASED_IN_VERSION );
    }

}
