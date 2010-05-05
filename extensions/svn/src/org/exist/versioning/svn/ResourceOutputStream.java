/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.versioning.svn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Date;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.xml.sax.InputSource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ResourceOutputStream extends OutputStream {

	private Resource resource;
	private File temp;
	private OutputStream os;

	public ResourceOutputStream(Resource resource, File temp) throws IOException {
		super();
		
		this.resource = resource;
		this.temp = temp;
		os = new FileOutputStream(temp);
	}

	@Override
	public void write(int b) throws IOException {
		os.write(b);
	}

	@Override
	public void write(byte b[], int off, int len) throws IOException {
		os.write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		os.flush();
	}

	@Override
	public void close() throws IOException {
		os.close();

    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			
			temp.delete();

			throw new IOException(e);
		}
		
		Collection currentDirectory = resource.getCollection();

		XmldbURI fileName = resource.uri.lastSegment();

		MimeType mimeType = MimeTable.getInstance().getContentTypeFor(fileName);

		if (mimeType == null) {
			mimeType = MimeType.BINARY_TYPE;
		}
		
		tm = db.getTransactionManager();
		Txn transaction = tm.beginTransaction();

		InputStream is = null;
		try {
			if (mimeType.isXMLType()) {
				// store as xml resource
				is = new FileInputStream(temp);
				IndexInfo info = currentDirectory.validateXMLResource(
						transaction, broker, fileName, new InputSource(new InputStreamReader(is)));
				is.close();
				info.getDocument().getMetadata().setMimeType(mimeType.getName());
				is = new FileInputStream(temp);
				currentDirectory.store(transaction, broker, info, new InputSource(new InputStreamReader(is)), false);
				is.close();

			} else {
				// store as binary resource
				is = new FileInputStream(temp);

				currentDirectory.addBinaryResource(transaction, broker, fileName, is,
						mimeType.getName(), (int) temp.length(), new Date(), new Date());

			}
			tm.commit(transaction);
		} catch (Exception e) {
			tm.abort(transaction);
			throw new IOException(e);
		} finally {
			SVNFileUtil.closeFile(is);

			temp.delete();

			db.release(broker);
		}
	}

}
