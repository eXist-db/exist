<%@ page import="org.xmldb.api.base.*" %>
<%@ page import="org.xmldb.api.modules.*" %>
<%@ page import="org.xmldb.api.*" %>
<%@ page import="org.exist.xmldb.*" %>
<%@ page import="org.exist.storage.*" %>
<%@ page contentType="text/html; charset=UTF-8" %>

<%!
	public final static String DEFAULT_DRIVER = "org.exist.xmldb.DatabaseImpl";
	public final static String DEFAULT_URI = "xmldb:exist:///db";
	public final static String DEFAULT_USER = "guest";
	public final static String DEFAULT_PASSWORD = "guest";
	
	public void jspInit() {
		try {
			ServletConfig conf = getServletConfig();
			String driver = conf.getInitParameter("driver");
			if(driver == null)
				driver = DEFAULT_DRIVER;
			// initialize driver
			Class cl = Class.forName(DEFAULT_DRIVER);
			Database database = (Database)cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
		} catch(Exception e) {
		}
	}
%>
<html>
	<head>
		<title>eXist Database Status</title>
		<link rel="stylesheet" type="text/css" href="status/status.css">
		<META HTTP-EQUIV="refresh" CONTENT="10; URL=<%= request.getRequestURI() %>"/>
	</head>
	<body>
		<% 
			Collection root = 
				DatabaseManager.getCollection(DEFAULT_URI, "guest", "guest");
			DatabaseInstanceManager manager = (DatabaseInstanceManager)
				root.getService("DatabaseInstanceManager", "1.0");
			DatabaseStatus status = manager.getStatus();
		%>

		<table border="0" cellspacing="0" cellpadding="2">
			<tr><td colspan="2" align="left"><img src="logo.jpg"/></td></tr>
			<%@ include file="statusbar.jsp" %>
			<tr>
				<td>
	<table border="0" width="100%" cellpadding="0" cellspacing="10">
			<tr>
				<td valign="top" width="70%">
					<table class="status" border="0" width="100%" cellpadding="4" cellspacing="0">
						<tr>
							<th colspan="2" align="left">Configuration</th>
						</tr>
						<tr>
							<td width="30%" class="left">Database id:</td>
							<td><%= status.getId() %></td>
						</tr>
						<tr>
							<td width="30%" class="left">Configuration file:</td>
							<td><%= status.getConfPath() %></td>
						</tr>
						<tr>
							<td width="30%" class="left">Data directory:</td>
							<td><%= status.getDataDir() %>
						</tr>
					</table>
				</td>
				<td valign="top" width="30%">
					<% 
						Runtime rt = Runtime.getRuntime();
					%>
					<table class="status" border="0" width="100%" cellpadding="4" cellspacing="0">
						<tr>
							<th colspan="2" align="left">Memory</th>
						</tr>
						<tr>
							<td width="30%" class="left">Free:</td>
							<td align="right"><%= (rt.freeMemory() / 1024) + "K" %></td>
						</tr>
						<tr>
							<td width="30%" class="left">Total:</td>
							<td align="right"><%= (rt.totalMemory() / 1024) + "K" %></td>
						</tr>
						<tr>
							<td width="30%" class="left">Max:</td>
							<td align="right"><%= (rt.maxMemory() / 1024) + "K" %></td>
						</tr> 
					</table>
				</td>
			</tr>
		</table>
		<table border="0" width="100%" cellpadding="0" cellspacing="10">
			<tr><td>
		<table class="status" border="0" cellpadding="4" cellspacing="0">
			<tr>
				<th colspan="2" align="left">Current Status</th>
			</tr>
			<tr>
				<td width="70%" class="left">Max. number of concurrent requests:</td>
				<td><%= status.getMaxBrokers() %></td>
			</tr>
			<tr>
				<td width="70%" class="left">Number of brokers running:</td>
				<td><%= status.getAvailableBrokers() %></td>
			<tr>
				<td width="70%" class="left">Number of brokers currently available to handle 
				requests:</td>
				<td><%= status.getRunningBrokers() %></td>
			</tr>
		</table>
		</td></tr>
		</table>

		<table border="0" width="100%" cellpadding="0" cellspacing="10">
			<tr>
				<td align="left" width="25%">
					<% IndexStats stats = status.getIndexStats("elements.dbx"); %>
					<table class="status" border="0" cellpadding="4" cellspacing="0">
						<tr>
							<th colspan="5" align="left">elements.dbx</th>
						</tr>
						<tr>
							<th class="colheadings" align="left">Type</th>
							<th class="colheadings" align="left">Max.</th>
							<th class="colheadings" align="left">Used</th>
							<th class="colheadings" align="left">Hits</th>
							<th class="colheadings" align="left">Fails</th>
						</tr>
						<tr>
							<% BufferStats bstats = stats.getIndexBufferStats(); %>
							<td align="left" class="left">Index</td>
							<td align="left"><%= bstats.getSize() %></td>
							<td align="left"><%= bstats.getUsed() %></td>
							<td align="left"><%= bstats.getPageHits() %></td>
							<td align="left"><%= bstats.getPageFails() %></td>
						</tr>
						<tr>
							<% bstats = stats.getDataBufferStats(); %>
							<td align="left" class="left">Data</td>
							<td align="left"><%= bstats.getSize() %></td>
							<td align="left"><%= bstats.getUsed() %></td>
							<td align="left"><%= bstats.getPageHits() %></td>
							<td align="left"><%= bstats.getPageFails() %></td>
						</tr>
					</table>
				</td>
				<td align="center" width="25%">
				<% 
					stats = status.getIndexStats("collections.dbx"); 
				%>
				<table class="status" border="0" cellpadding="4" cellspacing="0">
					<tr>
						<th colspan="5" align="left">collections.dbx</th>
					</tr>
					<tr>
						<th class="colheadings" align="left">Type</th>
						<th class="colheadings" align="left">Max.</th>
						<th class="colheadings" align="left">Used</th>
						<th class="colheadings" align="left">Hits</th>
						<th class="colheadings" align="left">Fails</th>
					</tr>
					<tr>
						<% bstats = stats.getIndexBufferStats(); %>
						<td align="left" class="left">Index</td>
						<td align="left"><%= bstats.getSize() %></td>
						<td align="left"><%= bstats.getUsed() %></td>
						<td align="left"><%= bstats.getPageHits() %></td>
						<td align="left"><%= bstats.getPageFails() %></td>
					</tr>
					<tr>
						<% bstats = stats.getDataBufferStats(); %>
						<td align="left" class="left">Data</td>
						<td align="left"><%= bstats.getSize() %></td>
						<td align="left"><%= bstats.getUsed() %></td>
						<td align="left"><%= bstats.getPageHits() %></td>
						<td align="left"><%= bstats.getPageFails() %></td>
					</tr>
				</table>
			</td>
			<td align="center" width="25%">
			<% 
			stats = status.getIndexStats("words.dbx"); 
		%>
		<table class="status" border="0" width="100%" cellpadding="4" cellspacing="0">
			<tr>
				<th colspan="5" align="left">words.dbx</th>
			</tr>
			<tr>
				<th class="colheadings" align="left">Type</th>
				<th class="colheadings" align="left">Max.</th>
				<th class="colheadings" align="left">Used</th>
				<th class="colheadings" align="left">Hits</th>
				<th class="colheadings" align="left">Fails</th>
			</tr>
			<tr>
			<% bstats = stats.getIndexBufferStats(); %>
				<td align="left" class="left">Index</td>
				<td align="left"><%= bstats.getSize() %></td>
				<td align="left"><%= bstats.getUsed() %></td>
				<td align="left"><%= bstats.getPageHits() %></td>
				<td align="left"><%= bstats.getPageFails() %></td>
			</tr>
			<tr>
			<% bstats = stats.getDataBufferStats(); %>
				<td align="left" class="left">Data</td>
				<td align="left"><%= bstats.getSize() %></td>
				<td align="left"><%= bstats.getUsed() %></td>
				<td align="left"><%= bstats.getPageHits() %></td>
				<td align="left"><%= bstats.getPageFails() %></td>
			</tr>
		</table>
		</td><td width="25%" align="right">
		<% 
			stats = status.getIndexStats("dom.dbx"); 
		%>
		<table class="status" border="0" width="100%" cellpadding="4" cellspacing="0">
			<tr>
				<th colspan="5" align="left">dom.dbx</th>
			</tr>
			<tr>
				<th class="colheadings" align="left">Type</th>
				<th class="colheadings" align="left">Max.</th>
				<th class="colheadings" align="left">Used</th>
				<th class="colheadings" align="left">Hits</th>
				<th class="colheadings" align="left">Fails</th>
			</tr>
			<tr>
			<% bstats = stats.getIndexBufferStats(); %>
				<td align="left" class="left">Index</td>
				<td align="left"><%= bstats.getSize() %></td>
				<td align="left"><%= bstats.getUsed() %></td>
				<td align="left"><%= bstats.getPageHits() %></td>
				<td align="left"><%= bstats.getPageFails() %></td>
			</tr>
			<tr>
			<% bstats = stats.getDataBufferStats(); %>
				<td align="left" class="left">Data</td>
				<td align="left"><%= bstats.getSize() %></td>
				<td align="left"><%= bstats.getUsed() %></td>
				<td align="left"><%= bstats.getPageHits() %></td>
				<td align="left"><%= bstats.getPageFails() %></td>
			</tr>
		</table>
		</td></tr></table>
	</body>
</html>