package org.exist.fluent;

/**
 * An action being undertaken on the database, used to characterize an event.
 * Note that for folders, <code>UPDATE</code> means rename.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public enum Trigger {
	BEFORE_STORE,
	AFTER_STORE,
	BEFORE_CREATE,
	AFTER_CREATE,
	BEFORE_UPDATE,
	AFTER_UPDATE,
	BEFORE_RENAME,
	AFTER_RENAME,
	BEFORE_MOVE,
	AFTER_MOVE,
	BEFORE_REMOVE,
	AFTER_REMOVE,
        BEFORE_UPDATE_META,
        AFTER_UPDATE_META
}
