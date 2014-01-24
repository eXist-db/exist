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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.exist.jetty.JettyStart;
import org.junit.BeforeClass;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RestApiSecurityTest extends AbstractApiSecurityTest {

    private static JettyStart server = null;
    
    private final static String HOST = "localhost";
    private final static int PORT = 8088;
    private final static String REST_URI = "http://" + HOST + ":" + PORT + "/rest";
    private final static String baseUri = "/db";
    
    @Override
    protected void createCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        executeQuery("xmldb:create-collection('/db', '" + collectionName + "')", uid, pwd);
    }

    @Override
    protected void removeCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        final String collectionUri = REST_URI + baseUri + "/" + collectionName;
        
        final Executor exec = getExecutor(uid, pwd);
        try {
            final HttpResponse resp = exec.execute(Request.Delete(collectionUri)).returnResponse();
            
            if(resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new ApiException("Could not remove collection: " + collectionUri + ". " + getResponseBody(resp.getEntity()));
            }
        } catch(final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    @Override
    protected void chownCol(final String collectionUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chown(xs:anyURI('" + collectionUri + "'), '" + owner_uid + "')", uid, pwd);
        executeQuery("sm:chgrp(xs:anyURI('" + collectionUri + "'), '" + group_gid + "')", uid, pwd);
    }

    @Override
    protected void chmodCol(final String collectionUri, final String mode, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chmod(xs:anyURI('" + collectionUri + "'), '" + mode + "')", uid, pwd);
    }

    @Override
    protected void chmodRes(final String resourceUri, final String mode, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chmod(xs:anyURI('" + resourceUri + "'), '" + mode + "')", uid, pwd);
    }

    @Override
    protected void chownRes(final String resourceUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chown(xs:anyURI('" + resourceUri + "'), '" + owner_uid + "')", uid, pwd);
        executeQuery("sm:chgrp(xs:anyURI('" + resourceUri + "'), '" + group_gid + "')", uid, pwd);
    }

    @Override
    protected String getXmlResourceContent(final String resourceUri, final String uid, final String pwd) throws ApiException {
        final Executor exec = getExecutor(uid, pwd);
        try {
            final HttpResponse resp = exec.execute(Request.Get(REST_URI + resourceUri)).returnResponse();
            
            if(resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new ApiException("Could not get XML resource from uri: " + resourceUri + ". " + getResponseBody(resp.getEntity()));
            } else {
                return getResponseBody(resp.getEntity());
            }
        } catch(final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    @Override
    protected void removeAccount(final String account_uid, final String uid, final String pwd) throws ApiException {
        executeQuery("xmldb:delete-user('" + account_uid + "')", uid, pwd);
    }

    @Override
    protected void removeGroup(final String group_uid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:delete-group('" + group_uid + "')", uid, pwd);
    }

    @Override
    protected void createAccount(final String account_uid, final String account_pwd, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("xmldb:create-user('" + account_uid + "', '" + account_pwd + "', ('" + group_gid + "'))", uid, pwd);
    }

    @Override
    protected void createGroup(final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("xmldb:create-group('" + group_gid + "', '" + uid + "')", uid, pwd);
    }

    @Override
    protected void createXmlResource(final String resourceUri, final String content, final String uid, final String pwd) throws ApiException {
        final Executor exec = getExecutor(uid, pwd);
        try {
            final HttpResponse resp = exec.execute(
                    Request.Put(REST_URI + resourceUri)
                    .addHeader("Content-Type", "application/xml")
                    .bodyByteArray(content.getBytes())
            ).returnResponse();
            
            if(resp.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                throw new ApiException("Could not store XML resource to uri: " + resourceUri + ". " + getResponseBody(resp.getEntity()));
            }
        } catch(final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    @Override
    protected void createBinResource(final String resourceUri, final byte[] content, final String uid, final String pwd) throws ApiException {
         final Executor exec = getExecutor(uid, pwd);
        try {
            final HttpResponse resp = exec.execute(
                    Request.Put(REST_URI + resourceUri)
                    .addHeader("Content-Type", "application/octet-stream")
                    .bodyByteArray(content)
            ).returnResponse();
            
            if(resp.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                throw new ApiException("Could not store Binary resource to uri: " + resourceUri + ". " + getResponseBody(resp.getEntity()));
            }
        } catch(final IOException ioe) {
            throw new ApiException(ioe);
        }
    }
    
    @BeforeClass
    public static void startServer() throws InterruptedException {
        //Don't worry about closing the server : the shutdownDB hook will do the job
        if (server == null) {
            server = new JettyStart();
            System.out.println("Starting standalone server...");
            server.run();
            while (!server.isStarted()) {
                Thread.sleep(1000);
            }
        }
    }

    private void executeQuery(final String xquery, final String uid, final String pwd) throws ApiException {
        final Executor exec = getExecutor(uid, pwd);
        try {
            final String queryUri = createQueryUri(xquery);
            
            final HttpResponse resp = exec.execute(Request.Get(queryUri)).returnResponse();
            
            if(resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new ApiException("Could not execute query uri: " + queryUri + ". " + getResponseBody(resp.getEntity()));
            }
        } catch(final IOException ioe) {
            throw new ApiException(ioe);
        }
    }
    
    private Executor getExecutor(final String uid, String pwd) {
        return Executor.newInstance().authPreemptive(new HttpHost(HOST, PORT)).auth(uid, pwd);
    }
    
    private String createQueryUri(final String xquery) throws UnsupportedEncodingException {
        return REST_URI + baseUri + "/?_query=" + URLEncoder.encode(xquery, "UTF-8");
    }
    
    private String getResponseBody(final HttpEntity entity) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        entity.writeTo(baos);
        return new String(baos.toByteArray());
    }
}
