package org.exist.fluent;

import java.io.File;
import java.lang.annotation.*;

import org.exist.collections.Collection;
import org.exist.security.SecurityManager;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.junit.*;

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
	
	protected static Database db;
    
	@Before public void startupDatabase() throws Exception {
		if (!Database.isStarted()) {
			ConfigFile configFileAnnotation = getClass().getAnnotation(ConfigFile.class);
			if (configFileAnnotation == null)
				throw new DatabaseException("Missing ConfigFile annotation on DatabaseTestCase subclass");
			Database.startup(new File(configFileAnnotation.value()));
			db = null;
		}
		if (db == null) db = new Database(SecurityManager.SYSTEM_USER);
		wipeDatabase();
		ListenerManager.configureTriggerDispatcher(db);	// config file gets erased by wipeDatabase()
	}
	
	@After public void shutdownDatabase() throws Exception {
		if (Database.isStarted()) {
			// TODO: a bug in eXist's removeCollection(root) means that we need to shut down immediately
			// after wiping every time, otherwise the database gets corrupted.  When this is fixed, this
			// method can become a static @AfterClass method for increased performance.
			wipeDatabase();
			Database.shutdown();
			db = null;
		}
	}
    
	private static void wipeDatabase() throws Exception {
		DBBroker broker = null;
		Transaction tx = Database.requireTransaction();
		try {
			broker = db.acquireBroker();
			Collection root = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
			broker.removeCollection(tx.tx, root);
			tx.commit();
		} finally {
			tx.abortIfIncomplete();
			db.releaseBroker(broker);
		}
	}

}