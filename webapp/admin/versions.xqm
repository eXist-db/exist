module namespace rev="http://exist-db.org/xquery/admin-interface/revisions";

import module namespace v="http://exist-db.org/versioning";
import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "dates.xqm";

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
					<th>User</th>
					<th colspan="3">Actions</th>
				</tr>
				{rev:display-revisions($resource)}
			</table>
		</div>
};

declare function rev:display-revisions($resource as xs:string) {
	let $doc := doc($resource)
	for $version in v:versions($doc)
	let $rev := $version/v:properties/v:revision/text()
	return (
		<tr>
			<td>{$rev}</td>
			<td>{date:format-dateTime($version/v:properties/v:date)}</td>
			<td>{$version/v:properties/v:user/text()}</td>
			<td>
				<a target="_new" 
					href="versions.xql?action=restore&amp;resource={$resource}&amp;rev={$rev}">
					Restore
				</a>
			</td>
			<td>
				<a href="versions.xql?action=diff&amp;resource={$resource}&amp;rev={$rev}" 
				    onclick="return displayDiff('R{$rev}','{$resource}','{$rev}')">
				Diff
				</a>
			</td>
			<td>
			    <a target="_new"
			        href="versions.xql?action=annotate&amp;resource={$resource}&amp;rev={$rev}">
			    Annotate
			    </a>
		    </td>
		</tr>,
		<tr>
			<td colspan="5">
				<div id="R{$rev}" class="diffsource" style="display: none"></div>
			</td>
		</tr>
	)
};
