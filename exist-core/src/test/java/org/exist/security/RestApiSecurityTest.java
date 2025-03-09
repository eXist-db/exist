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
package org.exist.security;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.exist.test.ExistWebServer;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.ClassRule;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RestApiSecurityTest extends AbstractApiSecurityTest {

    @ClassRule
    public static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private final static String baseUri = "/db";

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort() + "/rest";
    }

    @Override
    protected void createCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        executeQuery("xmldb:create-collection('/db', '" + collectionName + "')", uid, pwd);
    }

    @Override
    protected void removeCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        final String collectionUri = getServerUri() + baseUri + "/" + collectionName;
        
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
    protected void addCollectionUserAce(final String collectionUri, final String user_uid, final String mode, final boolean allow, final String uid, final String pwd) throws ApiException {
        final String query = "sm:add-user-ace(xs:anyURI('" + collectionUri + "'), '" + user_uid + "', " + (allow ? "true()" : "false()") + ", '" + mode + "')";
        executeQuery(query, uid, pwd);
    }

    @Override
    protected String getXmlResourceContent(final String resourceUri, final String uid, final String pwd) throws ApiException {
        final Executor exec = getExecutor(uid, pwd);
        try {
            final HttpResponse resp = exec.execute(Request.Get(getServerUri() + resourceUri)).returnResponse();
            
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
        executeQuery("if(sm:user-exists('" + account_uid + "'))then sm:remove-account('" + account_uid + "') else()", uid, pwd);
    }

    @Override
    protected void removeGroup(final String group_uid, final String uid, final String pwd) throws ApiException {
        executeQuery("if(sm:group-exists('" + group_uid + "'))then sm:remove-group('" + group_uid + "') else()", uid, pwd);
    }

    @Override
    protected void createAccount(final String account_uid, final String account_pwd, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:create-account('" + account_uid + "', '" + account_pwd + "', ('" + group_gid + "'))", uid, pwd);
    }

    @Override
    protected void createGroup(final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:create-group('" + group_gid + "', '" + uid + "', '" + group_gid + "')", uid, pwd);
    }

    @Override
    protected void createXmlResource(final String resourceUri, final String content, final String uid, final String pwd) throws ApiException {
        final Executor exec = getExecutor(uid, pwd);
        try {
            final HttpResponse resp = exec.execute(
                    Request.Put(getServerUri() + resourceUri)
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
                    Request.Put(getServerUri() + resourceUri)
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

    private void executeQuery(final String xquery, final String uid, final String pwd) throws ApiException {
        final Executor exec = getExecutor(uid, pwd);
        try {
            final String queryUri = createQueryUri(xquery);
            
            final HttpResponse resp = exec.execute(Request.Get(queryUri)).returnResponse();

            final int status = resp.getStatusLine().getStatusCode();
            if(status != HttpStatus.SC_OK) {
                throw new ApiException("HTTP " + status + " could not execute query uri: " + queryUri + ". " + getResponseBody(resp.getEntity()));
            }
        } catch(final IOException ioe) {
            throw new ApiException(ioe);
        }
    }
    
    private Executor getExecutor(final String uid, String pwd) {
        return Executor.newInstance().authPreemptive(new HttpHost("localhost", existWebServer.getPort())).auth(uid, pwd);
    }
    
    private String createQueryUri(final String xquery) throws UnsupportedEncodingException {
        return getServerUri() + baseUri + "/?_query=" + URLEncoder.encode(xquery, StandardCharsets.UTF_8);
    }
    
    private String getResponseBody(final HttpEntity entity) throws IOException {
        try(final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream(256)) {
            entity.writeTo(baos);
            return new String(baos.toByteArray());
        }
    }
}
