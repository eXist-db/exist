package org.exist.fluent;

import static org.junit.Assert.*;

import org.hamcrest.*;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class ListenerManagerTest extends DatabaseTestCase {
	private Mockery context = new JUnit4Mockery();
	private Document.Listener documentListener;
	private Folder.Listener folderListener;

	@Before public void prepareMocks() {
		documentListener = context.mock(Document.Listener.class, "documentListener");
		folderListener = context.mock(Folder.Listener.class, "folderListener");
	}

	@After public void unregisterMocks() throws Exception {
		if (documentListener != null) ListenerManager.INSTANCE.remove(documentListener);
		if (folderListener != null) ListenerManager.INSTANCE.remove(folderListener);
		documentListener = null;
		folderListener = null;
	}

	private Matcher<Document.Event> eqDelayedDoc(final Document.Event ev) {
		return new BaseMatcher<Document.Event>() {
			public void describeTo(Description desc) {
				desc.appendText("eqDelayedDoc(").appendValue(ev).appendText(")");
			}
			public boolean matches(Object o) {
				return new Document.Event(ev.trigger, ev.path, db.getDocument(ev.path)).equals(o);
			}
		};
	}

	private Action checkDocumentExists(final String path, final boolean shouldExist) {
		return new Action() {
			public void describeTo(Description desc) {
				desc.appendText("check that document '" + path + "' " + (shouldExist ? "exists" : "does not exist"));
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

	private Action checkFolderExists(final String path, final boolean shouldExist) {
		return new Action() {
			public void describeTo(Description desc) {
				desc.appendText("check that folder '" + path + "' " + (shouldExist ? "exists" : "does not exist"));
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

	private Action checkDocumentStamp(final String expectedStamp) {
		return new Action() {
			public void describeTo(Description desc) {
				desc.appendText("check that event document is stamped with '" + expectedStamp + "'");
			}

			public Object invoke(Invocation inv) throws Throwable {
				XMLDocument doc = ((Document.Event) inv.getParameter(0)).document.xml();
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
		return folder.documents().build(Name.overwrite(path.substring(k + 1)))
				.elem("test").attrIf(stamp != null, "stamp", stamp).end("test")
				.commit();
	}

	@Test public void listenDocumentsBeforeCreateDocument1() {
		final String docPath = "/top/test.xml";
		final Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, false));
		}});
		top.documents().listeners().add(Trigger.BEFORE_CREATE, documentListener);
		createDocument(docPath);
		createDocument("/elsewhere/test.xml");
		createDocument("/top/deeper/test.xml");
	}

	@Test public void listenDocumentsBeforeCreateDocument2() {
		final String docPath = "/top/test.xml";
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			never(documentListener).handle(with(any(Document.Event.class)));
		}});
		top.documents().listeners().add(Trigger.BEFORE_CREATE, documentListener);
		top.documents().listeners().remove(documentListener);
		createDocument(docPath);
	}

	@Test public void listenDocumentsAfterCreateDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.AFTER_CREATE, docPath, doc);
		doc.delete();
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(with(eqDelayedDoc(ev))); will(checkDocumentExists(docPath, true));
		}});
		top.documents().listeners().add(Trigger.AFTER_CREATE, documentListener);
		createDocument(docPath);
		createDocument("/elsewhere/test.xml");
		createDocument("/top/deeper/test.xml");
	}

	@Test public void listenDocumentsAfterCreateDocument2() {
		final String docPath = "/top/test.xml";
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			never(documentListener).handle(with(any(Document.Event.class)));
		}});
		top.documents().listeners().add(Trigger.AFTER_CREATE, documentListener);
		top.documents().listeners().remove(documentListener);
		createDocument(docPath);
	}

	@Test public void listenFolderBeforeCreateDocument1() {
		final String docPath = "/top/test2.xml";
		final Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, false));
		}});
		top.listeners().add(Trigger.BEFORE_CREATE, documentListener);
		createDocument(docPath);
		createDocument("/elsewhere/test.xml");
	}

	@Test public void listenFolderBeforeCreateDocument2() {
		final String docPath = "/top/test2.xml";
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			never(documentListener).handle(with(any(Document.Event.class)));
		}});
		top.listeners().add(Trigger.BEFORE_CREATE, documentListener);
		top.listeners().remove(documentListener);
		createDocument(docPath);
	}

	@Test public void listenFolderAfterCreateDocument1() {
		final String docPath = "/top/test2.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.AFTER_CREATE, docPath, doc);
		doc.delete();
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(with(eqDelayedDoc(ev))); will(checkDocumentExists(docPath, true));
		}});
		top.listeners().add(Trigger.AFTER_CREATE, documentListener);
		createDocument(docPath);
		createDocument("/elsewhere/test.xml");
	}

	@Test public void listenFolderAfterCreateDocument2() {
		final String docPath = "/top/test2.xml";
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			never(documentListener).handle(with(any(Document.Event.class)));
		}});
		top.listeners().add(Trigger.AFTER_CREATE, documentListener);
		top.listeners().remove(documentListener);
		createDocument(docPath);
	}

	@Test public void listenDocumentsBeforeUpdateDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath, "before");
		final Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentStamp("before"));
		}});
		top.documents().listeners().add(Trigger.BEFORE_UPDATE, documentListener);
		createDocument(docPath, "after");
		createDocument("/elsewhere/test.xml");
		createDocument("/top/deeper/test.xml");
	}

	@Test public void listenDocumentsAfterUpdateDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath, "before");
		final Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentStamp("after"));
		}});
		top.documents().listeners().add(Trigger.AFTER_UPDATE, documentListener);
		createDocument(docPath, "after");
		createDocument("/elsewhere/test.xml");
		createDocument("/top/deeper/test.xml");
	}

	@Test public void listenFolderBeforeUpdateDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath, "before");
		final Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentStamp("before"));
		}});
		top.listeners().add(Trigger.BEFORE_UPDATE, documentListener);
		createDocument(docPath, "after");
		createDocument("/elsewhere/test.xml");
	}

	@Test public void listenFolderAfterUpdateDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath, "before");
		final Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentStamp("after"));
		}});
		top.listeners().add(Trigger.AFTER_UPDATE, documentListener);
		createDocument(docPath, "after");
		createDocument("/elsewhere/test.xml");
	}

	@Test public void listenFolderDeepBeforeCreateDocument1() {
		final String docPath = "/top/middle/test2.xml";
		final Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
		Folder top = db.createFolder("/top");
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, false));
		}});
		top.listeners().add(Trigger.BEFORE_CREATE, documentListener);
		createDocument(docPath);
		createDocument("/elsewhere/test.xml");
	}

	@Test public void listenDocumentBeforeUpdateDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath, "before");
		final Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentStamp("before"));
		}});
		doc.listeners().add(Trigger.BEFORE_UPDATE, documentListener);
		createDocument(docPath, "after");
		createDocument("/elsewhere/test.xml");
		createDocument("/top/test2.xml");
	}

	@Test public void listenDocumentAfterUpdateDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath, "before");
		final Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentStamp("after"));
		}});
		doc.listeners().add(Trigger.AFTER_UPDATE, documentListener);
		createDocument(docPath, "after");
		createDocument("/elsewhere/test.xml");
		createDocument("/top/test2.xml");
	}

	@Test public void listenDocumentsBeforeDeleteDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, true));
		}});
		Folder top = db.createFolder("/top");
		top.documents().listeners().add(Trigger.BEFORE_DELETE, documentListener);
		doc.delete();
		createDocument("/elsewhere/test.xml").delete();
		createDocument("/top/deeper/test.xml").delete();
	}

	@Test public void listenDocumentsBeforeDeleteDocument2() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath, "before");
		doc.delete();
		final Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
		context.checking(new Expectations() {{
			one(documentListener).handle(with(eqDelayedDoc(ev))); will(checkDocumentExists(docPath, true));
		}});
		Folder top = db.createFolder("/top");
		top.documents().listeners().add(Trigger.BEFORE_DELETE, documentListener);
		createDocument(docPath).delete();
		createDocument("/elsewhere/test.xml").delete();
		createDocument("/top/deeper/test.xml").delete();
	}

	@Test public void listenDocumentsAfterDeleteDocument1() {
		final String docPath = "/top/test.xml";
		final Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, false));
		}});
		Folder top = db.createFolder("/top");
		top.documents().listeners().add(Trigger.AFTER_DELETE, documentListener);
		createDocument(docPath).delete();
		createDocument("/elsewhere/test.xml").delete();
		createDocument("/top/deeper/test.xml").delete();
	}

	@Test public void listenFolderBeforeDeleteDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, true));
		}});
		Folder top = db.createFolder("/top");
		top.listeners().add(Trigger.BEFORE_DELETE, documentListener);
		doc.delete();
		createDocument("/elsewhere/test.xml").delete();
	}

	@Test public void listenFolderBeforeDeleteDocument2() {
		final String docPath = "/top/deeper/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, true));
		}});
		Folder top = db.createFolder("/top");
		top.listeners().add(Trigger.BEFORE_DELETE, documentListener);
		doc.delete();
		createDocument("/elsewhere/test.xml").delete();
	}

	@Test public void listenFolderAfterDeleteDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, false));
		}});
		Folder top = db.createFolder("/top");
		top.listeners().add(Trigger.AFTER_DELETE, documentListener);
		doc.delete();
		createDocument("/elsewhere/test.xml").delete();
	}

	@Test public void listenFolderAfterDeleteDocument2() {
		final String docPath = "/top/deeper/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, false));
		}});
		Folder top = db.createFolder("/top");
		top.listeners().add(Trigger.AFTER_DELETE, documentListener);
		doc.delete();
		createDocument("/elsewhere/test.xml").delete();
	}

	@Test public void listenDocumentBeforeDeleteDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, true));
		}});
		doc.listeners().add(Trigger.BEFORE_DELETE, documentListener);
		doc.delete();
		createDocument("/elsewhere/test.xml").delete();
		createDocument("/top/deeper/test.xml").delete();
		createDocument("/top/test2.xml").delete();
	}

	@Test public void listenDocumentAfterDeleteDocument1() {
		final String docPath = "/top/test.xml";
		XMLDocument doc = createDocument(docPath);
		final Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
		context.checking(new Expectations() {{
			one(documentListener).handle(ev); will(checkDocumentExists(docPath, false));
		}});
		doc.listeners().add(Trigger.AFTER_DELETE, documentListener);
		doc.delete();
		createDocument("/elsewhere/test.xml").delete();
		createDocument("/top/deeper/test.xml").delete();
		createDocument("/top/test2.xml").delete();
	}

	@Test @Ignore("not yet implemented") public void listenBeforeCreateFolder1() {
		final String folderPath = "/top/child";
		final Folder.Event ev = new Folder.Event(Trigger.BEFORE_CREATE, folderPath, null);
		context.checking(new Expectations() {{
			one(folderListener).handle(ev); will(checkFolderExists(folderPath, false));
		}});
		Folder top = db.createFolder("/top");
		top.listeners().add(Trigger.BEFORE_CREATE, folderListener);
		top.children().create("child");
	}

	@Test @Ignore("not yet implemented") public void listenBeforeCreateFolder2() {
		final String folderPath = "/top/middle/child";
		final Folder.Event ev = new Folder.Event(Trigger.BEFORE_CREATE, folderPath, null);
		context.checking(new Expectations() {{
			one(folderListener).handle(ev); will(checkFolderExists(folderPath, false));
		}});
		Folder top = db.createFolder("/top");
		Folder middle = db.createFolder("/top/middle");
		top.listeners().add(Trigger.BEFORE_CREATE, folderListener);
		middle.children().create("child");
	}
}
