xquery version "1.0";
(: $Id$ :)

module namespace conf="http://exist-db.org/modules/mods-config";
declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace util="http://exist-db.org/xquery/util";

import module namespace md="http://exist-db.org/biblio/mods-display" at "mods-display.xq";

declare variable $conf:preload { true() };

declare variable $conf:css { "styles/display.css" };

declare function conf:get-namespace-decls() as xs:string? {
	"declare namespace mods='http://www.loc.gov/mods/v3';"
};

declare function conf:get-query-root($collection as xs:string) as xs:string {
    concat("collection('", $collection, "')//mods:mods")
};

declare function conf:display-record-full($rec as element()) as element() {
    md:record-full($rec)
};

declare function conf:display-names($rec as element()) as element()* {
    md:names($rec/mods:name)
};

declare function conf:display-year($rec as element()) as xs:string? {
    md:year($rec)
};

declare function conf:display-titles($rec as element()) as element()* {
    md:titles($rec)
};

(: Get the XPath expression for the specified field :)
declare function conf:queryField($field as xs:string) as xs:string
{
	if($field eq "au") then
	    "mods:name"
	else if($field eq "ti") then
	    "mods:titleInfo"
	else if($field eq "ab") then
	    "mods:abstract"
	else if($field eq "su") then
	    "mods:subject"
	else if($field eq "ye") then
	    "mods:originInfo/mods:dateIssued"
	else
	    "."
};

declare function conf:query-form($url as xs:string, $collection as xs:string) as element() {
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
