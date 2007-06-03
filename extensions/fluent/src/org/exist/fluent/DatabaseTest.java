package org.exist.fluent;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.*;

import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.jmock.cglib.MockObjectTestCase;

/**
 * A superclass for database unit tests.  It takes care of starting up and clearing the database in
 * its <code>setUp</code> method, and supports mocking with jMock.  By default, the database
 * will be configured from the file "conf.xml" in the current directory, but you can annotate your
 * test class with {@link DatabaseTest.ConfigFile} to specify a different one.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
@DatabaseTest.ConfigFile("conf.xml")
public abstract class DatabaseTest extends MockObjectTestCase {
	
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
	@Override protected void setUp() {
		ConfigFile configFileAnnotation = getClass().getAnnotation(ConfigFile.class);
		assert configFileAnnotation != null;
		Database.ensureStarted(new File(configFileAnnotation.value()));
		db = new Database(SecurityManager.SYSTEM_USER);
		DBBroker broker = null;
		Transaction tx = Database.requireTransaction();
		try {
			broker = db.acquireBroker();
			broker.removeCollection(tx.tx, broker.getCollection(XmldbURI.ROOT_COLLECTION_URI));
			tx.commit();
			ListenerManager.configureTriggerDispatcher(db);	// config file gets erased by command above
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