xquery version "1.0";

declare namespace script = "http://exist-db.org/xquery/script";
declare namespace request = "http://exist-db.org/xquery/request";

import module namespace json="http://www.json.org";

declare function script:showSubCollections() as element() {
	let $module := request:get-parameter("collection", "")
	return
		if ($module eq "modules") then
		<b>
			<a>
				<title>eXist</title>
				<isFolder>true</isFolder>
				<isLazy>true</isLazy>
				<key>eXist</key>
			</a>
		</b>
		else if ($module eq "eXist") then
		<b>
			<a>
				<title>users</title>
				<isFolder>false</isFolder>
				<isLazy>true</isLazy>
				<key>eXist-users</key>
			</a>
			<a>
				<title>groups</title>
				<isFolder>false</isFolder>
				<isLazy>true</isLazy>
				<key>eXist-groups</key>
			</a>
		</b>
		else
		<b></b>
};

declare function script:showSubResources() as element() {
	let $module := request:get-parameter("collection", "")
	return
	if ($module eq "eXist-users") then
	<table cellpadding="0" cellspacing="0" border="1" class="display" id="dataTable">
		<thead>
			<tr>
				<th>Name</th>
				<th>UID</th>
			</tr>
		</thead>
		<tbody>
			{
			for $user in //users/user
			return
			<tr>
				<td>{xs:string($user/@name)}</td>
				<td>{xs:string($user/@uid)}</td>
			</tr>
			}
		</tbody>
	</table>
	else if ($module eq "eXist-groups") then
	<table cellpadding="0" cellspacing="0" border="1" class="display" id="dataTable">
		<thead>
			<tr>
				<th>Name</th>
				<th>UID</th>
			</tr>
		</thead>
		<tbody>
			{
			for $group in //groups/group
			return
				<tr>
					<td>{xs:string($group/@name)}</td>
					<td>{xs:string($group/@id)}</td>
				</tr>
			}
		</tbody>
	</table>
	else
	<table cellpadding="0" cellspacing="0" border="1" class="display" id="dataTable">
	</table>
};

let $action := request:get-parameter("action", "")
return
	if($action eq "showSubCollections") then
		let $xml := script:showSubCollections()
		let $json := json:xml-to-json($xml)
		let $sLenght := string-length($json)-7
		return
			if(substring($json, 7, $sLenght) eq "") then "[]"
			else substring($json, 7, $sLenght)
	else if($action eq "showCollectionResources") then
		script:showSubResources()
	else()