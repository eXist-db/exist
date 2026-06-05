/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
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
package org.exist.xquery.modules.expathrepo;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.repo.ExistRepository;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.UserInteractionStrategy;
import org.expath.pkg.repo.tui.BatchUserInteraction;


/**
 * Remove Function: Remove package from repository
 *
 * @author <a href="mailto:jim.fuller@exist-db.org">James Fuller</a>
 * @author cutlass
 * @author ljo
 */
public class RemoveFunction extends BasicFunction {
    private final static Logger LOG = LogManager.getLogger(RemoveFunction.class);

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("remove", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
                    "Remove package, pkgName, from repository. Throws EXPATH007 with the underlying cause on failure (e.g. package not found, dependent packages still installed, filesystem permission error).",
                    new SequenceType[]{new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the package was removed"));

    public RemoveFunction(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final boolean force = false;
        final UserInteractionStrategy interact = new BatchUserInteraction();
        final String pkg = args[0].getStringValue();

        final Optional<ExistRepository> repo = getContext().getRepository();
        if (repo.isEmpty()) {
            throw new XPathException(this, EXPathErrorCode.EXPDY007, "expath repository not available");
        }

        try {
            final Repository parentRepo = repo.get().getParentRepo();
            parentRepo.removePackage(pkg, force, interact);
            repo.get().reportAction(ExistRepository.Action.UNINSTALL, pkg);
            context.getBroker().getBrokerPool().getXQueryPool().clear();
        } catch (final PackageException pe) {
            // Previously this catch returned BooleanValue.FALSE and swallowed the exception
            // (with a TODO from the previous author noting this should throw with the
            // underlying cause). The silent false offered no diagnostic, and downstream
            // tooling had no way to surface the actual failure to the user. Now we log and
            // rethrow so both the eXist log and the caller see the original cause.
            LOG.warn("Failed to remove package {}: {}", pkg, pe.getMessage(), pe);
            throw new XPathException(this, EXPathErrorCode.EXPDY007,
                    "Failed to remove package " + pkg + ": " + pe.getMessage(), pe);
        }
        return BooleanValue.TRUE;
    }
}
