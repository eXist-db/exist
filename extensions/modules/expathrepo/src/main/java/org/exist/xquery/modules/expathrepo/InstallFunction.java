/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2017 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.expathrepo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.repo.ExistPkgInfo;
import org.exist.repo.ExistRepository;
import org.exist.security.PermissionDeniedException;
import org.exist.repo.ClasspathHelper;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.tui.BatchUserInteraction;


/**
 * Install Function: Install package into repository
 *
 * @author <a href="mailto:jim.fuller@exist-db.org">James Fuller</a>
 * @author Wolfgang Meier
 * @author ljo
 */
public class InstallFunction extends BasicFunction {

    private final static Logger logger = LogManager.getLogger(InstallFunction.class);

    public final static FunctionSignature signatureInstall =
            new FunctionSignature(
                    new QName("install", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
                    "Install package from repository.",
                    new SequenceType[]{new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise"));

    public final static FunctionSignature signatureInstallFromDB =
            new FunctionSignature(
                    new QName("install-from-db", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
                    "Install package stored in database.",
                    new SequenceType[]{new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE, "database path to the package archive (.xar file)")},
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise"));

    public InstallFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        Sequence removed = BooleanValue.FALSE;
        boolean force = true;
        UserInteractionStrategy interact = new BatchUserInteraction();
        String pkgOrPath = args[0].getStringValue();
        Optional<ExistRepository> repo = getContext().getRepository();
        try {
            if (repo.isPresent()) {
                Repository parent_repo = repo.get().getParentRepo();
                Package pkg;
                if (isCalledAs("install")) {
                    // download .xar from a URI
                    URI uri = _getURI(pkgOrPath);
                    pkg = parent_repo.installPackage(uri, force, interact);
                    repo.get().reportAction(ExistRepository.Action.INSTALL, pkg.getName());
                } else {
                    // .xar is stored as a binary resource
				    try(final LockedDocument lockedDoc = getBinaryDoc(pkgOrPath);
                            final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
					    final DocumentImpl doc = lockedDoc.getDocument();
                        LOG.debug("Installing file: " + doc.getURI());
                        pkg = parent_repo.installPackage(new BinaryDocumentXarSource(context.getBroker().getBrokerPool(), transaction, (BinaryDocument)doc), force, interact);
					    repo.get().reportAction(ExistRepository.Action.INSTALL, pkg.getName());

                        transaction.commit();
				    }
                }
                ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
                if (info != null && !info.getJars().isEmpty())
                    ClasspathHelper.updateClasspath(context.getBroker().getBrokerPool(), pkg);
                // TODO: expath libs do not provide a way to see if there were any XQuery modules installed at all
                context.getBroker().getBrokerPool().getXQueryPool().clear();
                removed = BooleanValue.TRUE;
            } else {
                throw new XPathException("expath repository not available");
            }
        } catch (PackageException | TransactionException ex) {
            logger.error(ex.getMessage(), ex);
            return removed;
            // /TODO: _repo.removePackage seems to throw PackageException
            //throw new XPathException("Problem installing package " + pkg + " in expath repository, check that eXist-db has access permissions to expath repository file directory  ", ex);
        } catch (XPathException xpe) {
            logger.error(xpe.getMessage());
            return removed;
        }
        return removed;
    }

    private URI _getURI(String s) throws XPathException {
        URI uri;
        try {
            uri = new URI(s);
        } catch (URISyntaxException ex) {
            throw new XPathException(this, EXPathErrorCode.EXPDY001, s + " is not a valid URI: " + ex.getMessage(), new StringValue(s), ex);
        }
        if (uri.isAbsolute()) {
            return uri;
        } else {
            throw new XPathException(this, EXPathErrorCode.EXPDY001, s + " must be an absolute URI", new StringValue(s));
        }
    }

  private LockedDocument getBinaryDoc(final String path) throws XPathException {
      try {
          final XmldbURI uri = XmldbURI.createInternal(path);
          final LockedDocument lockedDoc = context.getBroker().getXMLResource(uri, LockMode.READ_LOCK);
          if (lockedDoc == null) {
            throw new XPathException(this, EXPathErrorCode.EXPDY001,
                path + " is not .xar resource",
                new StringValue(path)
            );
          } else if (lockedDoc.getDocument().getResourceType() != DocumentImpl.BINARY_FILE) {
            lockedDoc.close();
            throw new XPathException(this, EXPathErrorCode.EXPDY001,
                path + " is not a valid .xar, it's not a binary resource",
                new StringValue(path)
            );
          }
          return lockedDoc;
      } catch (PermissionDeniedException e) {
        throw new XPathException(this, EXPathErrorCode.EXPDY003, e.getMessage(), new StringValue(path), e);
      }
  }
}
