package org.exist.storage.md;

import org.exist.dom.DocumentImpl;
import org.exist.xmldb.XmldbURI;

import com.eaio.uuid.UUID;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

import static com.sleepycat.persist.model.Relationship.ONE_TO_ONE;

@Entity//(version=1) //http://docs.oracle.com/cd/E17076_02/html/java/com/sleepycat/persist/evolve/Conversion.html
public class MetasImpl implements Metas {
	
	@PrimaryKey private String uuid;
	
	@SecondaryKey(relate=ONE_TO_ONE,
				  onRelatedEntityDelete=DeleteAction.CASCADE) 
	protected String uri;

//	protected byte rType = 0;
	
	@SuppressWarnings("unused")
	private MetasImpl() {}

	protected MetasImpl(DocumentImpl doc) {
		update(doc);
		
		if (doc.getUUID() == null)
			uuid = (new UUID()).toString();
		else
			uuid = doc.getUUID().toString();
	}
	
	protected MetasImpl(XmldbURI uri) {
		this.uri = uri.toString();
	}

	public String getUUID() {
		return uuid;
	}

	public Meta put(String key, String value) {
		MetaImpl m = (MetaImpl)get(key);
		if (m == null)
			return MetaDataImpl._.addMeta(this, key, value);
		
		else {
			m.value = value;

			MetaDataImpl._.addMeta(m);
		}
		
		return m;
	}

	public Meta get(String key) {
		return MetaDataImpl._.getMeta(this, key);
	}

	protected void update(DocumentImpl doc) {
		uri = doc.getURI().toString();
//		rType = doc.getResourceType();
	}
	
	public EntityCursor<MetaImpl> keys() {
		return MetaDataImpl._.getMetaKeys(this);
	}

	public void restore(String uuid, String key, String value) {
		MetaDataImpl._.addMeta(this, uuid, key, value);
	}
}