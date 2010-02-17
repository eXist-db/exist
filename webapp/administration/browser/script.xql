xquery version "1.0";

declare namespace script = "http://exist-db.org/xquery/script";
declare namespace request = "http://exist-db.org/xquery/request";

import module namespace json="http://www.json.org";

declare function script:showSubCollections() as element() {
	let $parentCollection := request:get-parameter("collection", "")
	let $subCollections := xmldb:get-child-collections($parentCollection)
	return
	<b>{
		for $collection in $subCollections
		return
			<a>
				<title>{$collection}</title>
				<isFolder>true</isFolder>
				<isLazy>true</isLazy>
				<key>{concat($parentCollection, "/", $collection)}</key>
			</a>}
	</b>
};

declare function script:showSubResources() as element() {
	let $parentCollection := request:get-parameter("collection", "")
	let $subResources := xmldb:get-child-resources($parentCollection)
	return
	<table cellpadding="0" cellspacing="0" border="1" class="display" id="dataTable">
		<thead>
			<tr>
				<th>File</th>
				<th>Size</th>
			</tr>
		</thead>
		<tbody>
			{
			for $resource in $subResources
			return
			<tr>
				<td>{$resource}</td>
				{let $size := xmldb:size($parentCollection, $resource)
				return
					if ($size le 1024) then
						<td>{$size} B</td>
					else 
						let $size := round($size div 1024)
						return
						if ($size le 1024) then
							<td>{$size} KB</td>
						else
							<td>{round($size div 1024)} MB</td>
				}
			</tr>
			}
		</tbody>
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