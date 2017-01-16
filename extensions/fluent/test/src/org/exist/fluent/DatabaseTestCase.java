package org.exist.fluent;

import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import java.lang.annotation.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.exist.collections.Collection;
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
		ConfigFile configFileAnnotation = getClass().getAnnotation(ConfigFile.class);
		if (configFileAnnotation == null)
			throw new DatabaseException("Missing ConfigFile annotation on DatabaseTestCase subclass");
		Path configFile = Paths.get(configFileAnnotation.value());
		if (!Database.isStarted()) {
			Database.startup(configFile);
			db = null;
		}
		if (db == null) db = Database.login("admin", "");
		wipeDatabase();
		Database.configureRootCollection(configFile);	// config file gets erased by wipeDatabase()
	}
	
	@AfterClass public static void shutdownDatabase() throws Exception {
		if (Database.isStarted()) {
			wipeDatabase();
			Database.shutdown();
			db = null;
		}
	}
    
	private static void wipeDatabase() throws Exception {
		Transaction tx = db.requireTransactionWithBroker();
		try {
			Collection root = tx.broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
			for (Iterator<XmldbURI> it = root.collectionIterator(tx.broker); it.hasNext(); ) {
				XmldbURI childName = it.next();
				if (!childName.getCollectionPath().equals(XmldbURI.SYSTEM_COLLECTION_NAME)) {
					tx.broker.removeCollection(tx.tx, tx.broker.getCollection(root.getURI().append(childName)));
				}
			}
			for (Iterator<DocumentImpl> it = root.iterator(tx.broker); it.hasNext(); ) {
				DocumentImpl doc = it.next();
				if (doc instanceof BinaryDocument) {
					root.removeBinaryResource(tx.tx, tx.broker, doc);
				} else {
					root.removeXMLResource(tx.tx, tx.broker, doc.getFileURI());
				}
			}
			tx.commit();
		} finally {
			tx.abortIfIncomplete();
		}
	}

}