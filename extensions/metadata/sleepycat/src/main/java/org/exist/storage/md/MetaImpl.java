package org.exist.storage.md;

import com.eaio.uuid.UUID;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;

@Entity//(version=1)
public class MetaImpl implements Meta {
	
	@PrimaryKey private String uuid;
	
	@SecondaryKey(relate=MANY_TO_ONE,
				  onRelatedEntityDelete=DeleteAction.CASCADE)
	private String object;
	
	@SecondaryKey(relate=MANY_TO_ONE,
				  onRelatedEntityDelete=DeleteAction.CASCADE)
	private String key;
	
	@SecondaryKey(relate=MANY_TO_ONE,
			  onRelatedEntityDelete=DeleteAction.CASCADE)
	protected String value;
	
	@SuppressWarnings("unused")
	private MetaImpl() {}

	protected MetaImpl(String objectUUID, String k, String v) {
		this(objectUUID, (new UUID()).toString(), k, v);
	}
	
	protected MetaImpl(String objectUUID, String uuid, String k, String v) {
		this.uuid = uuid;
		object = objectUUID;
		
		key = k;
		value = v;
	}

	public String getUUID() {
		return uuid;
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public String getObject() {
		return object;
	}
}
