/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.lock;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.util.LockException;
import org.exist.util.Stacktrace;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An Aspect which when compiled in to the codebase, enforces the locking constraints set by the {@link EnsureLocked}
 * and {@link EnsureContainerLocked} annotations.
 *
 * Typically this is envisaged only being used for development or debugging purposes, and is unlikely to be compiled
 * into a production application as the reflection overhead would likely be too much of a performance drain.
 *
 * When compiled into eXist-db, the aspect may be disabled by setting the system
 * property `exist.ensurelocking.disabled=true`.
 *
 * Throws a LockException(s) if the appropriate locks are not held and
 * the System property `exist.ensurelocking.enforce=true` is set.
 *
 * The System property `exist.ensurelocking.output` decides whether logger output goes to StdErr or the log file. Values
 * are `console` for StdOut/StdErr or `log` for the log file. The System property
 * `exist.ensurelocking.output.stack.depth` determines the length of the stack trace to output, by default this is 0.
 *
 * The System property `exist.ensurelocking.trace=true` can be enabled to show detail about the checks being performed.
 *
 * {@link EnsureLocked} on a parameter, ensures that a lock of the correct type is already held for that parameter.
 *
 * {@link EnsureLocked} on a method, ensures that the object returned by the method has gained a lock of the correct
 * type for the calling thread.
 *
 * {@link EnsureUnlocked} on a parameter, ensures that no locks are already held for that parameter.
 *
 * {@link EnsureUnlocked} on a method, ensures that the object returned by the method has no lock.
 *
 * {@link EnsureContainerLocked} on a method, ensures that the encapsulating object on which the method operates holds
 * a lock of the correct type before the method is called.
 *
 * {@link EnsureContainerUnlocked} on a method, ensures that the encapsulating object on which the method operates holds
 * no locks before the method is called.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@Aspect
public class EnsureLockingAspect {

    public static final String PROP_DISABLED = "exist.ensurelocking.disabled";
    public static final String PROP_ENFORCE = "exist.ensurelocking.enforce";
    public static final String PROP_OUTPUT = "exist.ensurelocking.output";
    public static final String PROP_OUTPUT_STACK_DEPTH = "exist.ensurelocking.output.stack.depth";
    public static final String PROP_TRACE = "exist.ensurelocking.trace";

    private static final boolean DISABLED = Boolean.parseBoolean(System.getProperty(PROP_DISABLED, "false"));
    private static final boolean ENFORCE = Boolean.parseBoolean(System.getProperty(PROP_ENFORCE, "false"));
    private static final boolean OUTPUT_TO_CONSOLE = System.getProperty(PROP_OUTPUT, "console").equals("console");
    private static final int OUTPUT_STACK_DEPTH = Integer.parseInt(System.getProperty(PROP_OUTPUT_STACK_DEPTH, "0"));
    private static final boolean TRACE = Boolean.parseBoolean(System.getProperty(PROP_TRACE, "false"));

    private static final Logger LOG = LogManager.getLogger(EnsureLockingAspect.class);


    @Pointcut("execution(* *(..,@org.exist.storage.lock.EnsureLocked (*),..))")
    public void methodWithEnsureLockedParameters() {
    }

    @Pointcut("execution(@org.exist.storage.lock.EnsureLocked (*) *(..))")
    public void methodWithEnsureLockedReturnType() {
    }

    @Pointcut("execution(@org.exist.storage.lock.EnsureContainerLocked (*) *(..))")
    public void methodWithEnsureContainerLocked() {
    }

    @Pointcut("execution(* *(..,@org.exist.storage.lock.EnsureUnlocked (*),..))")
    public void methodWithEnsureUnlockedParameters() {
    }

    @Pointcut("execution(@org.exist.storage.lock.EnsureUnlocked (*) *(..))")
    public void methodWithEnsureUnlockedReturnType() {
    }

    @Pointcut("execution(@org.exist.storage.lock.EnsureContainerUnlocked (*) *(..))")
    public void methodWithEnsureContainerUnlocked() {
    }

