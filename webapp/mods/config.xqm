module namespace c="http://exist-db.org/modules/mods-config";

declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace util="http://exist-db.org/xquery/util";

declare variable $c:css { "styles/display.css" };

(:  called to select elements from a record for display :)
declare function c:displayItem($record as element())
as element()+
{
    $record/mods:titleInfo,
    $record/mods:name,
    $record/mods:abstract,
    $record/mods:subject,
    $record/mods:originInfo/mods:dateIssued,
    $record/mods:location
};

declare function c:orderByName($m as element()) as xs:string?
{
    let $name := $m/mods:name[1],
        $order :=
            if($name/mods:namePart[@type='family']) then
                concat($name/mods:namePart[@type='family'], ", ", $name/mods:namePart[@type='given'])
            else if($name/mods:namePart) then
                xs:string($name/mods:namePart)
            else
                ""
    return
        (util:log("debug", $order),
        $order)
};

(: Map order parameter to xpath for order by clause :)
declare function c:orderExpr($field as xs:string) as xs:string
{
	if ($field = "creator") then
        "c:orderByName($m)"
	else if ($field = "title") then
		"$m/m:titleInfo[1]/m:title[1]"
	else
		"$m/m:originInfo/m:dateCreated[1] descending"
};

declare function c:sidebar($url as xs:string, $user as xs:string, $collection as xs:string)
as element()
{
    <div id="sidebar">
        <div class="block">
            <h3>Menu</h3>
            <ul>
                <li>
                    <a href=".." id="current">Home</a>
                </li>
                <li>
                    <a href="{$url}" id="current">MODS Example</a>
                </li>
                <li>
                    <a href="{$url}?action=logout">Logout</a>
                </li>
            </ul>
        </div>
        
        <div class="block">
            <h3>Search</h3>
            <form action="{$url}" method="GET">
                <input class="search-sidebar" name="query" type="text" />
        		<input type="submit" class="search-button" value="search"/>
            </form>
            <ul>
                <li><a href="{$url}?show-form=true">Advanced Query</a></li>
            </ul>
        </div>
        
        <div class="userinfo">
            Logged in as: {$user}<br/>
            Collection: {$collection}
        </div>
    </div> 
};

declare function c:query-form($url as xs:string, $collection as xs:string) as element() {
    <form action="{$url}" method="GET">
        <table id="query" cellpadding="5" cellspacing="0" border="0">
            <tr>
                <th width="20%">
                    Search in
                </th>
                <th width="60%">
                    Search what
                </th>
                <th width="20%">
                </th>
            </tr>
            <tr>
                <td width="20%">
                    <select name="field1" size="1">
                        <option value="any" selected="true">Any</option>
                        <option value="au">Creator,Editor</option>
                        <option value="ti">Title</option>
                        <option value="ab">Description</option>
                        <option value="su">Topic</option>
                    </select>
                 </td>
                 <td width="60%">
                   <input type="text" name="term1" size="30" />
                 </td>
                 <td width="20%">
                   <select name="op" size="1">
                     <option value="or" selected="true">or</option>
                     <option value="and" selected="false">and</option>
                   </select>
                 </td>
            </tr>
            <tr>
                <td width="20%">
                    <select name="field2" size="1">
                        <option value="any" selected="true">Any</option>
                        <option value="au">Creator,Editor</option>
                        <option value="ti">Title</option>
                        <option value="ab">Description</option>
                        <option value="su">Topic</option>
                    </select>
                 </td>
                 <td width="60%">
                   <input type="text" name="term2" size="30" />
                 </td>
                 <td width="20%"/>
            </tr>
            <tr>
                <td colspan="3">
                    Match:
                    <input name="mode" value="all" type="radio" checked="true"/>All terms
                    <input name="mode" value="any" type="radio"/>Any term
                    <input name="mode" value="near" type="radio"/>All terms near
                </td>
            </tr>
            <tr>
                <td colspan="3">
                    <input type="submit" value="Submit"/>
                </td>
            </tr>
        </table>
    </form>
};
