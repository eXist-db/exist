/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2006 The eXist team
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc.,  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */

package org.exist.xquery.functions.xmldb;

import java.util.Arrays;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 *  Some modifications Copyright (C) 2004 Luigi P. Bai
 *  finder@users.sf.net
 */
public class XMLDBModule extends AbstractInternalModule {
    
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/xmldb";
    
    public final static String PREFIX = "xmldb";
    
    public final static FunctionDef[] functions = {
        new FunctionDef(XMLDBCollection.signature, XMLDBCollection.class),
                new FunctionDef(XMLDBCreateCollection.signature, XMLDBCreateCollection.class),
                new FunctionDef(XMLDBRegisterDatabase.signature, XMLDBRegisterDatabase.class),
                new FunctionDef(XMLDBStore.signatures[0], XMLDBStore.class),
                new FunctionDef(XMLDBStore.signatures[1], XMLDBStore.class),
                new FunctionDef(XMLDBLoadFromPattern.signatures[0], XMLDBLoadFromPattern.class),
                new FunctionDef(XMLDBLoadFromPattern.signatures[1], XMLDBLoadFromPattern.class),
                new FunctionDef(XMLDBLoadFromPattern.signatures[2], XMLDBLoadFromPattern.class),
                new FunctionDef(XMLDBAuthenticate.authenticateSignature, XMLDBAuthenticate.class),
                new FunctionDef(XMLDBAuthenticate.loginSignature, XMLDBAuthenticate.class),
                new FunctionDef(XMLDBGetCurrentUser.signature, XMLDBGetCurrentUser.class),
                new FunctionDef(XMLDBXUpdate.signature, XMLDBXUpdate.class),
                new FunctionDef(XMLDBCopy.signatures[0], XMLDBCopy.class),
                new FunctionDef(XMLDBCopy.signatures[1], XMLDBCopy.class),
                new FunctionDef(XMLDBMove.signatures[0], XMLDBMove.class),
                new FunctionDef(XMLDBMove.signatures[1], XMLDBMove.class),
                new FunctionDef(XMLDBRename.signatures[0], XMLDBRename.class),
                new FunctionDef(XMLDBRename.signatures[1], XMLDBRename.class),
                new FunctionDef(XMLDBRemove.signatures[0], XMLDBRemove.class),
                new FunctionDef(XMLDBRemove.signatures[1], XMLDBRemove.class),
                new FunctionDef(XMLDBHasLock.signature, XMLDBHasLock.class),
                new FunctionDef(XMLDBCreated.lastModifiedSignature, XMLDBCreated.class),
                new FunctionDef(XMLDBCreated.createdSignatures[0], XMLDBCreated.class),
                new FunctionDef(XMLDBCreated.createdSignatures[1], XMLDBCreated.class),
                new FunctionDef(XMLDBPermissions.signatures[0], XMLDBPermissions.class),
                new FunctionDef(XMLDBPermissions.signatures[1], XMLDBPermissions.class),
                new FunctionDef(XMLDBSize.signature, XMLDBSize.class),
                new FunctionDef(XMLDBGetUserOrGroup.getGroupSignatures[0], XMLDBGetUserOrGroup.class),
                new FunctionDef(XMLDBGetUserOrGroup.getGroupSignatures[1], XMLDBGetUserOrGroup.class),
                new FunctionDef(XMLDBGetUserOrGroup.getOwnerSignatures[0], XMLDBGetUserOrGroup.class),
                new FunctionDef(XMLDBGetUserOrGroup.getOwnerSignatures[1], XMLDBGetUserOrGroup.class),
                new FunctionDef(XMLDBGetChildCollections.signature, XMLDBGetChildCollections.class),
                new FunctionDef(XMLDBGetChildResources.signature, XMLDBGetChildResources.class),
                new FunctionDef(XMLDBSetCollectionPermissions.signature, XMLDBSetCollectionPermissions.class),
                new FunctionDef(XMLDBSetResourcePermissions.signature, XMLDBSetResourcePermissions.class),
                new FunctionDef(XMLDBUserAccess.fnExistsUser, XMLDBUserAccess.class),
                new FunctionDef(XMLDBUserAccess.fnUserGroups, XMLDBUserAccess.class),
                new FunctionDef(XMLDBUserAccess.fnUserHome, XMLDBUserAccess.class),
                new FunctionDef(XMLDBCreateUser.signature, XMLDBCreateUser.class),
                new FunctionDef(XMLDBChangeUser.signature, XMLDBChangeUser.class),
                new FunctionDef(XMLDBDeleteUser.signature, XMLDBDeleteUser.class),
                new FunctionDef(XMLDBChmodCollection.signature, XMLDBChmodCollection.class),
                new FunctionDef(XMLDBChmodResource.signature, XMLDBChmodResource.class),
                new FunctionDef(XMLDBCollectionExists.signature, XMLDBCollectionExists.class),
                new FunctionDef(XMLDBPermissionsToString.signature, XMLDBPermissionsToString.class),
                new FunctionDef(XMLDBIsAdmin.signature, XMLDBIsAdmin.class),
                new FunctionDef(XMLDBURIFunctions.signatures[0], XMLDBURIFunctions.class),
                new FunctionDef(XMLDBURIFunctions.signatures[1], XMLDBURIFunctions.class),
                new FunctionDef(XMLDBURIFunctions.signatures[2], XMLDBURIFunctions.class),
                new FunctionDef(XMLDBURIFunctions.signatures[3], XMLDBURIFunctions.class),
                new FunctionDef(XMLDBGetMimeType.signature, XMLDBGetMimeType.class)
    };

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public XMLDBModule() {
        super(functions, true);
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Module#getDescription()
         */
    public String getDescription() {
        return "Database manipulation functions";
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Module#getNamespaceURI()
         */
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Module#getDefaultPrefix()
         */
    public String getDefaultPrefix() {
        return PREFIX;
    }
    
}
