/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for xmldb module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author Luigi P. Bai, finder@users.sf.net, 2004
 * @author ljo
 */
public class XMLDBModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/xmldb";

    public final static String PREFIX = "xmldb";
    public final static String INCLUSION_DATE = "2004-09-12";
    public final static String RELEASED_IN_VERSION = "pre eXist-1.0";

    public final static String NEED_PRIV_USER = "The XQuery owner must have appropriate privileges to do this, e.g. having DBA role.";
    public final static String NEED_PRIV_USER_NOT_CURRENT = "The XQuery owner must have appropriate privileges to do this, e.g. having DBA role, and not being the owner of the currently running XQuery.";
    public final static String REMEMBER_OCTAL_CALC = "PLEASE REMEMBER that octal number 0755 is 7*64+5*8+5 i.e. 493 in decimal NOT 755. You can use util:base-to-integer(0755, 8) as argument for convenience.";
    public final static String COLLECTION_URI = "Collection URIs can be specified either as a simple collection path or an XMLDB URI.";
    public final static String ANY_URI = "Resource URIs can be specified either as a simple collection path, an XMLDB URI or any URI.";

    public final static FunctionDef[] functions = {
            new FunctionDef(XMLDBCreateCollection.signature, XMLDBCreateCollection.class),
            new FunctionDef(XMLDBRegisterDatabase.signature, XMLDBRegisterDatabase.class),
            new FunctionDef(XMLDBStore.signatures[0], XMLDBStore.class),
            new FunctionDef(XMLDBStore.signatures[1], XMLDBStore.class),
            new FunctionDef(XMLDBStore.signatures[2], XMLDBStore.class),
            new FunctionDef(XMLDBLoadFromPattern.signatures[0], XMLDBLoadFromPattern.class),
            new FunctionDef(XMLDBLoadFromPattern.signatures[1], XMLDBLoadFromPattern.class),
            new FunctionDef(XMLDBLoadFromPattern.signatures[2], XMLDBLoadFromPattern.class),
            new FunctionDef(XMLDBLoadFromPattern.signatures[3], XMLDBLoadFromPattern.class),
            new FunctionDef(XMLDBXUpdate.signature, XMLDBXUpdate.class),
            new FunctionDef(XMLDBCopy.signatures[0], XMLDBCopy.class),
            new FunctionDef(XMLDBCopy.signatures[1], XMLDBCopy.class),
            new FunctionDef(XMLDBMove.signatures[0], XMLDBMove.class),
            new FunctionDef(XMLDBMove.signatures[1], XMLDBMove.class),
            new FunctionDef(XMLDBRename.signatures[0], XMLDBRename.class),
            new FunctionDef(XMLDBRename.signatures[1], XMLDBRename.class),
            new FunctionDef(XMLDBRemove.signatures[0], XMLDBRemove.class),
            new FunctionDef(XMLDBRemove.signatures[1], XMLDBRemove.class),
            new FunctionDef(XMLDBHasLock.signature[0], XMLDBHasLock.class),
            new FunctionDef(XMLDBHasLock.signature[1], XMLDBHasLock.class),
            new FunctionDef(XMLDBCreated.lastModifiedSignature, XMLDBCreated.class),
            new FunctionDef(XMLDBCreated.createdSignatures[0], XMLDBCreated.class),
            new FunctionDef(XMLDBCreated.createdSignatures[1], XMLDBCreated.class),
            new FunctionDef(XMLDBSize.signature, XMLDBSize.class),
            new FunctionDef(XMLDBGetChildCollections.signature, XMLDBGetChildCollections.class),
            new FunctionDef(XMLDBGetChildResources.signature, XMLDBGetChildResources.class),
            new FunctionDef(XMLDBCollectionAvailable.signatures[0], XMLDBCollectionAvailable.class),
            new FunctionDef(XMLDBURIFunctions.signatures[0], XMLDBURIFunctions.class),
            new FunctionDef(XMLDBURIFunctions.signatures[1], XMLDBURIFunctions.class),
            new FunctionDef(XMLDBURIFunctions.signatures[2], XMLDBURIFunctions.class),
            new FunctionDef(XMLDBURIFunctions.signatures[3], XMLDBURIFunctions.class),
            new FunctionDef(XMLDBGetMimeType.signature, XMLDBGetMimeType.class),
            new FunctionDef(XMLDBSetMimeType.signature, XMLDBSetMimeType.class),
            new FunctionDef(XMLDBDocument.signature, XMLDBDocument.class),
            new FunctionDef(FunXCollection.signature, FunXCollection.class),
            new FunctionDef(XMLDBReindex.FNS_REINDEX_COLLECTION, XMLDBReindex.class),
            new FunctionDef(XMLDBReindex.FNS_REINDEX_DOCUMENT, XMLDBReindex.class),
            new FunctionDef(XMLDBDefragment.signatures[0], XMLDBDefragment.class),
            new FunctionDef(XMLDBDefragment.signatures[1], XMLDBDefragment.class),
            new FunctionDef(FindLastModified.signatures[0], FindLastModified.class),
            new FunctionDef(FindLastModified.signatures[1], FindLastModified.class),
            new FunctionDef(XMLDBMatchCollection.signature, XMLDBMatchCollection.class),
            new FunctionDef(XMLDBTouch.FNS_TOUCH_DOCUMENT_NOW, XMLDBTouch.class),
            new FunctionDef(XMLDBTouch.FNS_TOUCH_DOCUMENT, XMLDBTouch.class),

        /* TODO these functions for login/logout etc need to be re-engineered and added to the SecurityManagerModule (deprecating these) */
            new FunctionDef(XMLDBAuthenticate.authenticateSignature, XMLDBAuthenticate.class),
            new FunctionDef(XMLDBAuthenticate.loginSignatures[0], XMLDBAuthenticate.class),
            new FunctionDef(XMLDBAuthenticate.loginSignatures[1], XMLDBAuthenticate.class),
            new FunctionDef(XMLDBGetCurrentUser.signature, XMLDBGetCurrentUser.class),

            /** security functions, deprecated by those in SecurityManagerModule **/
            new FunctionDef(XMLDBSetCollectionPermissions.signature, XMLDBSetCollectionPermissions.class),
            new FunctionDef(XMLDBSetResourcePermissions.signature, XMLDBSetResourcePermissions.class),
            new FunctionDef(XMLDBUserAccess.fnExistsUser, XMLDBUserAccess.class),
            new FunctionDef(XMLDBUserAccess.fnUserGroups, XMLDBUserAccess.class),
            new FunctionDef(XMLDBUserAccess.fnUserPrimaryGroup, XMLDBUserAccess.class),
            new FunctionDef(XMLDBUserAccess.fnUserHome, XMLDBUserAccess.class),
            new FunctionDef(XMLDBCreateUser.signatures[0], XMLDBCreateUser.class),
            new FunctionDef(XMLDBCreateUser.signatures[1], XMLDBCreateUser.class),
            new FunctionDef(XMLDBDeleteUser.signature, XMLDBDeleteUser.class),
            new FunctionDef(XMLDBChangeUser.signatures[0], XMLDBChangeUser.class),
            new FunctionDef(XMLDBChangeUser.signatures[1], XMLDBChangeUser.class),
            new FunctionDef(XMLDBCreateGroup.signatures[0], XMLDBCreateGroup.class),
            new FunctionDef(XMLDBCreateGroup.signatures[1], XMLDBCreateGroup.class),
            new FunctionDef(XMLDBAddUserToGroup.signature, XMLDBAddUserToGroup.class),
            new FunctionDef(XMLDBRemoveUserFromGroup.signature, XMLDBRemoveUserFromGroup.class),
            new FunctionDef(XMLDBGroupExists.signatures[0], XMLDBGroupExists.class),
            new FunctionDef(XMLDBChmodCollection.signature, XMLDBChmodCollection.class),
            new FunctionDef(XMLDBChmodResource.signature, XMLDBChmodResource.class),
            new FunctionDef(XMLDBPermissions.signatures[0], XMLDBPermissions.class),
            new FunctionDef(XMLDBPermissions.signatures[1], XMLDBPermissions.class),
            new FunctionDef(XMLDBPermissionsToString.signatures[0], XMLDBPermissionsToString.class),
            new FunctionDef(XMLDBPermissionsToString.signatures[1], XMLDBPermissionsToString.class),
            new FunctionDef(XMLDBIsAdmin.signature, XMLDBIsAdmin.class),
            new FunctionDef(XMLDBIsAuthenticated.signature, XMLDBIsAuthenticated.class),
            new FunctionDef(XMLDBGetUsers.signature, XMLDBGetUsers.class),
            new FunctionDef(XMLDBGetUserOrGroup.getGroupSignatures[0], XMLDBGetUserOrGroup.class),
            new FunctionDef(XMLDBGetUserOrGroup.getGroupSignatures[1], XMLDBGetUserOrGroup.class),
            new FunctionDef(XMLDBGetUserOrGroup.getOwnerSignatures[0], XMLDBGetUserOrGroup.class),
            new FunctionDef(XMLDBGetUserOrGroup.getOwnerSignatures[1], XMLDBGetUserOrGroup.class),
            new FunctionDef(XMLDBGetCurrentUserAttribute.signature, XMLDBGetCurrentUserAttribute.class),
            new FunctionDef(XMLDBGetCurrentUserAttributeNames.signature, XMLDBGetCurrentUserAttributeNames.class)
    };

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public XMLDBModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, true);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDescription()
     */
    @Override
    public String getDescription() {
        return "A module for database manipulation functions.";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getNamespaceURI()
     */
    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDefaultPrefix()
     */
    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
