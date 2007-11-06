package org.exist.fluent;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.*;

import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;

/**
 * A superclass for database unit tests.  It takes care of starting up and clearing the database in
 * its <code>setUp</code> method, and supports mocking with jMock.  By default, the database
 * will be configured from the file "conf.xml" in the current directory, but you can annotate your
 * test class with {@link DatabaseTestCase.ConfigFile} to specify a different one.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
@DatabaseTestCase.ConfigFile("conf.xml")
public abstract class DatabaseTestCase {
	
	/**
	 * An annotation that specifies the path of the config file to use when setting up the database
	 * for a test.
	 * 
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	@Inherited @Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
	public @interface ConfigFile {
		String value();
	}
	
	protected Database db;
    
	@Before public void startupDatabase() {
		ConfigFile configFileAnnotation = getClass().getAnnotation(ConfigFile.class);
		assert configFileAnnotation != null;
		Database.ensureStarted(new File(configFileAnnotation.value()));
		db = new Database(SecurityManager.SYSTEM_USER);
		wipeDatabase();
		ListenerManager.configureTriggerDispatcher(db);	// config file gets erased by command above
	}

	@After public void shutdownDatabase() throws Exception {
		if (Database.isStarted()) {
			wipeDatabase();
			Database.shutdown();
		}
	}
    
	private void wipeDatabase() {
		DBBroker broker = null;
		Transaction tx = Database.requireTransaction();
		try {
			broker = db.acquireBroker();
			broker.removeCollection(tx.tx, broker.getCollection(XmldbURI.ROOT_COLLECTION_URI));
			tx.commit();
		} catch (PermissionDeniedException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			tx.abortIfIncomplete();
			db.releaseBroker(broker);
		}
	}

}