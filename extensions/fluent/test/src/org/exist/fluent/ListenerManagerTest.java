package org.exist.fluent;

import org.jmock.Mock;
import org.jmock.core.Constraint;
import org.jmock.core.Invocation;
import org.jmock.core.Stub;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:53:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class ListenerManagerTest extends DatabaseHelper {
    private Mock documentListener;
    private Mock folderListener;
    private Mock bothListener;
    private Document.Listener documentListenerProxy;
    private Folder.Listener folderListenerProxy;
    private BothListener bothListenerProxy;

    @Override
		protected void setUp() {
        super.setUp();

        documentListener = mock(Document.Listener.class);
        folderListener = mock(Folder.Listener.class);
        bothListener = mock(BothListener.class);

        documentListenerProxy = (Document.Listener) documentListener.proxy();
        folderListenerProxy = (Folder.Listener) folderListener.proxy();
        bothListenerProxy = (BothListener) bothListener.proxy();
    }

    @Override
		protected void tearDown() throws Exception {
        super.tearDown();
        ListenerManager.INSTANCE.remove(documentListenerProxy);
        ListenerManager.INSTANCE.remove(folderListenerProxy);
        ListenerManager.INSTANCE.remove(bothListenerProxy);
        documentListener = null;			folderListener = null;			bothListener = null;
        documentListenerProxy = null;	folderListenerProxy = null;	bothListenerProxy = null;
    }

    private Constraint eqDelayedDoc(final Document.Event ev) {
        return new Constraint() {
            public StringBuffer describeTo(StringBuffer buf) {
                return buf.append("eqDelayedDoc(").append(ev).append(")");
            }
            public boolean eval(Object o) {
                return new Document.Event(ev.trigger, ev.path, db.getDocument(ev.path)).equals(o);
            }
        };
    }

    private Stub checkDocumentExists(final String path, final boolean shouldExist) {
        return new Stub() {
            public StringBuffer describeTo(StringBuffer buf) {
                return buf.append("check that document '" + path + "' " + (shouldExist ? "exists" : "does not exist"));
            }
            public Object invoke(Invocation inv) throws Throwable {
                try {
                    db.getDocument(path);
                    if (!shouldExist) fail("document '" + path + "' exists but shouldn't");
                } catch (DatabaseException e) {
                    if (shouldExist) fail("document '" + path + "' doesn't exist but should");
                }
                return null;
            }
        };
    }

    private Stub checkFolderExists(final String path, final boolean shouldExist) {
        return new Stub() {
            public StringBuffer describeTo(StringBuffer buf) {
                return buf.append("check that folder '" + path + "' " + (shouldExist ? "exists" : "does not exist"));
            }
            public Object invoke(Invocation inv) throws Throwable {
                try {
                    db.getFolder(path);
                    if (!shouldExist) fail("folder '" + path + "' exists but shouldn't");
                } catch (DatabaseException e) {
                    if (shouldExist) fail("folder '" + path + "' doesn't exist but should");
                }
                return null;
            }
        };
    }

    private Stub checkDocumentStamp(final String expectedStamp) {
        return new Stub() {
            public StringBuffer describeTo(StringBuffer buf) {
                buf.append("check that event document is stamped with '" + expectedStamp + "'");
                return buf;
            }
            public Object invoke(Invocation inv) throws Throwable {
                XMLDocument doc = ((Document.Event) inv.parameterValues.get(0)).document.xml();
                assertNotNull("event document is null", doc);
                assertEquals(expectedStamp, doc.query().single("/test/@stamp").value());
                return null;
            }
        };
    }

    private XMLDocument createDocument(String path) {
        return createDocument(path, null);
    }

    private XMLDocument createDocument(String path, String stamp) {
        int k = path.lastIndexOf('/');
        assert k > 0;
        Folder folder = db.createFolder(path.substring(0, k));
        return folder.documents().build(Name.overwrite(path.substring(k+1)))
            .elem("test").attrIf(stamp != null, "stamp", stamp).end("test").commit();
    }

    public void testListenDocumentsBeforeCreateDocument1() {
        final String docPath = "/top/test.xml";
        Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
        top.documents().listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
        createDocument(docPath);
        createDocument("/elsewhere/test.xml");
        createDocument("/top/deeper/test.xml");
    }

    public void testListenDocumentsBeforeCreateDocument2() {
        final String docPath = "/top/test.xml";
        Folder top = db.createFolder("/top");
        documentListener.expects(never()).method("handle").with(ANYTHING);
        top.documents().listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
        top.documents().listeners().remove(documentListenerProxy);
        createDocument(docPath);
    }

    public void testListenDocumentsAfterCreateDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.AFTER_CREATE, docPath, doc);
        doc.delete();
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eqDelayedDoc(ev)).will(checkDocumentExists(docPath, true));
        top.documents().listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
        createDocument(docPath);
        createDocument("/elsewhere/test.xml");
        createDocument("/top/deeper/test.xml");
    }

    public void testListenDocumentsAfterCreateDocument2() {
        final String docPath = "/top/test.xml";
        Folder top = db.createFolder("/top");
        documentListener.expects(never()).method("handle").with(ANYTHING);
        top.documents().listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
        top.documents().listeners().remove(documentListenerProxy);
        createDocument(docPath);
    }

    public void testListenFolderBeforeCreateDocument1() {
        final String docPath = "/top/test2.xml";
        Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
        top.listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
        createDocument(docPath);
        createDocument("/elsewhere/test.xml");
    }

    public void testListenFolderBeforeCreateDocument2() {
        final String docPath = "/top/test2.xml";
        Folder top = db.createFolder("/top");
        documentListener.expects(never()).method("handle").with(ANYTHING);
        top.listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
        top.listeners().remove(documentListenerProxy);
        createDocument(docPath);
    }

    public void testListenFolderAfterCreateDocument1() {
        final String docPath = "/top/test2.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.AFTER_CREATE, docPath, doc);
        doc.delete();
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eqDelayedDoc(ev)).will(checkDocumentExists(docPath, true));
        top.listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
        createDocument(docPath);
        createDocument("/elsewhere/test.xml");
    }

    public void testListenFolderAfterCreateDocument2() {
        final String docPath = "/top/test2.xml";
        Folder top = db.createFolder("/top");
        documentListener.expects(never()).method("handle").with(ANYTHING);
        top.listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
        top.listeners().remove(documentListenerProxy);
        createDocument(docPath);
    }

    public void testListenDocumentsBeforeUpdateDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath, "before");
        Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("before"));
        top.documents().listeners().add(Trigger.BEFORE_UPDATE, documentListenerProxy);
        createDocument(docPath, "after");
        createDocument("/elsewhere/test.xml");
        createDocument("/top/deeper/test.xml");
    }

    public void testListenDocumentsAfterUpdateDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath, "before");
        Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("after"));
        top.documents().listeners().add(Trigger.AFTER_UPDATE, documentListenerProxy);
        createDocument(docPath, "after");
        createDocument("/elsewhere/test.xml");
        createDocument("/top/deeper/test.xml");
    }

    public void testListenFolderBeforeUpdateDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath, "before");
        Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("before"));
        top.listeners().add(Trigger.BEFORE_UPDATE, documentListenerProxy);
        createDocument(docPath, "after");
        createDocument("/elsewhere/test.xml");
    }

    public void testListenFolderAfterUpdateDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath, "before");
        Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("after"));
        top.listeners().add(Trigger.AFTER_UPDATE, documentListenerProxy);
        createDocument(docPath, "after");
        createDocument("/elsewhere/test.xml");
    }

    public void testListenFolderDeepBeforeCreateDocument1() {
        final String docPath = "/top/middle/test2.xml";
        Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
        Folder top = db.createFolder("/top");
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
        top.listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
        createDocument(docPath);
        createDocument("/elsewhere/test.xml");
    }

    public void testListenDocumentBeforeUpdateDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath, "before");
        Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("before"));
        doc.listeners().add(Trigger.BEFORE_UPDATE, documentListenerProxy);
        createDocument(docPath, "after");
        createDocument("/elsewhere/test.xml");
        createDocument("/top/test2.xml");
    }

    public void testListenDocumentAfterUpdateDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath, "before");
        Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("after"));
        doc.listeners().add(Trigger.AFTER_UPDATE, documentListenerProxy);
        createDocument(docPath, "after");
        createDocument("/elsewhere/test.xml");
        createDocument("/top/test2.xml");
    }

    public void testListenDocumentsBeforeDeleteDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
        Folder top = db.createFolder("/top");
        top.documents().listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
        doc.delete();
        createDocument("/elsewhere/test.xml").delete();
        createDocument("/top/deeper/test.xml").delete();
    }

    public void testListenDocumentsBeforeDeleteDocument2() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath, "before");
        doc.delete();
        Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
        documentListener.expects(once()).method("handle").with(eqDelayedDoc(ev)).will(checkDocumentExists(docPath, true));
        Folder top = db.createFolder("/top");
        top.documents().listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
        createDocument(docPath).delete();
        createDocument("/elsewhere/test.xml").delete();
        createDocument("/top/deeper/test.xml").delete();
    }

    public void testListenDocumentsAfterDeleteDocument1() {
        final String docPath = "/top/test.xml";
        Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
        Folder top = db.createFolder("/top");
        top.documents().listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
        createDocument(docPath).delete();
        createDocument("/elsewhere/test.xml").delete();
        createDocument("/top/deeper/test.xml").delete();
    }

    public void testListenFolderBeforeDeleteDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
        Folder top = db.createFolder("/top");
        top.listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
        doc.delete();
        createDocument("/elsewhere/test.xml").delete();
    }

    public void testListenFolderBeforeDeleteDocument2() {
        final String docPath = "/top/deeper/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
        Folder top = db.createFolder("/top");
        top.listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
        doc.delete();
        createDocument("/elsewhere/test.xml").delete();
    }

    public void testListenFolderAfterDeleteDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
        Folder top = db.createFolder("/top");
        top.listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
        doc.delete();
        createDocument("/elsewhere/test.xml").delete();
    }

    public void testListenFolderAfterDeleteDocument2() {
        final String docPath = "/top/deeper/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
        Folder top = db.createFolder("/top");
        top.listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
        doc.delete();
        createDocument("/elsewhere/test.xml").delete();
    }

    public void testListenDocumentBeforeDeleteDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
        doc.listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
        doc.delete();
        createDocument("/elsewhere/test.xml").delete();
        createDocument("/top/deeper/test.xml").delete();
        createDocument("/top/test2.xml").delete();
    }

    public void testListenDocumentAfterDeleteDocument1() {
        final String docPath = "/top/test.xml";
        XMLDocument doc = createDocument(docPath);
        Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
        documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
        doc.listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
        doc.delete();
        createDocument("/elsewhere/test.xml").delete();
        createDocument("/top/deeper/test.xml").delete();
        createDocument("/top/test2.xml").delete();
    }

    public void bugtestListenBeforeCreateFolder1() {
        final String folderPath = "/top/child";
        Folder.Event ev = new Folder.Event(Trigger.BEFORE_CREATE, folderPath, null);
        folderListener.expects(once()).method("handle").with(eq(ev)).will(checkFolderExists(folderPath, false));
        Folder top = db.createFolder("/top");
        top.listeners().add(Trigger.BEFORE_CREATE, folderListenerProxy);
        top.children().create("child");
    }

    public void bugtestListenBeforeCreateFolder2() {
        final String folderPath = "/top/middle/child";
        Folder.Event ev = new Folder.Event(Trigger.BEFORE_CREATE, folderPath, null);
        folderListener.expects(once()).method("handle").with(eq(ev)).will(checkFolderExists(folderPath, false));
        Folder top = db.createFolder("/top");
        Folder middle = db.createFolder("/top/middle");
        top.listeners().add(Trigger.BEFORE_CREATE, folderListenerProxy);
        middle.children().create("child");
    }

    private interface BothListener extends Document.Listener, Folder.Listener {/*nothing to add*/}
}
