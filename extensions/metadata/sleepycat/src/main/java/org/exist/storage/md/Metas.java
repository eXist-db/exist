package org.exist.storage.md;

import com.sleepycat.persist.EntityCursor;

public interface Metas {

	public String getUUID();

	public Meta get(String key);

	public Meta put(String key, String value);

	public EntityCursor<MetaImpl> keys();
}
