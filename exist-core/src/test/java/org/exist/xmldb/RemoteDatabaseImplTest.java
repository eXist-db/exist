/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb;

import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.util.SyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.fail;

/** A test case for accessing user management service remotely ? 
 * @author <a href="mailto:pierrick.brihaye@free.fr">Sebastian Bossung, Technische Universitaet Hamburg-Harburg
 * @author Pierrick Brihaye</a>
 */
public class RemoteDatabaseImplTest extends RemoteDBTest {

    protected final static String ADMIN_COLLECTION_NAME = "admin-collection";

    @Before
	public void setUp() throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        setUpRemoteDatabase();
	}    

    @Test
    public void testGetCollection() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException, SyntaxException, PermissionDeniedException {
        Class<?> cl = Class.forName(DB_DRIVER);
        Database database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);

        Collection rootCollection = DatabaseManager.getCollection(getUri() + XmldbURI.ROOT_COLLECTION, "admin", "");

        CollectionManagementService cms = rootCollection.getService(CollectionManagementService.class);
        Collection adminCollection = cms.createCollection(ADMIN_COLLECTION_NAME);
        UserManagementService ums = rootCollection.getService(UserManagementService.class);
        if (ums != null) {
            Permission p = ums.getPermissions(adminCollection);
            p.setMode(Permission.USER_STRING + "=+read,+write," + Permission.GROUP_STRING + "=-read,-write," + Permission.OTHER_STRING + "=-read,-write");
            ums.setPermissions(adminCollection, p);

            Collection guestCollection = DatabaseManager.getCollection(getUri() + XmldbURI.ROOT_COLLECTION + "/" + ADMIN_COLLECTION_NAME, "guest", "guest");

            Resource resource = guestCollection.createResource("testguest", BinaryResource.class);
            resource.setContent("123".getBytes());
            try {
                guestCollection.storeResource(resource);
                fail();
            } catch (XMLDBException e) {

            }

            cms.removeCollection(ADMIN_COLLECTION_NAME);
        }
    }
}
