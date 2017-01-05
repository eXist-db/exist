package org.exist.storage.lock;

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.xmldb.XmldbURI;

/**
 * Created by aretter on 21/01/2017.
 */
public class ManagedCollectionLock extends ManagedLock<Either<ReentrantReadWriteLock, Tuple2<ReentrantReadWriteLock, ReentrantReadWriteLock>>> {

    public ManagedCollectionLock(final Either<ReentrantReadWriteLock, Tuple2<ReentrantReadWriteLock, ReentrantReadWriteLock>> lock, final Runnable closer) {
        super(lock, closer);
    }

    public XmldbURI getPath() {
        return lock.fold(lock -> XmldbURI.create(lock.getId()), (parentLockAndLock) -> XmldbURI.create(parentLockAndLock._2.getId()));
    }
}
