package org.exist.storage.lock;

import org.exist.xmldb.XmldbURI;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ManagedCollectionLock extends ManagedLock<MultiLock[]> {

    private final XmldbURI collectionUri;

    public ManagedCollectionLock(final XmldbURI collectionUri, final MultiLock[] locks, final Runnable closer) {
        super(locks, closer);
        this.collectionUri = collectionUri;
    }

    public XmldbURI getPath() {
        return collectionUri;
    }

    public static ManagedCollectionLock notLocked(final XmldbURI collectionUri) {
        return new ManagedCollectionLock(collectionUri, null, () -> {});
    }
}
