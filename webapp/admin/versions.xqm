module namespace rev="http://exist-db.org/xquery/admin-interface/revisions";

import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "dates.xqm";

declare namespace v="http://exist-db.org/versioning";

declare function rev:main() as element()
{
	let $resource := request:get-parameter("resource", ())
	return
		<div class="panel">
			<div class="panel-head">Revisions for resource {$resource}</div>
			<table cellspacing="0" cellpadding="5" class="browse revisions">
				<tr>
					<th>Revision</th>
					<th>Date</th>
					<th colspan="2">Actions</th>
				</tr>
				{rev:display-revisions($resource)}
			</table>
		</div>
};

declare function rev:display-revisions($resource as xs:string) {
	let $doc := doc($resource)
	let $docName := util:document-name($doc)
	let $collection := util:collection-name($doc)
	let $vCollection := concat("/db/system/versions", $collection)
	for $version in collection($vCollection)/v:version[v:properties[v:document = $docName]]
	let $rev := $version/v:properties/v:revision/string()
	order by xs:long($rev) ascending
	return (
		<tr>
			<td>{$version/v:properties/v:revision/text()}</td>
			<td>{date:format-dateTime($version/v:properties/v:date)}</td>
			<td>
				<a target="_new" 
					href="versions.xql?action=restore&amp;resource={$resource}&amp;rev={$rev}">
					Restore
				</a>
			</td>
			<td>
				<a href="#" onclick="return displayDiff('R{$rev}','{$resource}','{$rev}')">
				Diff
				</a>
			</td>
		</tr>,
		<tr>
			<td colspan="4">
				<div id="R{$rev}" class="diffsource" style="display: none"></div>
			</td>
		</tr>
	)
};