    /**
     * Ensures that the parameters to a method
     * annotated by {@link EnsureLocked} hold
     * the indicated locks.
     *
     @param joinPoint the join point of the aspect
     *
     * @throws LockException if the appropriate locks are not held and
     *  the System property `exist.ensurelocking.enforce=true` is set.
     */
    @Before("methodWithEnsureLockedParameters()")
    public void enforceEnsureLockedParameters(final JoinPoint joinPoint) throws LockException {

        if(DISABLED) {
            return;
        }

        final MethodSignature ms = (MethodSignature)joinPoint.getSignature();
        final Method method = ms.getMethod();
        final Object[] args = joinPoint.getArgs();

        final List<AnnotatedParameterConstraint<EnsureLocked>> ensureLockedParameters = getAllParameterAnnotations(method, EnsureLocked.class);
        for (final AnnotatedParameterConstraint<EnsureLocked> ensureLockedConstraint : ensureLockedParameters) {

            // check the lock constraint holds
            final LockManager lockManager = getLockManager();
            boolean failed = false;
            if (lockManager != null) {
                final int idx = ensureLockedConstraint.getParameterIndex();
                final Object arg = args[idx];

                // if the argument is null, and annotated @Nullable, we can skip the check
                if(arg == null && !getAllParameterAnnotations(method, Nullable.class).isEmpty()) {
                    traceln(() -> "Skipping method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + " for null argument(idx=" + idx + ") with @EnsureLocked @Nullable");
                    continue;
                }

                final EnsureLockDetail ensureLockDetail = resolveLockDetail(ensureLockedConstraint, args);
                traceln(() -> "Checking: method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + "( " + toAnnotationString(EnsureLocked.class, ensureLockDetail) + " " + ensureLockedConstraint.getParameter().getName() + ") ...");

                switch (ensureLockDetail.type) {
                    case COLLECTION:
                        final XmldbURI collectionUri;
                        if (XmldbURI.class.isAssignableFrom(arg.getClass())) {
                            collectionUri = (XmldbURI) arg;
                        } else {
                            collectionUri = ((Collection) arg).getURI();
                        }

                        if (!hasCollectionLock(lockManager, collectionUri, ensureLockDetail)) {
                            report("FAILED: Constraint to require lock mode " + ensureLockDetail.mode + " on Collection: " + collectionUri + " FAILED");
                            failed = true;
                        }
                        break;

                    case DOCUMENT:
                        final XmldbURI documentUri;
                        if (XmldbURI.class.isAssignableFrom(arg.getClass())) {
                            documentUri = (XmldbURI) arg;
                        } else {
                            documentUri = ((DocumentImpl) arg).getURI();
                        }

                        if (!hasDocumentLock(lockManager, documentUri, ensureLockDetail)) {
                            report("FAILED: Constraint to require lock mode " + ensureLockDetail.mode + " on Document: " + documentUri + " FAILED");
                            failed = true;
                        }
                        break;

                    default:
                        throw new UnsupportedOperationException("Currently only Collection or Document locks are supported");
                }
            }

            if(!failed) {
                traceln(() -> "PASSED.");
            }
        }
    }

    /**
     * Ensures that the object returned by a method
     * has an lock taken upon it before it is returned.
     *
     * @param joinPoint the join point of the aspect
     *
     * @param result the result of the instrumented method
     *
     * @throws LockException if the appropriate locks are not held and
     *  the System property `exist.ensurelocking.enforce=true` is set.
     *
     */
    @AfterReturning(value = "methodWithEnsureLockedReturnType()", returning = "result")
    public void enforceEnsureLockedReturnType(final JoinPoint joinPoint, final Object result) throws Throwable {

        if(DISABLED) {
            return;
        }

        final MethodSignature ms = (MethodSignature)joinPoint.getSignature();
        final Method method = ms.getMethod();

        final AnnotatedMethodConstraint<EnsureLocked> ensureLockedConstraint = getMethodAnnotation(method, EnsureLocked.class);
        final EnsureLockDetail ensureLockDetail = resolveLockDetail(ensureLockedConstraint, joinPoint.getArgs());

        traceln(() -> "Checking: " + toAnnotationString(EnsureLocked.class, ensureLockDetail) + " method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + " ...");

        // check the lock constraint holds
        boolean failed = false;
        if(result != null) {
            final LockManager lockManager = getLockManager();
            if (lockManager != null) {
                switch (ensureLockDetail.type) {
                    case COLLECTION:
                        final XmldbURI collectionUri;
                        if (XmldbURI.class.isAssignableFrom(result.getClass())) {
                            collectionUri = (XmldbURI) result;
                        } else {
                            collectionUri = ((Collection) result).getURI();
                        }

                        if (!hasCollectionLock(lockManager, collectionUri, ensureLockDetail)) {
                            report("FAILED: Constraint to require lock mode " + ensureLockDetail.mode + " on Collection: " + collectionUri);
                            failed = true;
                        }
                        break;

                    case DOCUMENT:
                        final XmldbURI documentUri;
                        if (XmldbURI.class.isAssignableFrom(result.getClass())) {
                            documentUri = (XmldbURI) result;
                        } else {
                            documentUri = ((DocumentImpl) result).getURI();
                        }

                        if (!hasDocumentLock(lockManager, documentUri, ensureLockDetail)) {
                            report("FAILED: Constraint to require lock mode " + ensureLockDetail.mode + " on Document: " + documentUri + " FAILED");
                            failed = true;
                        }
                        break;

                    default:
                        throw new UnsupportedOperationException("Currently only Collection or Document locks are supported");
                }
            }
        } else {
            traceln(() -> "Unable to check return type as value is null!");
        }

        if(!failed) {
            traceln(() -> "PASSED.");
        }
    }

    /**
     * Ensures that the appropriate lock is held on the container
     * object which houses the method before the method is called.
     *
     * @param joinPoint the join point of the aspect
     * @param container the object containing the instrumented method
     *
     * @throws LockException if any locks are held and
     *  the System property `exist.ensurelocking.enforce=true` is set.
     */
    @Before("methodWithEnsureContainerLocked() && target(container)")
    public void enforceEnsureLockedContainer(final JoinPoint joinPoint, final Object container) throws LockException {

        if(DISABLED) {
            return;
        }

        final MethodSignature ms = (MethodSignature)joinPoint.getSignature();
        final Method method = ms.getMethod();

        final AnnotatedMethodConstraint<EnsureContainerLocked> ensureContainerLockedConstraint =
                getMethodAnnotation(method, EnsureContainerLocked.class);
        final EnsureLockDetail ensureLockDetail = resolveContainerLockDetail(ensureContainerLockedConstraint, joinPoint.getArgs());

        traceln(() -> "Checking: " + toAnnotationString(EnsureContainerLocked.class, ensureLockDetail) + " method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + " ...");

        // check the lock constraint holds
        boolean failed = false;
        final LockManager lockManager = getLockManager();
        if (lockManager != null) {
            switch (ensureLockDetail.type) {
                case COLLECTION:
                    final XmldbURI collectionUri;
                    if (Collection.class.isAssignableFrom(container.getClass())) {
                        collectionUri = ((Collection) container).getURI();
                    } else {
                        throw new IllegalArgumentException("Container type was identified as Collection, but the container is not an implementation of Collection");
                    }

                    if (collectionUri == null) {
                        LOG.warn("collectionUri is null, unable to validate contract");
                        break;
                    }

                    if (!hasCollectionLock(lockManager, collectionUri, ensureLockDetail)) {
                        report("FAILED: Constraint to require lock mode " + ensureLockDetail.mode + " on Collection: " + collectionUri);
                        failed = true;
                    }
                    break;

                case DOCUMENT:
                    final XmldbURI documentUri;
                    if (DocumentImpl.class.isAssignableFrom(container.getClass())) {
                        documentUri = ((DocumentImpl) container).getURI();
                    } else {
                        throw new IllegalArgumentException("Container type was identified as Document, but the container is not an implementation of DocumentImpl");
                    }

                    if (documentUri == null) {
                        LOG.warn("documentUri is null, unable to validate contract");
                        break;
                    }

                    if (!hasDocumentLock(lockManager, documentUri, ensureLockDetail)) {
                        report("FAILED: Constraint to require lock mode " + ensureLockDetail.mode + " on Document: " + documentUri + " FAILED");
                        failed = true;
                    }
                    break;

                default:
                    throw new UnsupportedOperationException("Currently only Collection or Document container locks are supported");
            }
        }

        if(!failed) {
            traceln(() -> "PASSED.");
        }
    }

    /**
     * Ensures that the parameters to a method
     * annotated by {@link EnsureUnlocked} do not hold
     * any locks.
     *
     * @param joinPoint the join point of the aspect
     * @throws LockException if any locks are held and
     *  the System property `exist.ensurelocking.enforce=true` is set.
     */
    @Before("methodWithEnsureUnlockedParameters()")
    public void enforceEnsureUnlockedParameters(final JoinPoint joinPoint) throws LockException {

        if(DISABLED) {
            return;
        }

        final MethodSignature ms = (MethodSignature)joinPoint.getSignature();
        final Method method = ms.getMethod();
        final Object[] args = joinPoint.getArgs();

        final List<AnnotatedParameterConstraint<EnsureUnlocked>> ensureUnlockedParameters = getAllParameterAnnotations(method, EnsureUnlocked.class);
        for (final AnnotatedParameterConstraint<EnsureUnlocked> ensureUnlockedConstraint : ensureUnlockedParameters) {
            final Lock.LockType lockType = resolveLockDetail(ensureUnlockedConstraint);
            traceln(() -> "Checking: method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + "( " + toAnnotationString(EnsureUnlocked.class, lockType) + " " + ensureUnlockedConstraint.getParameter().getName() + ") ...");

            // check the lock constraint holds
            final LockManager lockManager = getLockManager();
            boolean failed = false;
            if (lockManager != null) {
                final int idx = ensureUnlockedConstraint.getParameterIndex();
                final Object arg = args[idx];

                // if the argument is null, and annotated @Nullable, we can skip the check
                if(arg == null && !getAllParameterAnnotations(method, Nullable.class).isEmpty()) {
                    traceln(() -> "Skipping method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + " for null argument(idx=" + idx + ") with @EnsureUnlocked @Nullable");
                    continue;
                }

                switch (lockType) {
                    case COLLECTION:
                        final XmldbURI collectionUri;
                        if (XmldbURI.class.isAssignableFrom(arg.getClass())) {
                            collectionUri = (XmldbURI) arg;
                        } else {
                            collectionUri = ((Collection) arg).getURI();
                        }

                        if (!hasNoCollectionLocks(lockManager, collectionUri)) {
                            report("FAILED: Constraint to require no locks on Collection: " + collectionUri + " FAILED");
                            failed = true;
                        }
                        break;

                    case DOCUMENT:
                        final XmldbURI documentUri;
                        if (XmldbURI.class.isAssignableFrom(arg.getClass())) {
                            documentUri = (XmldbURI) arg;
                        } else {
                            documentUri = ((DocumentImpl) arg).getURI();
                        }

                        if (!hasNoDocumentLocks(lockManager, documentUri)) {
                            report("FAILED: Constraint to require no locks on Document: " + documentUri + " FAILED");
                            failed = true;
                        }
                        break;

                    default:
                        throw new UnsupportedOperationException("Currently only Collection or Document locks are supported");
                }
            }

            if(!failed) {
                traceln(() -> "PASSED.");
            }
        }
    }

    /**
     * Ensures that the object returned by a method
     * has no lock held upon it before it is returned.
     * @param joinPoint the join point of the aspect
     * @param result the result of the instrumented method
     * @throws LockException if any locks are held and
     *  the System property `exist.ensurelocking.enforce=true` is set.
     */
    @AfterReturning(value = "methodWithEnsureUnlockedReturnType()", returning = "result")
    public void enforceEnsureUnlockedReturnType(final JoinPoint joinPoint, final Object result) throws Throwable {

        if(DISABLED) {
            return;
        }

        final MethodSignature ms = (MethodSignature)joinPoint.getSignature();
        final Method method = ms.getMethod();

        final AnnotatedMethodConstraint<EnsureUnlocked> ensureUnlockedConstraint = getMethodAnnotation(method, EnsureUnlocked.class);
        final Lock.LockType lockType = resolveLockDetail(ensureUnlockedConstraint);

        traceln(() -> "Checking: " + toAnnotationString(EnsureUnlocked.class, lockType) + " method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + " ...");

        // check the lock constraint holds
        boolean failed = false;
        if(result != null) {
            final LockManager lockManager = getLockManager();
            if (lockManager != null) {
                switch (lockType) {
                    case COLLECTION:
                        final XmldbURI collectionUri;
                        if (XmldbURI.class.isAssignableFrom(result.getClass())) {
                            collectionUri = (XmldbURI) result;
                        } else {
                            collectionUri = ((Collection) result).getURI();
                        }

                        if (!hasNoCollectionLocks(lockManager, collectionUri)) {
                            report("FAILED: Constraint to require no locks on Collection: " + collectionUri + " FAILED");
                            failed = true;
                        }
                        break;

                    case DOCUMENT:
                        final XmldbURI documentUri;
                        if (XmldbURI.class.isAssignableFrom(result.getClass())) {
                            documentUri = (XmldbURI) result;
                        } else {
                            documentUri = ((DocumentImpl) result).getURI();
                        }

                        if (!hasNoDocumentLocks(lockManager, documentUri)) {
                            report("FAILED: Constraint to require no locks on Document: " + documentUri + " FAILED");
                            failed = true;
                        }
                        break;

                    default:
                        throw new UnsupportedOperationException("Currently only Collection or Document locks are supported");
                }
            }
        } else {
            traceln(() -> "Unable to check return type as value is null!");
        }

        if(!failed) {
            traceln(() -> "PASSED.");
        }
    }

    /**
     * Ensures that the no locks are held on the container
     * object which houses the method before the method is called.
     * @param joinPoint the join point of the aspect
     * @param container the object containing the instrumented method

     * @throws LockException if any locks are held and
     *  the System property `exist.ensurelocking.enforce=true` is set.
     */
    @Before("methodWithEnsureContainerUnlocked() && target(container)")
    public void enforceEnsureUnlockedContainer(final JoinPoint joinPoint, final Object container) throws LockException {

        if(DISABLED) {
            return;
        }

        final MethodSignature ms = (MethodSignature)joinPoint.getSignature();
        final Method method = ms.getMethod();

        final AnnotatedMethodConstraint<EnsureContainerUnlocked> ensureContainerUnlockedConstraint =
                getMethodAnnotation(method, EnsureContainerUnlocked.class);
        final Lock.LockType lockType = resolveContainerLockDetail(ensureContainerUnlockedConstraint);

        traceln(() -> "Checking: " + toAnnotationString(EnsureContainerUnlocked.class, lockType) + " method=" + ms.getDeclaringType().getName() + "#" + ms.getName() + " ...");

        // check the lock constraint holds
        boolean failed = false;
        final LockManager lockManager = getLockManager();
        if (lockManager != null) {
            switch (lockType) {
                case COLLECTION:
                    final XmldbURI collectionUri;
                    if (Collection.class.isAssignableFrom(container.getClass())) {
                        collectionUri = ((Collection) container).getURI();
                    } else {
                        throw new IllegalArgumentException("Container type was identified as Collection, but the container is not an implementation of Collection");
                    }

                    if (!hasNoCollectionLocks(lockManager, collectionUri)) {
                        report("FAILED: Constraint to require no locks on Collection: " + collectionUri);
                        failed = true;
                    }
                    break;

                case DOCUMENT:
                    final XmldbURI documentUri;
                    if (DocumentImpl.class.isAssignableFrom(container.getClass())) {
                        documentUri = ((DocumentImpl) container).getURI();
                    } else {
                        throw new IllegalArgumentException("Container type was identified as Document, but the container is not an implementation of DocumentImpl");
                    }

                    if (!hasNoDocumentLocks(lockManager, documentUri)) {
                        report("FAILED: Constraint to require no locks on Document: " + documentUri + " FAILED");
                        failed = true;
                    }
                    break;

                default:
                    throw new UnsupportedOperationException("Currently only Collection or Document container locks are supported");
            }
        }

        if(!failed) {
            traceln(() -> "PASSED.");
        }
    }

    private @Nullable LockManager getLockManager() {
        if(BrokerPool.isConfigured()) {
            try {
                return BrokerPool.getInstance().getLockManager();
            } catch (final EXistException e) {
                throw new IllegalStateException(e);
            }
        } else {
            traceln(() -> "Waiting for BrokerPool to become available...");
            return null;
        }
    }

    private boolean hasDocumentLock(final LockManager lockManager, final XmldbURI documentUri, final EnsureLockDetail ensureLockDetail) {
        switch (ensureLockDetail.mode) {
            case READ_LOCK:
                return lockManager.isDocumentLockedForRead(documentUri) ||
                        lockManager.isDocumentLockedForWrite(documentUri);

            case WRITE_LOCK:
                return lockManager.isDocumentLockedForWrite(documentUri);

            case NO_LOCK:
                if(ensureLockDetail.modeWasFromParam) {
                    traceln(() -> "Nothing to trace for NO_LOCK");  // TODO(AR) consider implementation strategies? although it is likely we will obsolete NO_LOCK
                    return true;
                }
                //intentional fallthrough

            default:
                throw new UnsupportedOperationException("Currently only READ or WRITE lock modes are supported");
        }
    }


    /**
     * Checks if a Collection is locked explicitly, or implicitly through a parent lock for the correct mode on the sub-tree.
     *
     * @true if a collection is locked either explicitly or implicitly
     */
    private boolean hasCollectionLock(final LockManager lockManager, final XmldbURI collectionUri, final EnsureLockDetail ensureLockDetail) {
        XmldbURI uri = collectionUri;
        while(uri.numSegments() > 0) {

            switch (ensureLockDetail.mode) {
                case READ_LOCK:
                    if(lockManager.isCollectionLockedForRead(uri) ||
                            lockManager.isCollectionLockedForWrite(uri)) {
                        return true;
                    }
                    break;

                case WRITE_LOCK:
                    if(lockManager.isCollectionLockedForWrite(uri)) {
                        return true;
                    }
                    break;

                case NO_LOCK:
                    if(ensureLockDetail.modeWasFromParam) {
                        traceln(() -> "Nothing to trace for NO_LOCK");  // TODO(AR) consider implementation strategies? although it is likely we will obsolete NO_LOCK
                        return true;
                    }
                    //intentional fallthrough

                default:
                    throw new UnsupportedOperationException("Currently only READ or WRITE lock modes are supported");
            }

            if (uri.numSegments() == 2 && uri.getCollectionPath().equals("/db")) {
                // we are at the root!
                break;
            }

            // loop round to parent collection
            uri = uri.removeLastSegment();
        }

        return false;
    }

    private boolean hasNoDocumentLocks(final LockManager lockManager, final XmldbURI documentUri) {
        return !(lockManager.isDocumentLockedForRead(documentUri)
                && lockManager.isDocumentLockedForWrite(documentUri));
    }

    private boolean hasNoCollectionLocks(final LockManager lockManager, final XmldbURI collectionUri) {
        return !(lockManager.isCollectionLockedForRead(collectionUri)
                && lockManager.isCollectionLockedForWrite(collectionUri));
    }

    private <T extends Annotation>  String toAnnotationString(final Class<T> annotationClass, final EnsureLockDetail ensureLockDetail) {
        return "@" + annotationClass.getSimpleName() + "(mode=" + ensureLockDetail.mode + ", type=" + ensureLockDetail.type + ")";
    }

    private <T extends Annotation>  String toAnnotationString(final Class<T> annotationClass, final Lock.LockType lockType) {
        return "@" + annotationClass.getSimpleName() + "(type=" + lockType + ")";
    }

    private EnsureLockDetail resolveContainerLockDetail(final AnnotatedMethodConstraint<EnsureContainerLocked> lockConstraint, final Object args[]) {
        final Tuple2<Lock.LockMode, Boolean> mode = getLockMode(lockConstraint.getAnnotation(), args);

        final Lock.LockType type;
        if(Collection.class.isAssignableFrom(lockConstraint.getMethod().getDeclaringClass())) {
            type = Lock.LockType.COLLECTION;
        } else if(Document.class.isAssignableFrom(lockConstraint.getMethod().getDeclaringClass())) {
            type = Lock.LockType.DOCUMENT;
        } else {
            // error
            throw new IllegalArgumentException("@EnsureContainerLocked is specified on a method whose container object is neither a Collection nor a Document");
        }

        return new EnsureLockDetail(mode._1, mode._2, type);
    }

    private EnsureLockDetail resolveLockDetail(final AnnotatedMethodConstraint<EnsureLocked> lockConstraint, final Object args[]) {
        final Tuple2<Lock.LockMode, Boolean> mode = getLockMode(lockConstraint.getAnnotation(), args);

        final Lock.LockType type;
        if (lockConstraint.getAnnotation().type() != Lock.LockType.UNKNOWN) {
            type = lockConstraint.getAnnotation().type();
        } else if (Collection.class.isAssignableFrom(lockConstraint.getMethod().getReturnType())) {
            type = Lock.LockType.COLLECTION;
        } else if (Document.class.isAssignableFrom(lockConstraint.getMethod().getReturnType())) {
            type = Lock.LockType.DOCUMENT;
        } else {
            // error
            throw new IllegalArgumentException("@EnsureLocked is specified on a method that returns neither a Collection nor a Document");
        }

        return new EnsureLockDetail(mode._1, mode._2, type);
    }

    private EnsureLockDetail resolveLockDetail(final AnnotatedParameterConstraint<EnsureLocked> lockConstraint, final Object args[]) {
        final Tuple2<Lock.LockMode, Boolean> mode = getLockMode(lockConstraint.getAnnotation(), args);

        final Lock.LockType type;
        if (lockConstraint.getAnnotation().type() != Lock.LockType.UNKNOWN) {
            type = lockConstraint.getAnnotation().type();
        } else if (Collection.class.isAssignableFrom(lockConstraint.getParameter().getType())
                || args[lockConstraint.getParameterIndex()] instanceof Collection) {
            type = Lock.LockType.COLLECTION;
        } else if (Document.class.isAssignableFrom(lockConstraint.getParameter().getType())
                || args[lockConstraint.getParameterIndex()] instanceof Document) {
            type = Lock.LockType.DOCUMENT;
        } else if (XmldbURI.class.isAssignableFrom(lockConstraint.getParameter().getType())) {
            throw new IllegalArgumentException("@EnsureLocked is specified on an XmldbURI method parameter, but is missing the `lockType` value");
        } else {
            // error
            throw new IllegalArgumentException("@EnsureLocked is specified on a method parameter that is neither a Collection, Document, nor an XmldbURI");
        }

        return new EnsureLockDetail(mode._1, mode._2, type);
    }

    private Lock.LockType resolveContainerLockDetail(final AnnotatedMethodConstraint<EnsureContainerUnlocked> lockConstraint) {
        final Lock.LockType type;
        if(Collection.class.isAssignableFrom(lockConstraint.getMethod().getDeclaringClass())) {
            type = Lock.LockType.COLLECTION;
        } else if(Document.class.isAssignableFrom(lockConstraint.getMethod().getDeclaringClass())) {
            type = Lock.LockType.DOCUMENT;
        } else {
            // error
            throw new IllegalArgumentException("@EnsureContainerUnlocked is specified on a method whose container object is neither a Collection nor a Document");
        }

        return type;
    }

    private Lock.LockType resolveLockDetail(final AnnotatedMethodConstraint<EnsureUnlocked> lockConstraint) {
        final Lock.LockType type;
        if (lockConstraint.getAnnotation().type() != Lock.LockType.UNKNOWN) {
            type = lockConstraint.getAnnotation().type();
        } else if (Collection.class.isAssignableFrom(lockConstraint.getMethod().getReturnType())) {
            type = Lock.LockType.COLLECTION;
        } else if (Document.class.isAssignableFrom(lockConstraint.getMethod().getReturnType())) {
            type = Lock.LockType.DOCUMENT;
        } else {
            // error
            throw new IllegalArgumentException("@EnsureUnlocked is specified on a method that returns neither a Collection nor a Document");
        }

        return type;
    }

    private Lock.LockType resolveLockDetail(final AnnotatedParameterConstraint<EnsureUnlocked> lockConstraint) {
        final Lock.LockType type;
        if (lockConstraint.getAnnotation().type() != Lock.LockType.UNKNOWN) {
            type = lockConstraint.getAnnotation().type();
        } else if (Collection.class.isAssignableFrom(lockConstraint.getParameter().getType())) {
            type = Lock.LockType.COLLECTION;
        } else if (Document.class.isAssignableFrom(lockConstraint.getParameter().getType())) {
            type = Lock.LockType.DOCUMENT;
        } else if (XmldbURI.class.isAssignableFrom(lockConstraint.getParameter().getType())) {
            throw new IllegalArgumentException("@EnsureUnlocked is specified on an XmldbURI method parameter, but is missing the `lockType` value");
        } else {
            // error
            throw new IllegalArgumentException("@EnsureUnlocked is specified on a method parameter that is neither a Collection, Document, nor an XmldbURI");
        }

        return type;
    }


    private Tuple2<Lock.LockMode, Boolean> getLockMode(final EnsureLocked ensureLocked, final Object args[]) {
        return getLockMode(ensureLocked.mode(), ensureLocked.modeParam(), args);
    }

    private Tuple2<Lock.LockMode, Boolean> getLockMode(final EnsureContainerLocked ensureContainerLocked, final Object args[]) {
        return getLockMode(ensureContainerLocked.mode(), ensureContainerLocked.modeParam(), args);
    }

    /**
     * @return A tuple, whose first value is the lock mode,
     * and whose second value is true if the mode was resolved from args.
     */
    private Tuple2<Lock.LockMode, Boolean> getLockMode(final Lock.LockMode specifiedLockMode, final short specifiedLockModeParam, final Object args[]) {
        final Tuple2<Lock.LockMode, Boolean> mode;
        if(specifiedLockMode != Lock.LockMode.NO_LOCK) {
            mode = new Tuple2<>(specifiedLockMode, false);
        } else if (specifiedLockModeParam != EnsureLocked.NO_MODE_PARAM) {
            final short idx = specifiedLockModeParam;
            if(idx < args.length) {
                final Object arg = args[idx];
                if(arg instanceof Lock.LockMode) {
                    mode = new Tuple2<>((Lock.LockMode)arg, true);
                } else {
                    throw new IllegalArgumentException("modeParam was specified on @EnsureLocked but its index was not a Lock.LockMode parameter, found:  " + arg.getClass().getName());
                }
            } else {
                throw new IllegalArgumentException("modeParam was specified on @EnsureLocked but its index was out-of-bounds");
            }
        } else {
            final List<Lock.LockMode> lockModeArgs = getLockModeArgs(args);
            if(lockModeArgs.size() == 1) {
                mode = new Tuple2<>(lockModeArgs.getFirst(), true);
            } else if(lockModeArgs.isEmpty()) {
                throw new IllegalArgumentException("No mode or modeParam was specified on @EnsureLocked and no LockMode parameter was found");
            } else {
                throw new IllegalArgumentException("No mode or modeParam was specified on @EnsureLocked and more than one LockMode parameter was found");
            }
        }

        return mode;
    }

    private List<Lock.LockMode> getLockModeArgs(final Object[] args) {
        final List<Lock.LockMode> lockModeArgs = new ArrayList<>();
        for(final Object arg : args) {
            if(arg instanceof Lock.LockMode) {
                lockModeArgs.add((Lock.LockMode)arg);
            }
        }
        return lockModeArgs;
    }

    private @Nullable <T extends Annotation> AnnotatedMethodConstraint<T> getMethodAnnotation(final Method method, final Class<T> annotationClass) {
        final T methodAnnotation = method.getDeclaredAnnotation(annotationClass);
        if(methodAnnotation != null) {
            return new AnnotatedMethodConstraint<>(methodAnnotation, method);
        }

        final Class declaringClazz = method.getDeclaringClass();

        final Class superClazz = declaringClazz.getSuperclass();
        if(superClazz != null && !superClazz.equals(Object.class)) {
            final Method superMethod = findMethodOnOtherClass(method, superClazz);
            if (superMethod != null) {
                final AnnotatedMethodConstraint<T> superMethodAnnotation =
                        getMethodAnnotation(superMethod, annotationClass);
                if(superMethodAnnotation != null) {
                    return superMethodAnnotation;
                }
            }
        }

        for (final Class interfaceClazz  : declaringClazz.getInterfaces()) {
            final Method interfaceMethod = findMethodOnOtherClass(method, interfaceClazz);
            if (interfaceMethod != null) {
                final AnnotatedMethodConstraint<T> interfaceMethodAnnotation =
                        getMethodAnnotation(interfaceMethod, annotationClass);
                if(interfaceMethodAnnotation != null) {
                    return interfaceMethodAnnotation;
                }
            }
        }

        return null;
    }

    private <T extends Annotation> List<AnnotatedParameterConstraint<T>> getAllParameterAnnotations(final Method method,
            final Class<T> annotationClass) {
        final List<AnnotatedParameterConstraint<T>> annotatedParameters = new ArrayList<>();
        getAllParameterAnnotations(method, annotationClass, annotatedParameters);
        return annotatedParameters;
    }

    private <T extends Annotation> void getAllParameterAnnotations(final Method method, final Class<T> annotationClass,
            final List<AnnotatedParameterConstraint<T>> results) {
        final Parameter[] parameters = method.getParameters();
        for(int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];
            final T parameterAnnotation = parameter.getDeclaredAnnotation(annotationClass);
            if(parameterAnnotation != null) {
                results.add(new AnnotatedParameterConstraint<>(parameterAnnotation, parameter, i));
            }
        }

        final Class declaringClazz = method.getDeclaringClass();

        final Class superClazz = declaringClazz.getSuperclass();
        if(superClazz != null && !superClazz.equals(Object.class)) {
            final Method superMethod = findMethodOnOtherClass(method, superClazz);
            if (superMethod != null) {
                getAllParameterAnnotations(superMethod, annotationClass, results);
            }
        }

        for (final Class interfaceClazz  : declaringClazz.getInterfaces()) {
            final Method interfaceMethod = findMethodOnOtherClass(method, interfaceClazz);
            if (interfaceMethod != null) {
                getAllParameterAnnotations(interfaceMethod, annotationClass, results);
            }
        }
    }

    private @Nullable Method findMethodOnOtherClass(final Method method, final Class otherClazz) {
        try {
            return otherClazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
        } catch (final NoSuchMethodException e) {
            // nothing to do
            return null;
        }
    }

    private static void report(final String message) throws LockException {
        final String reportMessage;
        if(OUTPUT_STACK_DEPTH > 0) {
            reportMessage = message + ": " + Stacktrace.asString(Stacktrace.substack(Thread.currentThread().getStackTrace(), 2, OUTPUT_STACK_DEPTH));
        } else {
            reportMessage = message;
        }

        if(OUTPUT_TO_CONSOLE) {
            System.err.println(reportMessage);
        } else {
            LOG.error(reportMessage);
        }

        if(ENFORCE) {
            throw new LockException(message);
        }
    }

    private static void traceln(final Supplier<String> messageFn) {
        if(TRACE) {
            if(OUTPUT_TO_CONSOLE) {
                System.out.println(messageFn.get());
            } else {
                LOG.trace(messageFn.get());
            }
        }
    }

    private static class AnnotatedParameterConstraint<T extends Annotation> extends AnnotatedConstraint<T, Parameter> {
        private final int parameterIndex;
        public AnnotatedParameterConstraint(final T annotation, final Parameter parameter, final int parameterIndex) {
            super(annotation, parameter);
            this.parameterIndex = parameterIndex;
        }

        public Parameter getParameter() {
            return annotationTarget;
        }

        public int getParameterIndex() {
            return parameterIndex;
        }
    }

    private static class AnnotatedMethodConstraint<T extends Annotation> extends AnnotatedConstraint<T, Method> {
        public AnnotatedMethodConstraint(final T annotation, final Method method) {
            super(annotation, method);
        }

        public Method getMethod() {
            return annotationTarget;
        }
    }

    private static abstract class AnnotatedConstraint<T extends Annotation, U> {
        private final T annotation;
        protected final U annotationTarget;

        public AnnotatedConstraint(final T annotation, final U annotationTarget) {
            this.annotation = annotation;
            this.annotationTarget = annotationTarget;
        }

        public T getAnnotation() {
            return annotation;
        }
    }

    private static class EnsureLockDetail {
        private final Lock.LockMode mode;
        private final boolean modeWasFromParam;
        private final Lock.LockType type;

        public EnsureLockDetail(final Lock.LockMode mode, final boolean modeWasFromParam, final Lock.LockType type) {
            this.mode = mode;
            this.modeWasFromParam = modeWasFromParam;
            this.type = type;
        }
    }
}
