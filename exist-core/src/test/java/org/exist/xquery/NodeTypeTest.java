package org.exist.xquery;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

/**
 * RemoveAndReloadTest.java
 *
 * O2 IT Engineering
 * Zurich,  Switzerland (CH)
 *
 * This test provokes a parameter type error (how?).
 * 
 * @author Tobias Wunden
 * @version 1.0
 */
public class NodeTypeTest {

	@ClassRule
	public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

	public static final String DOC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<page partition=\"home\" path=\"/\" version=\"live\">" +
			"    <header>" +
			"        <renderer>home_dreispaltig</renderer>" +
			"        <layout>default</layout>" +
			"        <type>default</type>" +
			"        <publish>" +
			"            <from>2005/06/06 10:53:40 GMT</from>" +
			"            <to>292278994/08/17 07:12:55 GMT</to>" +
			"        </publish>" +
			"        <security>" +
			"            <owner>www</owner>" +
			"            <permission id=\"system:manage\" type=\"role\">system:editor</permission>" +
			"            <permission id=\"system:read\" type=\"role\">system:guest</permission>" +
			"            <permission id=\"system:translate\" type=\"role\">system:translator</permission>" +
			"            <permission id=\"system:publish\" type=\"role\">system:publisher</permission>" +
			"            <permission id=\"system:write\" type=\"role\">system:editor</permission>" +
			"        </security>" +
			"        <keywords/>" +
			"        <title language=\"de\">Home</title>" +
			"        <title language=\"fr\">Home</title>" +
			"        <title language=\"it\">Home</title>" +
			"        <modified>" +
			"            <date>2005/06/06 10:53:40 GMT</date>" +
			"            <user>markus.jauss</user>" +
			"        </modified>" +
			"    </header>" +
			"    <body/>" +
			"</page>";

	/**
	 * This test passes nodes containing xml entities to eXist and tries
	 * to read it back in:
	 * <ul>
	 * <li>Register a database instance</li>
	 * <li>Write a "live" document to the database using the XQueryService</li>
	 * <li>Create a "work" version of it</li>
	 * </ul>
	 */
	@Test
	public final void removeAndReload() throws XMLDBException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		// write "live" document to the database
		store(DOC, "live.xml");
		
		// copy content from work.xml to live.xml using XUpdate
		prepareWorkVersion();
	}

	/**
	 * Stores the given xml fragment into the database.
	 * 
	 * @param xml the xml document
	 * @param document the document name	 
	 */
	private final void store(final String xml, final String document) throws XMLDBException {
		final StringBuilder query = new StringBuilder();
		query.append("declare namespace xmldb='http://exist-db.org/xquery/xmldb';");
		query.append("let $isLoggedIn := xmldb:login('" + XmldbURI.ROOT_COLLECTION_URI + "', '" + TestUtils.ADMIN_DB_USER + "', '" + TestUtils.ADMIN_DB_USER + "'),");
		query.append("$doc := xmldb:store('" + XmldbURI.ROOT_COLLECTION + "', $document, $data)");
		query.append("return <result/>");

		final XQueryService service = (XQueryService)server.getRoot().getService("XQueryService", "1.0");

        service.declareVariable("document", document);
        service.declareVariable("data", xml);
        final CompiledExpression cQuery = service.compile(query.toString());
        service.execute(cQuery);
	}

	/**
	 * Updates the given xml fragment in the database using XUpdate.
	 */
	private final void prepareWorkVersion() throws XMLDBException {
		final StringBuilder query = new StringBuilder();
		query.append("declare namespace xmldb='http://exist-db.org/xquery/xmldb';\n");
		query.append("declare namespace f='urn:weblounge';\n");

		// Returns a new with a given body and a new header
		query.append("declare function f:create($live as node(), $target as xs:string) as node() { \n");
		query.append("    <page partition='{$live/@partition}' path='{$live/@path}' version='{$target}'> \n");
		query.append("        {$live/*} \n");
		query.append("    </page> \n");
		query.append("}; \n");

		// Function "prepare". Checks if the work version already exists. If this is not the
		// case, it calls the "create" function to have a new page created with the live body
		// but with a "work" or "$target" header.
		query.append("declare function f:prepare($data as node(), $target as xs:string) as xs:string? { \n");
		query.append("    if (empty(xmldb:xcollection($collection)/page[@version eq $target])) then \n");
		query.append("        let $isLoggedIn := xmldb:login($collection, '" + TestUtils.ADMIN_DB_USER + "', '" + TestUtils.ADMIN_DB_PWD + "') \n");
		query.append("        return xmldb:store($collection, concat($target, '.xml'), f:create($data, $target)) \n");
		query.append("    else \n");
		query.append("    () \n");
		query.append("}; \n");
		
		// Main clause, tries to create a work from an existing live version
		query.append("let $live := xmldb:xcollection($collection)/page[@version eq 'live'],\n");
        query.append("     $log := util:log('DEBUG', $live),\n");
		query.append("     $w := f:prepare($live, 'work')\n");
		query.append("    return\n");
		query.append("		              ()\n");

		final XQueryService service = (XQueryService)server.getRoot().getService("XQueryService", "1.0");
        service.declareVariable("collection", XmldbURI.ROOT_COLLECTION);
        final CompiledExpression cQuery = service.compile(query.toString());
        service.execute(cQuery);
	}

	/**
	 * Updates the given xml fragment in the database using XUpdate.
	 */
	@SuppressWarnings("unused")
	private void xupdateRemove(final String doc) throws XMLDBException {
		final StringBuilder query = new StringBuilder();
		query.append("declare namespace xmldb='http://exist-db.org/xquery/xmldb';");
		query.append("let $isLoggedIn := xmldb:login('" + XmldbURI.ROOT_COLLECTION_URI + "', '" + TestUtils.ADMIN_DB_USER + "', '" + TestUtils.ADMIN_DB_USER + "'),");
		query.append("$mods := xmldb:remove('" + XmldbURI.ROOT_COLLECTION + "', '" + doc + "')");
		query.append("return <modifications>{$mods}</modifications>");

		final XQueryService service = (XQueryService)server.getRoot().getService("XQueryService", "1.0");
        final CompiledExpression cQuery = service.compile(query.toString());
        service.execute(cQuery);
	}

	/**
	 * Loads the xml document identified by <code>document</code> from the database.
	 *
	 * @param document the document to load	 
	 */
	@SuppressWarnings("unused")
	private Node load(final String document) throws XMLDBException {
		final StringBuilder query = new StringBuilder();
		query.append("let $result := doc(string-join(('" + XmldbURI.ROOT_COLLECTION + "', $document), '/'))");
		query.append("return ($result)");

		final XQueryService service = (XQueryService)server.getRoot().getService("XQueryService", "1.0");
        service.declareVariable("document", document);
        final CompiledExpression cQuery = service.compile(query.toString());
        final ResourceSet set = service.execute(cQuery);
        if (set != null && set.getSize() > 0) {
            return ((XMLResource)set.getIterator().nextResource()).getContentAsDOM();
        }
		return null;
	}
}