module namespace c="http://exist-db.org/modules/mods-config";

declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace util="http://exist-db.org/xquery/util";

(: the base URI for the chiba servlet :)
declare variable $c:chiba { "/chiba/XFormsServlet?form=/forms/mods.xml" };

(: the xsl stylesheet to use for display :)
declare variable $c:overviewXsl { "styles/overview.xsl" };
declare variable $c:detailsXsl { "styles/mods-detailed.xsl" };

declare variable $c:css { "styles/display.css" };

(:  called to select elements from a record for display :)
declare function c:displayItem($record as element())
as element()+
{
    $record/mods:titleInfo,
    $record/mods:name,
    $record/mods:abstract,
    $record/mods:subject,
    $record/mods:originInfo/mods:dateIssued
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

declare function c:sidebar($url as xs:string)
{
    <div id="sidebar">
        <div class="block">
            <h3>Menu</h3>
            <ul>
                <li>
                    <a href="{$url}" id="current">Home</a>
                </li>
                <li>
                    <a href="#">Detailed View</a>
                </li>
                <li>
                    <a href="#">Process Document</a>
                </li>
            </ul>
        </div>
        
        <div class="block">
            <h3>Search</h3>
            <form action="{$url}" method="GET">
                <input class="search-sidebar" name="query" type="text" />
        		<input type="submit" class="search-button" value="search"/>
            </form>
        </div>
    </div> 
};
