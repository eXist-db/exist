module namespace c="http://exist-db.org/modules/mods-config";

declare namespace mods="http://www.loc.gov/mods/v3";

(: the base URI for the chiba servlet :)
declare variable $c:chiba { "/chiba/XFormsServlet?form=/forms/mods.xml" };

(: the xsl stylesheet to use for display :)
declare variable $c:overviewXsl { "styles/overview.xsl" };
declare variable $c:detailsXsl { "styles/mods-detailed.xsl" };

declare variable $c:css { "styles/default-style.css" };

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

declare function c:sidebar($url as xs:string)
{
    <div id="sidebar">
        <div id="navcontainer">
            <ul id="navlist">
                <li id="active">
                    <a href="{$url}" id="current">Home</a>
                </li>
                <li>
                    <a href="{$c:chiba}&amp;submitsave=store.xq">Add Record</a>
                </li>
                <li>
                    <a href="#">Detailed View</a>
                </li>
                <li>
                    <a href="#">Process Document</a>
                </li>
            </ul>
            <form action="{$url}" method="GET">
                <input class="search-sidebar" name="query" type="text" />
        		<input type="submit" class="search-button" value="search"/>
            </form>
        </div>
    </div> 
};
