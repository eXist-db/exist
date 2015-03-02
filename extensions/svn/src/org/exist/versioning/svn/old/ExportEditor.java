/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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
package org.exist.versioning.svn.old;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class ExportEditor implements ISVNEditor {
	private XmldbURI rootPath;

	private Collection myRootDirectory;

	BrokerPool pool = null;
	DBBroker broker = null;
	TransactionManager transact = null;
	Txn transaction;

	private SVNProperties fileProperties;
	private Map<String, String> dirProperties;

	private File currentTmpFile;

	private SVNDeltaProcessor deltaProcessor;

	private String currentPath;
	private Collection currentDirectory;
	private File currentFile;

	private ISVNOptions options;
	private String eolStyle = SVNProperty.EOL_STYLE_NATIVE;

	public ExportEditor(XmldbURI path) throws EXistException {
		rootPath = path;

		deltaProcessor = new SVNDeltaProcessor();

		options = new DefaultSVNOptions();
		dirProperties = new SVNHashMap();
	}

	public void targetRevision(long revision) throws SVNException {
		System.out.println("targetRevision");
	}

	public void openRoot(long revision) throws SVNException {
		System.out.println("openRoot");

		try {
			pool = BrokerPool.getInstance();
			//BUG: need to be released!!! where???
			broker = pool.get(pool.getSecurityManager().getSystemSubject());
			transact = pool.getTransactionManager();

			myRootDirectory = broker.getCollection(rootPath);

			currentDirectory = myRootDirectory;

			transaction = transact.beginTransaction();

		} catch (Exception e) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
					"error: failed to initialize database.");
			throw new SVNException(err);
		}
		
		currentPath = "";
	}

	public void addDir(String path, String copyFromPath, long copyFromRevision)
			throws SVNException {
		System.out.println("addDir");

		currentPath = path;

		Collection child;

		try {
			child = broker.getOrCreateCollection(transaction, myRootDirectory
					.getURI().append(path));
			broker.saveCollection(transaction, child);

			currentDirectory = child;
		} catch (PermissionDeniedException e) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
					"error: failed on permission.");
			throw new SVNException(err);

		} catch (IOException e) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
					"error: failed on IO.");
			throw new SVNException(err);
		} catch (TriggerException e) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
					"error: failed on IO.");
			throw new SVNException(err);
		}
	}

	public void openDir(String path, long revision) throws SVNException {
		System.out.println("openDir");

		currentPath = path;

		try {
			currentDirectory = broker.getCollection(myRootDirectory.getURI().append(path));
		} catch (PermissionDeniedException e) {
			throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "error: failed on IO."), e);
		}
	}

	public void changeDirProperty(String name, SVNPropertyValue property)
			throws SVNException {
		// UNDERSTAND: should check path?
		if (SVNProperty.EXTERNALS.equals(name) && property != null) {
			dirProperties.put(currentPath, property.getString());
		}
	}

	public void addFile(String path, String copyFromPath, long copyFromRevision)
			throws SVNException {
		System.out.println("addFile path = " + path);

		path = SVNEncodingUtil.uriEncode(path);

		// TODO: check parent for that resource.

		// create child resource.
		currentFile = SVNFileUtil.createTempFile("", ".tmp"); // prefix???

		// TODO: "COPY"

		fileProperties = new SVNProperties();
		checksum = null;
	}

	public void openFile(String path, long revision) throws SVNException {
		System.out.println("openFile");
	}

	public void changeFileProperty(String path, String name,
			SVNPropertyValue property) throws SVNException {
		// UNDERSTAND: should check path?
		fileProperties.put(name, property);
	}

	/* *************************************
	 * ************ text part **************
	 * *************************************
	 */
	private String checksum;

	public void applyTextDelta(String path, String baseChecksum)
			throws SVNException {
		String name = SVNPathUtil.tail(path);
		currentTmpFile = SVNFileUtil.createTempFile(name, ".tmp");
		deltaProcessor.applyTextDelta((File) null, currentTmpFile, true);
	}

	public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow)
			throws SVNException {
		return deltaProcessor.textDeltaChunk(diffWindow);
	}

	public void textDeltaEnd(String path) throws SVNException {
		checksum = deltaProcessor.textDeltaEnd();
	}

	public void closeFile(String path, String textChecksum) throws SVNException {
		System.out.println(" closeFile path = " + path);

		if (textChecksum == null) {
			textChecksum = fileProperties.getStringValue(SVNProperty.CHECKSUM);
		}

		String realChecksum = checksum != null ? checksum : SVNFileUtil
				.computeChecksum(currentTmpFile);
		checksum = null;

		if (textChecksum != null && !textChecksum.equals(realChecksum)) {
			SVNErrorMessage err = SVNErrorMessage
					.create(
							SVNErrorCode.CHECKSUM_MISMATCH,
							"Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
							new Object[] { currentFile, textChecksum,
									realChecksum });
			SVNErrorManager.error(err, SVNLogType.WC);
		}

		try {
			String date = fileProperties
					.getStringValue(SVNProperty.COMMITTED_DATE);
			boolean special = fileProperties
					.getStringValue(SVNProperty.SPECIAL) != null;
			boolean binary = SVNProperty.isBinaryMimeType(fileProperties
					.getStringValue(SVNProperty.MIME_TYPE));
			String keywords = fileProperties
					.getStringValue(SVNProperty.KEYWORDS);
			Map keywordsMap = null;
			// if (keywords != null) {
			// String url = SVNPathUtil.append(myURL,
			// SVNEncodingUtil.uriEncode(currentPath));
			// url = SVNPathUtil.append(url,
			// SVNEncodingUtil.uriEncode(currentFile.getName()));
			// String author =
			// fileProperties.getStringValue(SVNProperty.LAST_AUTHOR);
			// String revStr =
			// fileProperties.getStringValue(SVNProperty.COMMITTED_REVISION);
			// keywordsMap = SVNTranslator.computeKeywords(keywords, url,
			// author, date, revStr, options);
			// }
			String charset = SVNTranslator.getCharset(fileProperties
					.getStringValue(SVNProperty.CHARSET),
								  fileProperties
					.getStringValue(SVNProperty.MIME_TYPE),
					currentFile.getPath(), options);
			byte[] eolBytes = null;
			if (SVNProperty.EOL_STYLE_NATIVE.equals(fileProperties
					.getStringValue(SVNProperty.EOL_STYLE))) {
				eolBytes = SVNTranslator.getEOL(eolStyle != null ? eolStyle
						: fileProperties.getStringValue(SVNProperty.EOL_STYLE),
						options);
			} else if (fileProperties.containsName(SVNProperty.EOL_STYLE)) {
				eolBytes = SVNTranslator.getEOL(fileProperties
						.getStringValue(SVNProperty.EOL_STYLE), options);
			}

			if (binary) {
				// no translation unless 'special'.
				charset = null;
				eolBytes = null;
				keywordsMap = null;
			}

			if (charset != null || eolBytes != null
					|| (keywordsMap != null && !keywordsMap.isEmpty())
					|| special) {
				SVNTranslator.translate(currentTmpFile, currentFile, charset,
						eolBytes, keywordsMap, special, true);
			} else {
				SVNFileUtil.rename(currentTmpFile, currentFile);
			}

			boolean executable = fileProperties
					.getStringValue(SVNProperty.EXECUTABLE) != null;

			if (executable) {
				SVNFileUtil.setExecutable(currentFile, true);
			}

			if (!special && date != null) {
				currentFile.setLastModified(SVNDate.parseDate(date).getTime());
			}

			XmldbURI fileName = XmldbURI.create(path).lastSegment();

			MimeType mimeType = MimeTable.getInstance().getContentTypeFor(
					fileName);
			// unknown mime type, here preferred is to do nothing
			if (mimeType == null) {
				// TODO: report error? path +
				// " - unknown suffix. No matching mime-type found in : " +
				// MimeTable.getInstance().getSrc());

				// if some one prefers to store it as binary by default, but
				// dangerous
				mimeType = MimeType.BINARY_TYPE;
			}

			InputStream is = null;
			try {
				if (mimeType.isXMLType()) {
					// store as xml resource
					is = new FileInputStream(currentFile);
					IndexInfo info = currentDirectory.validateXMLResource(
							transaction, broker, fileName, new InputSource(
									new InputStreamReader(is)));
//									new InputStreamReader(is, charset)));
					is.close();
					info.getDocument().getMetadata().setMimeType(
							mimeType.getName());
					is = new FileInputStream(currentFile);
					currentDirectory
							.store(transaction, broker, info, new InputSource(
									new InputStreamReader(is)), false);
//									new InputStreamReader(is, charset)), false);
					is.close();

				} else {
					// store as binary resource
					is = new FileInputStream(currentFile);

					BinaryDocument doc = currentDirectory.addBinaryResource(
							transaction, broker, fileName, is,
							mimeType.getName(), currentFile.length(), new Date(), new Date());

				}
			} catch (FileNotFoundException e) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, "error: ."); // TODO: error
				// description
				throw new SVNException(err);
			} catch (TriggerException e) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, "error: ."); // TODO: error
				// description
				throw new SVNException(err);
			} catch (EXistException e) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, "error: ."); // TODO: error
				// description
				throw new SVNException(err);
			} catch (PermissionDeniedException e) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, "error: ."); // TODO: error
				// description
				throw new SVNException(err);
			} catch (LockException e) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, "error: ."); // TODO: error
				// description
				throw new SVNException(err);
			} catch (IOException e) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, "error: ."); // TODO: error
				// description
				throw new SVNException(err);

			} catch (SAXException e) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, "error: ."); // TODO: error
				// description
				throw new SVNException(err);

			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException e) {
					}

				currentFile.delete();

			}

		} finally {
			currentTmpFile.delete();
		}
	}

	public void closeDir() throws SVNException {
		try {
			currentDirectory = broker.getCollection(currentDirectory.getParentURI());
		} catch (PermissionDeniedException e) {
			throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "error: ."), e);
		}

		currentPath = SVNPathUtil.removeTail(currentPath);
	}

	public void deleteEntry(String path, long revision) throws SVNException {
		System.out.println("deleteEntry");
	}

	public void absentDir(String path) throws SVNException {
		System.out.println("absentDir");
	}

	public void absentFile(String path) throws SVNException {
		System.out.println("absentFile");
	}

	public SVNCommitInfo closeEdit() throws SVNException {
		System.out.println("closeEdit");

		try {
			transact.commit(transaction);
		} catch (TransactionException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
                    "error: failed on transaction's commit.");
            throw new SVNException(err);
        } finally {
            transact.close(transaction);
        }

		return null;
	}

	public void abortEdit() throws SVNException {
		System.out.println("abortEdit");
	}
}
