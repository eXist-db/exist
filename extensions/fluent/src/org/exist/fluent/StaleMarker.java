package org.exist.fluent;

class StaleMarker {
	
	private boolean stale;

	synchronized void mark() {
		stale = true;
	}
	
	synchronized void check() {
		if (stale) throw new DatabaseException("stale reference to database object");
	}
	
	void track(String path) {
		Database.trackStale(path, this);
	}
	
}
