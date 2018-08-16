package org.exist.fluent;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.ExtArrayNodeSet;
import java.io.*;
import java.util.*;

import org.exist.EXistException;
import org.exist.collections.*;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


/**
 * <p>A named collection of XML documents in the database.  Each document belongs
 * to precisely one folder.  Folders can be nested, with queries carried out
 * within either the scope of a single folder or of a whole subtree.</p>
 * 
 * <p>Though the name <code>Collection</code> is commonly used for these
 * constructs in the context of XML databases, the name <code>Folder</code> was
 * chosen instead to avoid conflicting with the ubiquitous Java <code>java.util.Collection</code>
 * interface.</p>
 * 
 * <p>Instances of this class are not thread-safe.</p>
 *   
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.31 $ ($Date: 2006/09/04 06:09:05 $)
 */
public class Folder extends NamedResource implements Cloneable {
	
	/**
	 * Listener for events affecting folders.  The three possible actions are folder
	 * creation, renaming (update), and deletion.
	 * 
	 * <em>WARNING:</em>  as of September 1, 2005, eXist does not implement
	 * folder triggers so folder listeners are ineffective.  This warning will be removed
	 * when the situation is known to have been corrected.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public interface Listener extends org.exist.fluent.Listener {
		/**
		 * Respond to a folder event. 
		 *
		 * @param ev the details of the event
		 */
		void handle(Folder.Event ev);
	}
	
	/**
	 * An event that concerns a folder.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public static class Event extends org.exist.fluent.Listener.Event {
		/**
		 * The folder that's the subject of this event.
		 * Note that for some timing/action combinations, this field might be <code>null</code>.
		 */
		public final Folder folder;
		
		Event(Trigger trigger, String path, Folder folder) {
			super(trigger, path);
			this.folder = folder;
		}
		
		Event(ListenerManager.EventKey key, Folder folder) {
			super(key);
			this.folder = folder;
		}
		
		@Override
		public boolean equals(Object o) {
			return
				super.equals(o) &&
				(folder == null ? ((Event) o).folder == null : folder.equals(((Event) o).folder));
		}
		
		@Override
		public int hashCode() {
			return super.hashCode() * 37 + (folder == null ? 0 : folder.hashCode());
		}
		
		@Override
		public String toString() {
			StringBuilder buf = new StringBuilder(super.toString());
			buf.insert(3, "Folder.");
			buf.insert(buf.length()-1, ", " + folder);
			return buf.toString();
		}
	}
	
	/**
	 * The children (subfolders) facet of a collection.  Permits operations on subfolders,
	 * both immediate and indirect.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public class ChildrenFacet implements Iterable<Folder> {
		
		private ChildrenFacet() {}

		/**
		 * Return an existing descendant this of this folder, inheriting this one's namespace mappings.
		 * 
		 * @param descendantName the relative name of the child folder to get; must not start with '/'
		 * @return the descendant folder with the given name
		 */
		public Folder get(String descendantName) {
			staleMarker.check();
			if (descendantName.startsWith("/")) throw new IllegalArgumentException("descendant name starts with '/': " + descendantName);
			String parentPath = path();
			if (parentPath.equals("/")) parentPath = "";
			return new Folder(parentPath + "/" + descendantName, false, Folder.this);
		}

		/**
		 * Get a descendant folder of this folder, or create it if it doesn't exist.  It inherits
		 * this folder's namespace mappings.
		 * 
		 * @param descendantName the relative name of the descendant folder to create; must not start with '/'
		 * @return the child folder with the given name
		 */
		public Folder create(String descendantName) {
			staleMarker.check();
			if (descendantName.startsWith("/")) throw new IllegalArgumentException("descendant name starts with '/': " + descendantName);
			String parentPath = path();
			if (parentPath.equals("/")) parentPath = "";
			return new Folder(parentPath + "/" + descendantName, true, Folder.this);
		}

		/**
		 * Return the number of immediate child folders of this folder.
		 *
		 * @return the number of child subfolders
		 */
		public int size() {
                        DBBroker _broker = null;
                        try {
                            _broker = db.acquireBroker();
                            return getQuickHandle().getChildCollectionCount(_broker);
                        } catch(PermissionDeniedException pde) {
                            throw new DatabaseException(pde.getMessage(), pde);
			} finally {
                            db.releaseBroker(_broker);
			}
			
		}

		/**
		 * Return whether this folder has a descendant folder with the given name.
		 *
		 * @param descendantName the relative name of the descendant folder; must not start with '/'
		 * @return <code>true</code> if this folder has a descendant wich the given name, <code>false</code> otherwise
		 */
		public boolean contains(String descendantName) {
			staleMarker.check();
			if (descendantName.startsWith("/")) throw new IllegalArgumentException("child name starts with '/': " + descendantName);
			DBBroker _broker = null;
			try {
				_broker = db.acquireBroker();
				return _broker.getCollection(XmldbURI.create(path() + "/" + descendantName)) != null;
                        } catch(PermissionDeniedException pde) {
                            throw new DatabaseException(pde.getMessage(), pde);
			} finally {
				db.releaseBroker(_broker);
			}
		}
		
		/**
		 * Export these children folders to the given directory.
		 * If the directory does not exist it will be created.  A subdirectory will be created
		 * for each child folder.
		 *
		 * @param destination the destination folder to export into
		 * @throws IOException if the export failed due to an I/O error
		 */
		public void export(File destination) throws IOException {
			if (!destination.exists()) destination.mkdirs();
			if (!destination.isDirectory()) throw new IOException("export destination not a directory: " + destination);
			for (Folder child : this) {
				child.export(new File(destination, child.name()));
			}
		}

                public class FolderIterator implements Iterator<Folder> {
                    private Folder last;
                    
                    private Iterator<XmldbURI> delegate = null;
                    private DBBroker broker = null;
                    
                    private Iterator<XmldbURI> getDelegate() throws IllegalStateException {
                        if(delegate == null) {
                            try {
                                broker = db.acquireBroker();
                                delegate = getQuickHandle().collectionIterator(broker);
                            } catch(final PermissionDeniedException | LockException e) {
                                throw new IllegalStateException(e.getMessage(), e);
                            }
                        }
                        
                        return delegate;
                    }
                    
                    @Override
                    public void remove() {
                            staleMarker.check();
                            if (last == null) {
                                throw new IllegalStateException("no collection to remove");
                            }
                            last.delete();
                            last = null;
                    }
                    
                    @Override
                    public boolean hasNext() {
                            staleMarker.check();
                            boolean result = getDelegate().hasNext();
                            if(!result) {
                                db.releaseBroker(broker);
                            }
                            return result;
                    }
                    
                    @Override
                    public Folder next() {
                            staleMarker.check();
                            last = get(getDelegate().next().getCollectionPath());
                            return last;
                    }
                }
                
		/**
		 * Return an iterator over the immediate child subfolders.  You can use this iterator to
		 * selectively delete subfolders as well.
		 * 
		 * @return an iterator over the child subfolders 
		 */
                @Override
		public Iterator<Folder> iterator() {
                    staleMarker.check();
                    return new FolderIterator();
		}
		
	}
	
	/**
	 * The immediate documents facet of a folder.  Gives access to the (conceptual) set of
	 * documents contained directly in a folder.  All functions will <em>not</em> consider
	 * documents contained in subfolders.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public class DocumentsFacet extends Resource implements Iterable<Document> {
		
		private ListenersFacet listeners;

		/**
		 * The facet that gives control over listeners for documents contained directly within
		 * a folder.
		 *
		 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
		 */
		public class ListenersFacet {
			
			/**
			 * Add a listener for all documents directly in this folder.  Equivalent to <code>add(EnumSet.of(trigger), listener)</code>.
			 *
			 * @see #add(Set, Document.Listener)
			 * @param trigger the kind of event the listener should be notified of
			 * @param listener the listener to notify of events
			 */
			public void add(Trigger trigger, Document.Listener listener) {
				add(EnumSet.of(trigger), listener);
			}
			
			/**
			 * Add a listener for all documents directly in this folder.
			 * 
			 * @param triggers the kinds of events the listener should be notified of; the set must not be empty
			 * @param listener the listener to notify of events 
			 */
			public void add(Set<Trigger> triggers, Document.Listener listener) {
				staleMarker.check();
				ListenerManager.INSTANCE.add(path(), ListenerManager.Depth.ONE, triggers, listener, Folder.this);
			}
			
			/**
			 * Remove a listener previously added through this facet.  This will remove the listener from
			 * all combinations of timing and action for this folder, even if added via multiple invocations
			 * of the <code>add</code> methods.  However, it will not remove the listener from combinations
			 * added through other facets.
			 *
			 * @param listener the listener to remove
			 */
			public void remove(Document.Listener listener) {
				ListenerManager.INSTANCE.remove(path(), ListenerManager.Depth.ONE, listener);
			}
		}

		public class DocumentsFacetIterator implements Iterator<Document> {
			private Iterator<DocumentImpl> delegate;
			private Document last;

			public DocumentsFacetIterator() {
				acquire(LockMode.READ_LOCK);
				try {
					delegate = handle.iterator(broker);
				} catch(PermissionDeniedException | LockException e) {
					throw new DatabaseException(e.getMessage(), e);
				} finally {
					release();
				}
			}

			public void remove() {
				staleMarker.check();
				if (last == null) throw new IllegalStateException("no document to remove");
				last.delete();
				last = null;
			}
			public boolean hasNext() {
				staleMarker.check();
				return delegate.hasNext();
			}
			public Document next() {
				staleMarker.check();
				last = Document.newInstance(delegate.next(), Folder.this);
				return last;
			}
		}

		private DocumentsFacet() {
			super(Folder.this.namespaceBindings, Folder.this.db);
		}
		
		@Override Sequence convertToSequence() {
			staleMarker.check();
			return getDocsSequence(false);
		}
		
		/**
		 * Return the facet that allows control over listenersof the folder's immediate documents. 
		 *
		 * @return the immediate documents' listener facet
		 */
		public ListenersFacet listeners() {
			if (listeners == null) listeners = new ListenersFacet();
			return listeners;
		}
		
		/**
		 * Build a new XML document in this collection, with the given relative path.  Remember to {@link ElementBuilder#commit commit}
		 * commit the builder when done.  If the builder doesn't commit, no document is created.
		 * 
		 * @param name the relative path to the new document
		 * @return a builder to use to create the document
		 */
		public ElementBuilder<XMLDocument> build(final Name name) {
			Folder target = name.stripPathPrefix(Folder.this);
			if (target != Folder.this) return target.documents().build(name);
			
			staleMarker.check();
			return new ElementBuilder<XMLDocument>(Folder.this.namespaceBindings(), false, new ElementBuilder.CompletedCallback<XMLDocument>() {
				public XMLDocument completed(Node[] nodes) {
					assert nodes.length == 1;
					Node node = nodes[0];
					transact(LockMode.WRITE_LOCK);
					try {
						name.setContext(handle);
						IndexInfo info = handle.validateXMLResource(tx.tx, broker, XmldbURI.create(name.get()), node);
						changeLock(LockMode.NO_LOCK);
						handle.store(tx.tx, broker, info, node);
						commit();
					} catch (EXistException e) {
						throw new DatabaseException(e);
					} catch (PermissionDeniedException e) {
						throw new DatabaseException(e);
					} catch (TriggerException e) {
						throw new DatabaseException(e);
					} catch (SAXException e) {
						throw new DatabaseException(e);
					} catch (LockException e) {
						throw new DatabaseException(e);
					} catch (IOException e) {
						throw new DatabaseException(e);
					} finally {
						release();
					}
					return get(name.get()).xml();
				}
			});
		}

		/**
		 * Return whether this facet's folder contains a document with the given relative path.
		 *
		 * @param documentPath the relative path of the document to check for
		 * @return <code>true</code> if the folder contains a document with the given path, <code>false</code> otherwise
		 */
		public boolean contains(String documentPath) {
			checkIsRelativeDocPath(documentPath);
			if (documentPath.contains("/")) return database().contains(Folder.this.path() + "/" + documentPath);
                        
                        DBBroker _broker = null;
                        try {
                            _broker = db.acquireBroker();
                            return getQuickHandle().getDocument(_broker, XmldbURI.create(documentPath)) != null;
                        } catch(PermissionDeniedException pde) {
                            throw new DatabaseException(pde.getMessage(), pde);
                        } finally {
                            if(_broker != null) {
                                db.releaseBroker(_broker);
                            }
                        }
			
		}

		/**
		 * Create an XML document with the given relative path, takings its contents from the given source.
		 *
		 * @param name the desired relative path of the document
		 * @param source the source of XML data to read in the document contents from; the folder's namespace bindings are <em>not</em> applied
		 * @return the newly created document
		 * @throws DatabaseException if anything else goes wrong
		 */
		public XMLDocument load(Name name, Source.XML source) {
			Folder target = name.stripPathPrefix(Folder.this);
			if (target != Folder.this) return target.documents().load(name, source);
			
			transact(LockMode.WRITE_LOCK);
			try {
				source.applyOldName(name);
				name.setContext(handle);
				IndexInfo info = handle.validateXMLResource(tx.tx, broker, XmldbURI.create(name.get()), source.toInputSource());
				changeLock(LockMode.NO_LOCK);
				handle.store(tx.tx, broker, info, source.toInputSource());
				commit();
			} catch (EXistException e) {
				throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
			} catch (PermissionDeniedException e) {
				throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
			} catch (TriggerException e) {
				throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
			} catch (SAXException e) {
				throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
			} catch (LockException e) {
				throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
			} catch (IOException e) {
				throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
			} finally {
				release();
			}
			return get(name.get()).xml();
		}
		
		/**
		 * Create a binary document with the given relative path, takings its contents from the given source.
		 *
		 * @param name the desired relative path of the document
		 * @param source the source to read the document contents from
		 * @return the newly created document
		 * @throws DatabaseException if anything else goes wrong
		 */
		public Document load(Name name, Source.Blob source) {
			Folder target = name.stripPathPrefix(Folder.this);
			if (target != Folder.this) return target.documents().load(name, source);
			
			transact(LockMode.WRITE_LOCK);
			try {
				source.applyOldName(name);
				name.setContext(handle);
				InputStream inputStream = source.toInputStream();
				try {
					handle.addBinaryResource(tx.tx, broker, XmldbURI.create(name.get()), inputStream, null, source.getLength());
				} catch (PermissionDeniedException e) {
					throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
				} catch (LockException e) {
					throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
				} catch (TriggerException e) {
					throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
				} catch (EXistException e) {
                                        throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
                                } finally {
					inputStream.close();
				}
				commit();
			} catch (IOException e) {
				throw new DatabaseException("failed to create document '" + name + "' from source " + source, e);
			} finally {
				release();
			}
			return get(name.get());
		}
		
		/**
		 * Get the contained document with the given relative path. 
		 *
		 * @param documentPath the relative path of the document to find
		 * @return the document with the given path
		 * @throws DatabaseException if unable to find or access the desired document
		 */
		public Document get(String documentPath) {
			checkIsRelativeDocPath(documentPath);
			if (documentPath.contains("/")) return database().getDocument(Folder.this.path() + "/" + documentPath);
			
                        DBBroker _broker = null; 
                        try {
                            _broker = db.acquireBroker();
                            DocumentImpl dimpl = getQuickHandle().getDocument(_broker, XmldbURI.create(documentPath));
                            if (dimpl == null) throw new DatabaseException("no such document: " + documentPath);
                            return Document.newInstance(dimpl, Folder.this);
                        } catch(PermissionDeniedException pde) {
                            throw new DatabaseException(pde.getMessage(), pde);
                        } finally {
                            if(_broker != null) {
                                db.releaseBroker(_broker);
                            }
                        }
		}
		
		private void checkIsRelativeDocPath(String docPath) {
			if (docPath.startsWith("/")) throw new IllegalArgumentException("relative path cannot start with '/': " + docPath);
			if (docPath.endsWith("/")) throw new IllegalArgumentException("relative path cannot end with '/': " + docPath);
		}

		/**
		 * Return the number of documents immediately contained in the folder.
		 *
		 * @return the number of child documents
		 */
		public int size() {
                    DBBroker _broker = null;
                    try {
                        _broker = db.acquireBroker();
			return getQuickHandle().getDocumentCount(_broker);
                    } catch (PermissionDeniedException pde) {
                        throw new DatabaseException(pde.getMessage(), pde);
                    } finally {
                        db.releaseBroker(_broker);
                    }
		}
		
		/**
		 * Export the immediately contained documents to the given directory.
		 * If the directory does not exist it will be created.  Documents in this
		 * folder will appear directly in the given directory.
		 *
		 * @param destination the destination folder to export into
		 * @throws IOException if the export failed due to an I/O error
		 */
		public void export(File destination) throws IOException {
			if (!destination.exists()) destination.mkdirs();
			if (!destination.isDirectory()) throw new IOException("export destination not a directory: " + destination);
			for (Document doc : this) {
				doc.export(new File(destination, doc.name()));
			}
		}

		/**
		 * Query over the documents immediately contained in the folder, ignoring any documents
		 * in subfolders.
		 * 
		 * @return a query service over the folder's immediate documents
		 */
		@Override public QueryService query() {
			staleMarker.check();
			return super.query();
		}
		
		@Override QueryService createQueryService() {
			return new QueryService(Folder.this) {
				@Override
				protected void prepareContext(DBBroker broker_) {
					acquire(LockMode.READ_LOCK, broker_);
					try {
						docs = handle.allDocs(broker_, new DefaultDocumentSet(), false);
						baseUri = new AnyURIValue(handle.getURI());
                                        }catch (PermissionDeniedException pde) {
                                            throw new DatabaseException(pde.getMessage(), pde);
					} finally {
						release();
					}
				}
			};
		}
		
		/**
		 * Return an iterator over the folder's immediate documents.  This iterator can be used
		 * to selectively delete documents as well.
		 * 
		 * @return an iterator over the folder's immediate documents
		 */
		@Override
		public Iterator<Document> iterator() {
			return new DocumentsFacetIterator();
		}
	}
	
	/**
	 * The facet that allwos control over listeners to the folder, and all its descendant folders and
	 * documents.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public class ListenersFacet {
		
		/**
		 * Add a listener for either folder or document events on this folder, its contents, and all
		 * its descendants and their contents as well.  Equivalent to <code>add(EnumSet.of(trigger), listener)</code>.
		 *
		 * @see #add(Set, org.exist.fluent.Listener)
		 * @param trigger the kind of event the listener should be notified of
		 * @param listener the listener to notify of events
		 */
		public void add(Trigger trigger, org.exist.fluent.Listener listener) {
			add(EnumSet.of(trigger), listener);
		}
		
		/**
		 * Add a listener for either folder or document events on this folder, its contents, and all
		 * its descendants and their contents as well.  The listener's type (either {@link Document.Listener}
		 * or {@link Folder.Listener}) will determine the kinds of events it will receive.  Note that
		 * if the listener implements both interfaces it will be notified of events that concern both
		 * documents and folders (and satisfy the timing and action characteristics requested).
		 * 
		 * @param triggers the kinds of events the listener should be notified of; the set must not be empty
		 * @param listener the listener to notify of events 
		 */
		public void add(Set<Trigger> triggers, org.exist.fluent.Listener listener) {
			staleMarker.check();
			ListenerManager.INSTANCE.add(path(), ListenerManager.Depth.MANY, triggers, listener, Folder.this);
		}
		
		/**
		 * Remove a listener previously added through this facet.  This will remove the listener from
		 * all combinations of timing and action for this folder, even if added via multiple invocations
		 * of the <code>add</code> methods.  However, it will not remove the listener from combinations
		 * added through other facets, even this folder's documents facet or the facets of any descendants.
		 *
		 * @param listener the listener to remove
		 */
		public void remove(org.exist.fluent.Listener listener) {
			ListenerManager.INSTANCE.remove(path(), ListenerManager.Depth.MANY, listener);
		}
	}
	

	
	private String path;
	private StaleMarker staleMarker;
	private ChildrenFacet children;
	private DocumentsFacet documents;
	private ListenersFacet listeners;
	private MetadataFacet metadata;
	
	// the following are only valid while we're holding a broker
	private DBBroker broker;
	private Collection handle;
	private Transaction tx;
	private LockMode lockMode;
	private boolean ownBroker;
	
	/**
	 * Create a wrapper around the given collection.
	 * 
	 * @param path the absolute path to the desired collection, must start with '/'
	 * @param createIfMissing what to do if the given collection doesn't exist; if <code>true</code>, create it, otherwise signal an error
	 * @param origin the origin, indicating the database instance and namespace bindings to be extended
	 * @throws DatabaseException if the collection cannot be found and is not supposed to be created
	 */
	Folder(String path, boolean createIfMissing, Resource origin) {
		this(path, createIfMissing, origin.namespaceBindings().extend(), origin.database());
	}
	
	Folder(String path, boolean createIfMissing, NamespaceMap namespaceBindings, Database db) {
		super(namespaceBindings, db);
		if (path.length() == 0 || path.charAt(0) != '/') throw new IllegalArgumentException("path must start with /, got " + path);
		
		try {
			broker = db.acquireBroker();
			Collection collection;
			if (createIfMissing) {
				tx = Database.requireTransaction();
				try {
					collection = createInternal(path);
					tx.commit();
				} finally {
					tx.abortIfIncomplete();
				}
			} else {
				try {
                                    collection = broker.getCollection(XmldbURI.create(path));
                                
                                    if (collection == null) {
                                        throw new DatabaseException("collection not found '" + path + "'");
                                    }
                                } catch(PermissionDeniedException pde) {
                                    throw new DatabaseException(pde.getMessage(), pde);
                                }
			}
			// store the normalized path, minus the root prefix if possible
			changePath(collection.getURI().getCollectionPath());
		} finally {
			db.releaseBroker(broker);
			broker = null;
			tx = null;
		}
	}
	
	private void changePath(String newPath) {
		this.path = Database.normalizePath(newPath);
		staleMarker = new StaleMarker();
		staleMarker.track(path);
	}
	
	/**
	 * Return this folder's listeners facet, giving control over listeners to events on this folder,
	 * its contents, and all its descendants.
	 *
	 * @return this folder's listeners facet
	 */
	public ListenersFacet listeners() {
		if (listeners == null) listeners = new ListenersFacet();
		return listeners;
	}

	@Override public MetadataFacet metadata() {
		if (metadata == null) metadata = new MetadataFacet(getQuickHandle().getPermissionsNoLock(), db) {
			@Override public Date creationDate() {
				return new Date(getQuickHandle().getCreationTime());
			}
		};
		return metadata;
	}

	/**
	 * Create a duplicate handle that copies the original's path and namespace bindings.
	 * No copy is created of the underlying folder.  The namespace bindings will copy the
	 * original's immediate namespace map and namespace bindings inheritance chain.
	 * 
	 * @return a duplicate of this collection wrapper
	 */
	@Override
	public Folder clone() {
		staleMarker.check();
		return new Folder(path(), false, namespaceBindings.clone(), db);
	}
	
	public Folder cloneWithoutNamespaceBindings() {
		staleMarker.check();
		return new Folder(path(), false, new NamespaceMap(), db);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Folder) return path().equals(((Folder) o).path());
		return false;
	}
	
	@Override
	public int hashCode() {
		return path().hashCode();
	}
	
	@Override
	public String toString() {
		return "folder '" + path() + "'";
	}
	
	private Collection createInternal(String targetPath) {
		try {
			Collection collection = broker.getOrCreateCollection(tx.tx, XmldbURI.create(targetPath));
			broker.saveCollection(tx.tx, collection);
			broker.flush();
			return collection;
		} catch (PermissionDeniedException e) {
			throw new DatabaseException(e);
		} catch (IOException e) {
			throw new DatabaseException(e);
		} catch (TriggerException e) {
			throw new DatabaseException(e);
		}
	}
	
	void transact(LockMode _lockMode) {
		if (tx != null) throw new IllegalStateException("transaction already in progress");
		tx = Database.requireTransaction();
		acquire(_lockMode);
	}
	
	void commit() {
		if (tx == null) throw new IllegalStateException("no transaction in progress");
		tx.commit();		// later aborts will do nothing
	}
	
	void acquire(LockMode _lockMode) {
		DBBroker _broker = db.acquireBroker();
		ownBroker = true;
		try {
			acquire(_lockMode, _broker);
		} catch (RuntimeException e) {
			db.releaseBroker(_broker);
			ownBroker = false;
			throw e;
		}
	}
	
	void acquire(LockMode _lockMode, DBBroker _broker) {
		staleMarker.check();
		if(broker != null || handle != null) throw new IllegalStateException("broker already acquired");
		broker = _broker;
		try {
                    handle = broker.openCollection(XmldbURI.create(path()), _lockMode);
                    if(handle == null) {
                        throw new DatabaseException("collection not found '" + path + "'");
                    }
                    this.lockMode = _lockMode;
		} catch (RuntimeException e) {
                    broker = null;
                    handle = null;
                    throw e;
		} catch(PermissionDeniedException pde) {
                    broker = null;
                    handle = null;
                    throw new DatabaseException(pde.getMessage(), pde);
                }
	}
	
	void release() {
		if (broker == null || handle == null) throw new IllegalStateException("broker not acquired");
		if (tx != null) tx.abortIfIncomplete();
		if (lockMode != LockMode.NO_LOCK) handle.getLock().release(lockMode);
		if (ownBroker) db.releaseBroker(broker);
		ownBroker = false;
		broker = null;
		handle = null;
		tx = null;
	}
	
	void changeLock(LockMode newLockMode) {
		if (broker == null || handle == null) throw new IllegalStateException("broker not acquired");
		if (lockMode == newLockMode) return;
		if (lockMode == LockMode.NO_LOCK) {
			try {
				handle.getLock().acquire(newLockMode);
				lockMode = newLockMode;
			} catch (LockException e) {
				throw new DatabaseException(e);
			}
		} else {
			if (newLockMode != LockMode.NO_LOCK) throw new IllegalStateException("cannot change between read and write lock modes");
			handle.getLock().release(lockMode);
			lockMode = newLockMode;
		}
	}
	
	private Collection getQuickHandle() {
		acquire(LockMode.NO_LOCK);
		try {
			return handle;
		} finally {
			release();
		}
	}
	
	/**
	 * Return whether this folder is empty, i.e. has no documents or subfolders in it.
	 *
	 * @return whether this folder is empty
	 */
	public boolean isEmpty() {
		acquire(LockMode.NO_LOCK);
		try {
			return handle.getDocumentCount(broker) == 0 && handle.getChildCollectionCount(broker) == 0;
                } catch (PermissionDeniedException pde) {
                    throw new DatabaseException(pde.getMessage(), pde);
                } finally {
			release();
		}
                
	}
	
	/**
	 * Remove all resources and subfolders from this folder, but keep the folder itself.
	 */
	public void clear() {
		transact(LockMode.WRITE_LOCK);
		try {
			if (handle.getDocumentCount(broker) == 0 && handle.getChildCollectionCount(broker) == 0) return;
			broker.removeCollection(tx.tx, handle);
			createInternal(path);
			commit();
		} catch (PermissionDeniedException e) {
			throw new DatabaseException(e);
		} catch (IOException e) {
			throw new DatabaseException(e);
		} catch (TriggerException e) {
			throw new DatabaseException(e);
		} finally {
			release();
		}
		changePath(path);	// reset stale marker
	}
	
	/**
	 * Delete this folder, including all documents and descendants.  If invoked on the root folder,
	 * deletes all documents and descendants but does not delete the root folder itself.
	 */
	@Override public void delete() {
		transact(LockMode.NO_LOCK);
		try {
			// TODO: temporary hack, remove once removing root collection works in eXist
			if (path.equals("/")) {
				for (Iterator<DocumentImpl> it = handle.iterator(broker); it.hasNext();) {
					DocumentImpl dimpl = it.next();
					if (dimpl instanceof BinaryDocument) {
						handle.removeBinaryResource(tx.tx, broker, dimpl);
					} else {
						handle.removeXMLResource(tx.tx, broker, dimpl.getFileURI());
					}
				}
			}
			// end hack
			broker.removeCollection(tx.tx, handle);
			commit();
		} catch (PermissionDeniedException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new DatabaseException(e);
		} catch (LockException e) {
			throw new DatabaseException(e);
		} catch (TriggerException e) {
			throw new DatabaseException(e);
		} finally {
			release();
		}
	}
	
	/**
	 * Move this folder to another spot in the folder hierarchy, possibly changing its name in the process.
	 * This folder will refer to the newly relocated folder when this method returns.  You can use this
	 * method to move a folder without changing its name (<code>folder.move(destFolder, Name.keepCreate())</code>)
	 * or to rename it without changing its location (<code>folder.move(folder.parent(), Name.create(newName))</code>).
	 *
	 * @param destination the new parent folder of this folder
	 * @param name the new name for this folder
	 */
	@Override public void move(Folder destination, Name name) {
		changePath(moveOrCopyThisFolder(destination, name, false));
	}
	
	/**
	 * Copy this folder to another place in the folder hierarchy, returning the newly copied folder
	 * with namespace bindings inherited from this one.
	 *
	 * @param destination the desired parent folder of the copy
	 * @param name the desired name of the copy
	 * @return a reference to the copied folder
	 */
	@Override public Folder copy(Folder destination, Name name) {
		return new Folder(moveOrCopyThisFolder(destination, name, true), false, this);
	}
	
	private String moveOrCopyThisFolder(Folder destination, Name name, boolean copy) {
		db.checkSame(destination);
		transact(LockMode.WRITE_LOCK);
		try {
			destination.acquire(LockMode.WRITE_LOCK, broker);
			try {
				name.setOldName(name());
				name.setContext(handle);
				if (copy) {
					broker.copyCollection(tx.tx, handle, destination.handle, XmldbURI.create(name.get()));
				} else {
					broker.moveCollection(tx.tx, handle, destination.handle, XmldbURI.create(name.get()));
				}
				commit();
			} catch (PermissionDeniedException e) {
				throw new DatabaseException(e);
			} catch (LockException e) {
				throw new DatabaseException(e);
			} catch (IOException e) {
				throw new DatabaseException(e);
			} catch (TriggerException e) {
				throw new DatabaseException(e);
			} catch (EXistException e) {
				throw new DatabaseException(e);
			} finally {
				destination.release();
			}
		} finally {
			release();
		}
		return destination.path() + "/" + name.get();
	}
	
	/**
	 * Provide access to the documents facet of this folder.  The documents facet is
	 * the conceptual set of documents contained directly in this collection (and therefore
	 * excludes documents contained in any subfolders).
	 * 
	 * @return the documents facet
	 */
	public DocumentsFacet documents() {
		if (documents == null) documents = new DocumentsFacet();
		return documents;
	}
	
	/**
	 * Return whether this folder or one of its descendants contains the given named resource.
	 * 
	 * @param res the resource to check for
	 * @return <code>true</code> if the resource is contained (directly or indirectly) in this folder, <code>false</code> otherwise
	 */
	public boolean contains(NamedResource res) {
		staleMarker.check();
		db.checkSame(res);
		return res.path().startsWith(path() + (path().equals("/") ? "" : "/"));
	}
	
	/**
	 * Return the given path stripped of this folder's prefix path, i.e. relative to this folder.
	 * The given path can be either to a document or to a subfolder.  If the given path is to
	 * this folder itself, return the empty string.
	 *
	 * @param subPath the path to relativize
	 * @return the given path relative to this folder
	 */
	public String relativePath(String subPath) {
		if (subPath.equals(path())) return "";
		if (path().equals("/") && subPath.charAt(0) == '/') return subPath.substring(1);
		if (!subPath.startsWith(path()+"/")) throw new IllegalArgumentException("path '" + subPath + "' does not fall under this collection's path '" + path() + "'");
		return subPath.substring(path().length() + 1);
	}
	
	/**
	 * Return the full path to the folder; always starts with '/'.
	 * 
	 * @return the full path to the folder
	 */
	@Override public String path() {
		return path;
	}
	
	/**
	 * Return the local name of the folder; this is the last segment of its path.
	 * 
	 * @return the local name of the folder
	 */
	@Override public String name() {
		return path().substring(path().lastIndexOf('/')+1);
	}
	
	/**
	 * Return the parent folder of this folder.  It will inherit this folder's namespace bindings.
	 * 
	 * @return the parent folder that this folder is a child of
	 * @throws DatabaseException if this is the root folder
	 */
	public Folder parent() {
		String parentPath = getQuickHandle().getURI().removeLastSegment().getCollectionPath();
		if (parentPath == null || parentPath.length() == 0) throw new DatabaseException("this is the root collection");
		return new Folder(parentPath, false, this);
	}
	
	/**
	 * Return the children facet of this folder that gives access to operations on its subfolders
	 * and descendants.
	 *
	 * @return the children facet of this folder
	 */
	public ChildrenFacet children() {
		if (children == null) children = new ChildrenFacet();
		return children;
	}
	
	/**
	 * Export the contents of this folder (both documents and subfolders) to the given
	 * directory.  If the directory does not exist it will be created.  Documents in this
	 * folder will appear directly in the given directory, i.e. a subdirectory matching this
	 * folder will <em>not</em> be created.
	 *
	 * @param destination the destination folder to export into
	 * @throws IOException if the export failed due to an I/O error
	 */
	public void export(File destination) throws IOException {
		documents().export(destination);
		children().export(destination);
	}
	
	@Override Sequence convertToSequence() {
		staleMarker.check();
		return getDocsSequence(true);
	}
	
	private Sequence getDocsSequence(boolean recursive) {
		try {
			DocumentSet docs;
			acquire(LockMode.READ_LOCK);
			try {
				docs = handle.allDocs(broker, new DefaultDocumentSet(), recursive);
                        } catch(PermissionDeniedException pde) {
                            throw new DatabaseException(pde.getMessage(), pde);
                        } finally {
				release();
			}
			Sequence result = new ExtArrayNodeSet(docs.getDocumentCount(), 1);
			for (Iterator<?> i = docs.getDocumentIterator(); i.hasNext();) {
				result.add(new NodeProxy((DocumentImpl) i.next()));
			}
			return result;
		} catch (XPathException e) {
			throw new RuntimeException("unexpected exception converting document set to sequence", e);
		}
	}
	
	/**
	 * Return a query service for executing queries over the contents of this folder and the contents
	 * of all its descendants.  If you only want to query the documents contained directly in this folder,
	 * see {@link #documents() the documents facet}.
	 * 
	 * @return a query service over this folder's contents and all its descendants' contents 
	 */
	@Override public QueryService query() {
		staleMarker.check();
		return super.query();
	}
	
	@Override QueryService createQueryService() {
		return new QueryService(this) {
			@Override void prepareContext(DBBroker broker_) {
				acquire(LockMode.READ_LOCK, broker_);
				try {
					docs = handle.allDocs(broker_, new DefaultDocumentSet(), true);
					baseUri = new AnyURIValue(handle.getURI());
                                } catch(PermissionDeniedException pde) {
                                    throw new DatabaseException(pde.getMessage(), pde);
				} finally {
					release();
				}
			}
		};
	}
	
	void removeDocument(DocumentImpl dimpl) {
		transact(LockMode.WRITE_LOCK);
		try {
			if (dimpl instanceof BinaryDocument) {
				handle.removeBinaryResource(tx.tx, broker, dimpl.getFileURI());
			} else {
				handle.removeXMLResource(tx.tx, broker, dimpl.getFileURI());
			}
			commit();
		} catch (final PermissionDeniedException | IOException | LockException | TriggerException e) {
			throw new DatabaseException(e);
		} finally {
			release();
		}
	}
	
	DocumentImpl moveOrCopyDocument(DocumentImpl doc, Name name, boolean copy) {
		XmldbURI uri;
		transact(LockMode.WRITE_LOCK);
		try {
			name.setContext(handle);
			uri = XmldbURI.create(name.get());
			if (copy) {
				tx.lockRead(doc);
				broker.copyResource(tx.tx, doc, handle, uri);
			} else {
				tx.lockWrite(doc);
				broker.moveResource(tx.tx, doc, handle, uri);
			}
			commit();
		} catch (PermissionDeniedException pde) {
			throw new DatabaseException(pde.getMessage(), pde);
		} catch (LockException e) {
			throw new DatabaseException("lock denied", e);
		} catch (IOException e) {
			throw new DatabaseException(e);
		} catch (TriggerException e) {
			throw new DatabaseException(e);
		} catch (EXistException e) {
			throw new DatabaseException(e);
		} finally {
			release();
		}
                
                DBBroker _broker = null;
                Collection _handle = getQuickHandle();
                try {
                     _broker = db.acquireBroker();
                    return _handle.getDocument(_broker, uri);
                } catch(PermissionDeniedException pde) {
                    throw new DatabaseException(pde.getMessage(), pde);
                } finally {
                    if(_broker != null) {
                        db.releaseBroker(_broker);
                    }
                }
                        
                    
	}
}