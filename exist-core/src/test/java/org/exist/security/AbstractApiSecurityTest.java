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

import org.junit.After;

import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.TestUtils.ADMIN_DB_USER;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class AbstractApiSecurityTest {
    
    protected final static String TEST_COLLECTION1_NAME = "securityTest1";
    
    protected final static String TEST_COLLECTION1 = "/db/" + TEST_COLLECTION1_NAME;
    
    protected final static String TEST_XML_DOC1_NAME = "test.xml";
    protected final static String TEST_XML_DOC1 = TEST_COLLECTION1 + "/" + TEST_XML_DOC1_NAME;
    protected final static String TEST_XML_DOC1_CONTENT = "<test/>";
    
    protected final static String TEST_BIN_DOC1_NAME = "test.bin";
    protected final static String TEST_BIN_DOC1 = TEST_COLLECTION1 + "/" + TEST_BIN_DOC1_NAME;
    protected final static byte[] TEST_BIN_DOC1_CONTENT = "binary-test".getBytes();
    
    protected final static String TEST_USER1_UID = "test1";
    protected final static String TEST_USER1_PWD = TEST_USER1_UID;
    
    protected final static String TEST_USER2_UID = "test2";
    protected final static String TEST_USER2_PWD = TEST_USER2_UID;
    
    protected final static String TEST_GROUP_GID = "group1";
    protected final static String TEST_GROUP_PWD = TEST_GROUP_GID;
    
    @Test
    public void canReadXmlResourceWithOnlyExecutePermissionOnParentCollection() throws ApiException {
        
        chmodCol(TEST_COLLECTION1, "--x------", TEST_USER1_UID, TEST_USER1_PWD);
        
        final String content = getXmlResourceContent(TEST_XML_DOC1, TEST_USER1_UID, TEST_USER1_PWD);
        assertEquals(TEST_XML_DOC1_CONTENT, content);
    }
    
    @Test
    public void cannotReadXmlResourceWithoutExecutePermissionOnParentCollection() throws ApiException {

        chmodCol(TEST_COLLECTION1, "rw-------", TEST_USER1_UID, TEST_USER1_PWD);
        
        try {
            final String content = getXmlResourceContent(TEST_XML_DOC1, TEST_USER1_UID, TEST_USER1_PWD);
            fail("Excpected READ collection denied!");
        } catch(final ApiException ae) {
            //do nothing <-- expected exception
        }
    }
    
    protected abstract void createCol(String collectionName, String uid, String pwd) throws ApiException;
    protected abstract void removeCol(String collectionName, String uid, String pwd) throws ApiException;
    
    protected abstract void chownCol(String collectionUri, String owner_uid, String group_gid, String uid, String pwd) throws ApiException;
    protected abstract void chmodCol(String collectionUri, String mode, String uid, String pwd) throws ApiException;
    protected abstract void chmodRes(String resourceUri, String mode, String uid, String pwd) throws ApiException;
    protected abstract void chownRes(String resourceUri, String owner_uid, String group_gid, String uid, String pwd) throws ApiException;
    
    protected abstract String getXmlResourceContent(String resourceUri, String uid, String pwd) throws ApiException;
    
    protected abstract void removeAccount(String account_uid, String uid, String pwd) throws ApiException;
    protected abstract void removeGroup(String group_gid, String uid, String pwd) throws ApiException;
    protected abstract void createAccount(String account_uid, String account_pwd, String group_uid, String uid, String pwd) throws ApiException;
    protected abstract void createGroup(String group_gid, String uid, String pwd) throws ApiException;
    protected abstract void createXmlResource(String resourceUri, String content, String uid, String pwd) throws ApiException;
    protected abstract void createBinResource(String resourceUri, byte[] content, String uid, String pwd) throws ApiException;
    
    
    @Before
    public void setup() throws ApiException {
        
        chmodCol("/db", "rwxr-xr-x", ADMIN_DB_USER, ADMIN_DB_PWD); //ensure /db is always 755
        
        removeAccount(TEST_USER1_UID, ADMIN_DB_USER, ADMIN_DB_PWD);
        removeGroup(TEST_USER1_UID, ADMIN_DB_USER, ADMIN_DB_PWD);  // remove personal group!
        removeAccount(TEST_USER2_UID, ADMIN_DB_USER, ADMIN_DB_PWD);
        removeGroup(TEST_USER2_UID, ADMIN_DB_USER, ADMIN_DB_PWD);  // remove personal group!

        removeGroup(TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);
        
        createGroup(TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);
        createAccount(TEST_USER1_UID, TEST_USER1_PWD, TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);
        createAccount(TEST_USER2_UID, TEST_USER2_PWD, TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);

        // create a collection /db/securityTest as user "test1"
        createCol(TEST_COLLECTION1_NAME, ADMIN_DB_USER, ADMIN_DB_PWD);
        // pass ownership to test1
        chownCol(TEST_COLLECTION1, TEST_USER1_UID, TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);
        chmodCol(TEST_COLLECTION1, "rwxrwx---", ADMIN_DB_USER, ADMIN_DB_PWD);
        
        createXmlResource(TEST_XML_DOC1, TEST_XML_DOC1_CONTENT, ADMIN_DB_USER, ADMIN_DB_PWD);
        chmodRes(TEST_XML_DOC1, "rwxrwx---", ADMIN_DB_USER, ADMIN_DB_PWD);
        chownRes(TEST_XML_DOC1, TEST_USER1_UID, TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);
        
        createBinResource(TEST_BIN_DOC1, TEST_BIN_DOC1_CONTENT, ADMIN_DB_USER, ADMIN_DB_PWD);
        chmodRes(TEST_BIN_DOC1, "rwxrwx---", ADMIN_DB_USER, ADMIN_DB_PWD);
        chownRes(TEST_BIN_DOC1, TEST_USER1_UID, TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);
    }

    @After
    public void cleanup() throws ApiException {
        removeCol(TEST_COLLECTION1_NAME, ADMIN_DB_USER, ADMIN_DB_PWD);

        removeAccount(TEST_USER1_UID, ADMIN_DB_USER, ADMIN_DB_PWD);
        removeAccount(TEST_USER2_UID, ADMIN_DB_USER, ADMIN_DB_PWD);
        removeGroup(TEST_GROUP_GID, ADMIN_DB_USER, ADMIN_DB_PWD);
    }
    
    protected String getCollectionUri(String resourceUri) {
        return resourceUri.substring(0, resourceUri.lastIndexOf("/"));
    }
    
    protected String getResourceName(String resourceUri) {
        return resourceUri.substring(resourceUri.lastIndexOf("/") + 1);
    }
}
