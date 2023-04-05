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
package org.exist.source;

import java.io.*;

import org.exist.EXistException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.internal.aider.UnixStylePermissionAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Source implementation that reads from a binary resource
 * stored in the database.
 * 
 * @author wolf
 */
public class DBSource extends AbstractSource {
    
    private final BinaryDocument doc;
    private final long lastModified;
    private String encoding = UTF_8.name();
    private final boolean checkEncoding;
    private final BrokerPool brokerPool;
    
    public DBSource(final BrokerPool brokerPool, final BinaryDocument doc, final boolean checkXQEncoding) {
        super(hashKey(doc.getURI().toString()));
        this.brokerPool = brokerPool;
        this.doc = doc;
        this.lastModified = doc.getLastModified();
        this.checkEncoding = checkXQEncoding;
    }

    @Override
    public String path() {
        return getDocumentPath().toString();
    }

    @Override
    public String type() {
        return "DB";
    }

    public XmldbURI getDocumentPath() {
    	return doc.getURI();
    }

    public long getLastModified() {
        return lastModified;
    }

    @Override
    public Validity isValid() {
        Validity result;
        try (final DBBroker broker = brokerPool.getBroker();
             final LockedDocument lockedDoc = broker.getXMLResource(doc.getURI(), LockMode.READ_LOCK)) {
            if (lockedDoc == null) {
                result = Validity.INVALID;
            } else if(lockedDoc.getDocument().getLastModified() > lastModified) {
                result = Validity.INVALID;
            } else {
                result = Validity.VALID;
            }
        } catch (final EXistException | PermissionDeniedException pde) {
            result = Validity.INVALID;
        }

        return result;
    }

    @Override
    public Reader getReader() throws IOException {
        try (final DBBroker broker = brokerPool.getBroker()) {
            final InputStream is = broker.getBinaryResource(doc);
            final BufferedInputStream bis = new BufferedInputStream(is);
            bis.mark(64);
            checkEncoding(bis);
            bis.reset();
            return new InputStreamReader(bis, encoding);
        } catch (final EXistException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try (final DBBroker broker = brokerPool.getBroker()) {
            return broker.getBinaryResource(doc);
        } catch (final EXistException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public String getContent() throws IOException {
        final long binaryLength = doc.getContentLength();
        if (binaryLength > Integer.MAX_VALUE) {
            throw new IOException("Resource too big to be read using this method.");
        }

        try (final DBBroker broker = brokerPool.getBroker();
                final InputStream raw = broker.getBinaryResource(doc);
                final UnsynchronizedByteArrayOutputStream buf = new UnsynchronizedByteArrayOutputStream((int)binaryLength)) {
            buf.write(raw);
            try (final InputStream is = buf.toInputStream()) {
                checkEncoding(is);
                return buf.toString(encoding);
            }
        } catch (final EXistException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public QName isModule() throws IOException {
        try (final DBBroker broker = brokerPool.getBroker();
             final InputStream is = broker.getBinaryResource(doc)) {
            return getModuleDecl(is);
        } catch (final EXistException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private void checkEncoding(final InputStream is) {
        if (checkEncoding) {
            final String checkedEnc = guessXQueryEncoding(is);
            if(checkedEnc != null) {
                encoding = checkedEnc;
            }
        }
    }
    
    @Override
    public String toString() {
    	return doc.getDocumentURI();
    }

    /**
     * Check: has current subject requested permissions for this resource?
     *
     * @param mode The requested mode
     * @throws PermissionDeniedException if user has not sufficient rights
     *
     * @deprecated These security checks should be done by the caller
     */
    @Deprecated
    public void validate(final int mode) throws PermissionDeniedException {
        //TODO(AR) This check should not even be here! Its up to the database to refuse access not requesting source
        try (final DBBroker broker = brokerPool.getBroker()) {
            final Subject subject = broker.getCurrentSubject();
            if (subject != null) {
                doValidation(subject, mode);
            }
        } catch (final EXistException e) {
            throw new PermissionDeniedException(e.getMessage(), e);
        }
    }

    /**
     * Check: has subject requested permissions for this resource?
     *
     * @param subject The subject
     * @param mode The requested mode
     * @throws PermissionDeniedException if user has not sufficient rights
     *
     * @deprecated These security checks should be done by the caller
     */
    @Override
    @Deprecated
    public void validate(final Subject subject, final int mode) throws PermissionDeniedException {
        //TODO(AR) This check should not even be here! Its up to the database to refuse access not requesting source
        if (subject == null) {
            final String modeStr = new UnixStylePermissionAider(mode).toString();
            throw new PermissionDeniedException("Subject not given for checking  '" + modeStr + "' access to resource '" + doc.getURI() + "'.");
        } else {
            doValidation(subject, mode);
        }
    }

    private void doValidation(final Subject subject, final int mode) throws PermissionDeniedException {
        if (!doc.getPermissions().validate(subject, mode)) {
            final String modeStr = new UnixStylePermissionAider(mode).toString();
            throw new PermissionDeniedException("Subject '" + subject.getName() + "' does not have '" + modeStr + "' access to resource '" + doc.getURI() + "'.");
        }
    }

    public Permission getPermissions() {
        return doc.getPermissions();
    }

    @Override
    public int hashCode() {
        return getDocumentPath().hashCode();
    }
}
