/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2013 The eXist Project
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
package org.exist.security;

import java.util.LinkedList;
import org.exist.jetty.JettyStart;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.UserManagementService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
@RunWith (Parameterized.class)
public class XmldbApiSecurityTest extends AbstractApiSecurityTest {

    private String baseUri;

    private static JettyStart server;

    public XmldbApiSecurityTest(final String baseUri) {
        this.baseUri = baseUri;
    }

    @Parameterized.Parameters
    public static LinkedList<String[]> instances() {
        LinkedList<String[]> params = new LinkedList<String[]>();
        params.add(new String[] { "xmldb:exist://" });
        params.add(new String[] { "xmldb:exist://localhost:" + System.getProperty("jetty.port", "8088") + "/xmlrpc" });
        
        return params;
    }
    
    
    
    @Override
    protected void createCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + "/db", uid, pwd);
            CollectionManagementService cms = (CollectionManagementService)col.getService("CollectionManagementService", "1.0");
            cms.createCollection(collectionName);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @Override
    protected void removeCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + "/db", uid, pwd);
            final Collection child = col.getChildCollection(collectionName);
            if(child != null) {
                child.close();
                final CollectionManagementService cms = (CollectionManagementService)col.getService("CollectionManagementService", "1.0");
                cms.removeCollection(collectionName);
            }
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
      
    @Override
    protected void chownCol(final String collectionUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + collectionUri, uid, pwd);
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");
            
            ums.chown(ums.getAccount(owner_uid), group_gid);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @Override
    protected void chmodCol(final String collectionUri, final String mode, final String uid, final String pwd) throws ApiException {
        
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + collectionUri, uid, pwd);
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");

            ums.chmod(mode);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @Override
    protected void chownRes(final String resourceUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + getCollectionUri(resourceUri), uid, pwd);
            
            final Resource resource = col.getResource(getResourceName(resourceUri));
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");
            
            ums.chown(resource, ums.getAccount(owner_uid), group_gid);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @Override
    protected void chmodRes(final String resourceUri, final String mode, final String uid, final String pwd) throws ApiException {
        
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + getCollectionUri(resourceUri), uid, pwd);
            
            final Resource resource = col.getResource(getResourceName(resourceUri));
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");
            ums.chmod(resource, mode);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }

    @Override
    protected String getXmlResourceContent(final String resourceUri, final String uid, final String pwd) throws ApiException {
        
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + getCollectionUri(resourceUri), uid, pwd);
            final Resource resource = col.getResource(getResourceName(resourceUri));
            return (String)resource.getContent();
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }

    @Override
    protected void removeAccount(final String account_uid, final String uid, final String pwd) throws ApiException {
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + "/db", uid, pwd);
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");

            final Account acct = ums.getAccount(account_uid);
            if(acct != null){
                ums.removeAccount(acct);
            }
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @Override
    protected void removeGroup(String group_uid, String uid, String pwd) throws ApiException {
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + "/db", uid, pwd);
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");

            final Group grp = ums.getGroup(group_uid);
            if(grp != null){
                ums.removeGroup(grp);
            }
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }

    @Override
    protected void createAccount(String account_uid, String account_pwd, String group_uid, String uid, String pwd) throws ApiException {
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + "/db", uid, pwd);
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");

            final Group group = ums.getGroup(group_uid);

            final Account user = new UserAider(account_uid, group);
            user.setPassword(account_pwd);
            ums.addAccount(user);
            
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @Override
    protected void createGroup(String group_uid, String uid, String pwd) throws ApiException {
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + "/db", uid, pwd);
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");

            Group group = new GroupAider("exist", group_uid);
            ums.addGroup(group);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }

    @Override
    protected void createXmlResource(String resourceUri, String content, String uid, String pwd) throws ApiException {
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + getCollectionUri(resourceUri), uid, pwd);
            Resource resource = col.createResource(getResourceName(resourceUri), XMLResource.RESOURCE_TYPE);
            resource.setContent(content);
            col.storeResource(resource);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @Override
    protected void createBinResource(String resourceUri, byte[] content, String uid, String pwd) throws ApiException {
        Collection col = null;
        try {
            col = DatabaseManager.getCollection(baseUri + getCollectionUri(resourceUri), uid, pwd);
            Resource resource = col.createResource(getResourceName(resourceUri), BinaryResource.RESOURCE_TYPE);
            resource.setContent(content);
            col.storeResource(resource);
        } catch(final XMLDBException xmldbe) {
            throw new ApiException(xmldbe);
        } finally {
            if(col != null) {
                try {
                    col.close();
                } catch (final XMLDBException xmldbe) {
                    throw new ApiException(xmldbe);
                }
            }
        }
    }
    
    @BeforeClass
    public static void startServer() {
//            Class<?> cl = Class.forName(DB_DRIVER);
//            Database database = (Database) cl.newInstance();
//            database.setProperty("create-database", "true");
//            DatabaseManager.registerDatabase(database);
//            Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
//            assertNotNull(root);
            
            System.out.println("Starting standalone server...");
            server = new JettyStart();
            server.run();
    }

    @AfterClass
    public static void stopServer() {
//        try {
//         Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
//            DatabaseInstanceManager mgr =
//                (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
//            mgr.shutdownDB();
//        } catch (XMLDBException e) {
//            e.printStackTrace();
//        }
        System.out.println("Shutdown standalone server...");
        server.shutdown();
        server = null;
    }
}
