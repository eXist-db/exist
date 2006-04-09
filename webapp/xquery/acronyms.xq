xquery version "1.0";

declare namespace a="http://www.xml-acronym-demystifier.org";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace util="http://exist-db.org/xquery/util";

declare function a:query-part($field as xs:string) as xs:string
{
	if($field = "acronym") then
		"a:Acronym/@id"
	else if($field = "expansion") then
		"a:Acronym/@expansion"
	else if($field = "definition") then
		"a:Definition"
	else
		"."
};

declare function a:build-query($field as xs:string, $term as xs:string)
as xs:string
{
	concat("//a:Entry[", a:query-part($field), " &amp;= """, $term, """]")
};

declare function a:do-query() as element()
{
	let $field := request:get-parameter("field", "any"),
		$term := request:get-parameter("term", "")
	return
		if (string-length($term) = 0) then
			<p>Please specify one or more keywords to search for!</p>
		else
			<entries>
				{ util:eval(a:build-query($field, $term)) }
			</entries>
};

<document xmlns:xi="http://www.w3.org/2001/XInclude">
	
	<header>
    	<logo src="logo.jpg"/>
    	<title>Open Source Native XML Database</title>
		<author email="wolfgang@exist-db.org">Wolfgang M. Meier</author>
		<style href="styles/acronyms.css"/>
	</header>
    
	<xi:include href="sidebar.xml"/>

	<body>
		<section title="XML Acronym Demystifier Example">

		<p>A very simple example to search for XML acronyms. The required XML 
		source document is available from <a href="http://www.xml-acronym-demystifier.org/">The XML Acronym 
		Demystifier</a>. There's an XQuery script to install all examples automatically. Just go to the 
        <a href="../admin/admin.xql?user=admin&amp;password=&amp;panel=setup">Examples 
        Setup</a> page.</p>

		<form method="get" action="acronyms.xq">
			<table bgcolor="#F3F3F3" width="100%" cellpadding="5"
				cellspacing="0" border="0">
				<tr bgcolor="#D9D9D9">
					<th width="20%" align="left">Search for</th>
					<th width="80%" align="left" colspan="2">List of keywords</th>
				</tr>
				<tr>
					<td width="20%">
						<select name="field" size="1">
							<option value="acronym">Acronym</option>
							<option value="expansion">Expansion</option>
							<option value="definition">Definition</option>
							<option value="any" selected="true">Any Field</option>
						</select>
					</td>
					<td width="60%">
						<input type="text" name="term" size="30"/>
					</td>
					<td width="20%">
						<input type="submit"/>
					</td>
				</tr>
			</table>
		</form>
		{ a:do-query() }
		<p>
			<small>View <a href="source/acronyms.xq">source code</a>
			</small>
		</p>
		</section>
	</body>
</document>
