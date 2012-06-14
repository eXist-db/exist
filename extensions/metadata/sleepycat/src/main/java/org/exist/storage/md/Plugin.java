package org.exist.storage.md;

import org.exist.plugin.Jack;
import org.exist.plugin.PluginsManager;
import org.exist.security.PermissionDeniedException;

public class Plugin implements Jack {
	
	MetaDataImpl md;
	
	public Plugin(PluginsManager manager) throws PermissionDeniedException {
		md = new MetaDataImpl(manager.getDatabase());
		
		manager.getDatabase().getDocumentTriggers().add(new DocumentEvents());
		manager.getDatabase().getCollectionTriggers().add(new CollectionEvents());
	}
	
	@Override
	public void sync() {
		md.sync();
	}

	@Override
	public void stop() {
		md.close();
	}
}
