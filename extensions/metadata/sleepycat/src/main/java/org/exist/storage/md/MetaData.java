package org.exist.storage.md;

import java.util.List;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;

public abstract class MetaData {

    public final static boolean enabled = true;

	protected static MetaData _ = null;
	
	public static MetaData get() {
		return _;
	}
	
	protected final static Logger LOG = Logger.getLogger(MetaData.class);

	public abstract DocumentImpl getDocument(String uuid) throws EXistException, PermissionDeniedException;

	public abstract List<DocumentImpl> matchDocuments(String key, String value) throws EXistException, PermissionDeniedException;

	public abstract Metas addMetas(DocumentImpl doc);

//	public abstract Metas getMetas(DocumentImpl doc);
	public abstract Metas getMetas(XmldbURI uri);

//	public abstract void delMetas(DocumentImpl doc);
	public abstract void delMetas(XmldbURI uri);

	public abstract void sync();

	public abstract void close();
}
