package org.exist.storage.lock;

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.xmldb.XmldbURI;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class ManagedCollectionLock extends ManagedLock<Either<java.util.concurrent.locks.Lock, Tuple2<java.util.concurrent.locks.Lock, java.util.concurrent.locks.Lock>>> {

    private final XmldbURI collectionUri;

    public ManagedCollectionLock(final XmldbURI collectionUri, final Either<java.util.concurrent.locks.Lock, Tuple2<java.util.concurrent.locks.Lock, java.util.concurrent.locks.Lock>> lock, final Runnable closer) {
        super(lock, closer);
        this.collectionUri = collectionUri;
    }

    public XmldbURI getPath() {
        return collectionUri;
    }
}
