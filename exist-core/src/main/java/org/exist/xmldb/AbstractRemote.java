package org.exist.xmldb;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.exist.security.ACLPermission;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.PermissionAiderFactory;

/**
 * Base class for Remote XMLDB classes
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class AbstractRemote {

    protected RemoteCollection collection;

    AbstractRemote(final RemoteCollection collection) {
        this.collection = collection;
    }

    protected XmldbURI resolve(final XmldbURI name) {
        if (collection != null) {
            return collection.getPathURI().resolveCollectionPath(name);
        } else {
            return name;
        }
    }
    
    protected Stream<ACEAider> extractAces(final Object aclParameter) {
        return Optional.ofNullable((Object[])aclParameter)
                .map(Arrays::stream)
                .map(stream -> stream.map(o -> (ACEAider)o))
                .orElse(Stream.<ACEAider>empty());
    }
    
    protected Permission getPermission(final String owner, final String group, final int mode, final Stream<ACEAider> aces) throws PermissionDeniedException {
        final Permission perm = PermissionAiderFactory.getPermission(owner, group, mode);
        if(perm instanceof ACLPermission) {
            final ACLPermission aclPermission = (ACLPermission)perm;
            for(final ACEAider ace : aces.collect(Collectors.toList())) {
                aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
            }
        }
        return perm;
    }
}
