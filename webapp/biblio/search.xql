xquery version "1.0";

(:~
    The core XQuery script for the bibliographic demo. It receives a template XML document
    from the controller and expands it. If a search was trigger by the user, the script
    proceeds as follows:
    
    <ul>
        <li>the input form parameters are transformed into a simple XML structure
            to describe the query</li>
        <li>an XPath string is generated from the XML query structure</li>
        <li>the XPath is executed and the sort criteria applied</li>
        <li>query results, XML query and sort criteria are stored into the HTTP session</li>
        <li>the template is expanded, forms are regenerated to match the query</li>
    </ul>
    
    To apply a filter to an existing query, we just extend the XML representation
    of the query.
:)
import module namespace jquery="http://exist-db.org/xquery/jquery" at "resource:org/exist/xquery/lib/jquery.xql";

declare namespace biblio="http:/exist-db.org/xquery/biblio";

declare namespace xmldb="http://exist-db.org/xquery/xmldb";

import module namespace mods="http://www.loc.gov/mods/v3" at "retrieve-mods.xql";
import module namespace sort="http://exist-db.org/xquery/sort"
	at "java:org.exist.xquery.modules.sort.SortModule";
	
declare option exist:serialize "media-type=text/xml omit-xml-declaration=no";

declare variable $biblio:CREDENTIALS := ("biblio", "mods");

declare variable $biblio:COLLECTION := "/db/mods";

(:~
    Simple mapping from field names to an XPath expression
:)
declare variable $biblio:FIELDS :=
	<fields>
		<field name="Title">(
		mods:mods[ft:query(mods:titleInfo, '$q', $options)]
		      union
		mods:mods[ft:query(mods:relatedItem/mods:titleInfo, '$q', $options)]
		)</field>
		<field name="Name">(
		mods:mods[ft:query(mods:name, '$q', $options)]
		      union
		mods:mods[ft:query(mods:relatedItem/mods:name, '$q', $options)]		
		)</field>
		<field name="Date">(
		mods:mods[ft:query(mods:originInfo/mods:dateCreated, '$q', $options)]
		      union
		mods:mods[ft:query(mods:originInfo/mods:dateIssued, '$q', $options)]
		      union
		mods:mods[ft:query(mods:originInfo/mods:dateCaptured, '$q', $options)]
		      union
		mods:mods[ft:query(mods:originInfo/mods:copyrightDate, '$q', $options)]
		      union
		mods:mods[ft:query(mods:relatedItem/mods:originInfo/mods:dateCreated, '$q', $options)]
		      union
		mods:mods[ft:query(mods:relatedItem/mods:originInfo/mods:dateIssued, '$q', $options)]
		      union
		mods:mods[ft:query(mods:relatedItem/mods:originInfo/mods:dateCaptured, '$q', $options)]
		      union
		mods:mods[ft:query(mods:relatedItem/mods:originInfo/mods:copyrightDate, '$q', $options)]
		)</field>
		<field name="Abstract">mods:mods[ft:query(mods:abstract, '$q', $options)]</field>
        <field name="Note">mods:mods[ft:query(mods:note, '$q', $options)]</field>
        <field name="Subject">mods:mods[ft:query(mods:subject, '$q', $options)]</field>
        <field name="All Except Date">(
        mods:mods[ft:query(.//*, '$q', $options)]
		)</field>
        
	</fields>;

(:
    Default template to be used for form generation if no
    query was specified
:)
declare variable $biblio:TEMPLATE_QUERY :=
    <query>
        <collection>{$biblio:COLLECTION}</collection>
        <and>
            <field m="1" name="All"></field>
        </and>
    </query>;

(:~
    Regenerate the HTML form to match the query, e.g. after adding more filter
    clauses.
:)
declare function biblio:form-from-query($query as element()) as element()+ {
    for $field at $pos in $query//field
    return
        <tr class="repeat">
            <td>
            {
                let $operator := 
                    if ($field/preceding-sibling::*) then
                        $field/../local-name(.)
                    else
                        ()
                return
                    <select name="operator{$pos}">
                    { if (empty($operator)) then attribute style { "display: none;" } else () }
                    {
                        for $opt in ("and", "or", "not")
                        return
                            <option>
                            {
                                if ($opt eq $operator) then
                                    attribute selected { "selected" }
                                else
                                    ()
                            }
                            { $opt }
                            </option>
                    }
                    </select>
            }
            </td>
            <td>
                <select name="field{$pos}">
                {
                    for $f in $biblio:FIELDS/field
                    return
                        <option>
                            { if ($f/@name eq $field/@name) then attribute selected { "selected" } else () }
                            {$f/@name/string()}
                        </option>
                }
                </select>
            </td>
            <td>
                <jquery:input name="input{$pos}" value="{$field/string()}">
                    <jquery:autocomplete url="autocomplete.xql"
                        width="300" multiple="false"
                        matchContains="false"
                        paramsCallback="autocompleteCallback">
                    </jquery:autocomplete>
                </jquery:input>
            </td>
        </tr>
};

(:~
    Generate an XPath query string from the given XML representation
    of the query.
:)
declare function biblio:generate-query($xml as element()) as xs:string* {
    typeswitch ($xml)
        case element(query) return
            for $child in $xml/*
            return
                biblio:generate-query($child)
        case element(and) return
            (
                biblio:generate-query($xml/*[1]), 
                " intersect ", 
                biblio:generate-query($xml/*[2])
            )
        case element(or) return
            (
                biblio:generate-query($xml/*[1]), 
                " union ", 
                biblio:generate-query($xml/*[2])
            )
        case element(not) return
            (
                biblio:generate-query($xml/*[1]), 
                " except ", 
                biblio:generate-query($xml/*[2])
            )
        case element(field) return
            let $expr0 := $biblio:FIELDS/field[@name = $xml/@name]
            let $expr := if ($expr0) then $expr0 else $biblio:FIELDS/field[last()]
            let $collection := concat("collection('", $xml/ancestor::query/collection/string(), "')//")
            return
                ($collection, replace($expr, '\$q', $xml/string()))
        case element(collection) return
            if (not($xml/..//field)) then
                ('collection("', $xml/string(), '")//mods:mods')
            else
                ()
        default return
            ()
};

(:~
    Transform the XML representation of the query into a simple string
    for display to the user in the query history.
:)
declare function biblio:xml-query-to-string($xml as element()) as xs:string* {
    typeswitch ($xml)
        case element(query) return
            biblio:xml-query-to-string($xml/*[1])
        case element(and) return
            (
                biblio:xml-query-to-string($xml/*[1]), 
                " AND ", 
                biblio:xml-query-to-string($xml/*[2])
            )
        case element(or) return
            (
                biblio:xml-query-to-string($xml/*[1]), 
                " OR ", 
                biblio:xml-query-to-string($xml/*[2])
            )
        case element(not) return
            (
                biblio:xml-query-to-string($xml/*[1]), 
                " NOT ", 
                biblio:xml-query-to-string($xml/*[2])
            )
        case element(field) return
            concat($xml/@name, ':', $xml/string())
        default return
            ()
};

(:~
    Process single form parameter. Called from biblio:process-form().
:)
declare function biblio:process-form-parameters($params as xs:string*) as element() {
    let $param := $params[1]
    let $n := substring-after($param, 'input')
    let $value := request:get-parameter($param, "")
    let $field := request:get-parameter(concat("field", $n), 'all')
    let $operator := request:get-parameter(concat("operator", $n), "and")
    return
        if (count($params) eq 1) then
            <field m="{$n}" name="{$field}">{$value}</field>
        else
            element { xs:QName($operator) } {
                biblio:process-form-parameters(subsequence($params, 2)),
                <field m="{$n}" name="{$field}">{$value}</field>
            }
};

(:~
    Process the received form parameters and create an XML representation of
    the query. Filter out empty parameters and take care of boolean operators.
:)
declare function biblio:process-form() as element(query)? {
    let $collection := request:get-parameter("collection", $biblio:COLLECTION)
    let $fields :=
        (:  Get a list of all input parameters which are not empty,
            ordered by input name. :)
        for $param in request:get-parameter-names()[starts-with(., 'input')]
        let $value := request:get-parameter($param, ())
        where string-length($value) gt 0
        order by $param descending
        return
            $param
    return
        if (exists($fields)) then
            (:  process-form recursively calls itself for every parameter and
                generates and XML representation of the query. :)
            <query>
                <collection>{$collection}</collection>
                { biblio:process-form-parameters($fields) }
            </query>
        else
            ()
};

(:~
    Helper function used to sort by name within the "order by"
    clause of the query.
:)
declare function biblio:orderByName($m as element()) as xs:string?
{
    for $name in $m/mods:name[1]
    return
        mods:retrieve-name($name, 1)
};
    
(: Map order parameter to xpath for order by clause :)
declare function biblio:orderExpr($field as xs:string?) as xs:string
{
    if (sort:has-index('mods:name')) then
        if ($field eq "Score") then
            "ft:score($hit) descending"
        else if ($field = "Author") then
            "sort:index('mods:name', $hit)"
        else if ($field = "Title") then
            "sort:index('mods:title', $hit)"
        else
            "sort:index('mods:date', $hit)"
    else
        if ($field eq "Score") then
            "ft:score($hit) descending"
        else if ($field = "Author") then
            "biblio:orderByName($hit)"
        else if ($field = "Title") then
            "$hit/mods:titleInfo[1]/mods:title[1]"
        else
            "$hit/mods:originInfo/mods:dateCreated[1] descending"
};

(:~
    Evaluate the actual XPath query and order the results
:)
declare function biblio:evaluate-query($query as xs:string, $sort as xs:string?) {
    let $sortExpr := biblio:orderExpr($sort)
    let $orderedQuery :=
            concat("for $hit in ", $query, " order by ", $sortExpr, " return $hit")
    let $options :=
        <options>
            <default-operator>and</default-operator>
        </options>
    return
        util:eval($orderedQuery)
};

(:~
    Add a query to the user's query history. We store the XML representation
    of the query.
:)
declare function biblio:add-to-history($queryAsXML as element()) {
    let $oldHistory := session:get-attribute('history')
    let $newHistory :=
        let $n := if ($oldHistory) then max(for $n in $oldHistory/query/@n return xs:int($n)) + 1 else 1
        return
            <history>
                <query id="q{$n}" n="{$n}">
                    { $queryAsXML/* }
                </query>
                { $oldHistory/query }
            </history>
    return
        session:set-attribute('history', $newHistory)
};

(:~
    Retrieve a query from the query history
:)
declare function biblio:query-from-history($id as xs:string) {
    let $history := session:get-attribute('history')
    return
        $history/query[@id = $id]
};

(:~
    Returns the query history as a HTML list. The queries are
    transformed into a simple string representation.
:)
declare function biblio:query-history() {
    <ul>
    {
        let $history := session:get-attribute('history')
        for $query at $pos in $history/query
        return
            <li><a href="?history={$query/@id}&amp;query-tabs=advanced">{biblio:xml-query-to-string($query)}</a></li>
    }
    </ul>
};

(:~
    Evaluate the query given as XML and store its results into the HTTP session
    for later reference.
:)
declare function biblio:eval-query($queryAsXML as element()?) {
    if ($queryAsXML) then
        let $query := string-join(biblio:generate-query($queryAsXML), '')
        let $log := util:log("DEBUG", ("QUERY: ", $query))
        let $sort0 := request:get-parameter("sort", ())
        let $sort := if ($sort0) then $sort0 else session:get-attribute("sort")
        let $results := biblio:evaluate-query($query, $sort)
        (:~ Take the query results and store them into the HTTP session. :)
        let $null := session:set-attribute('mods:cached', $results)
        let $null := session:set-attribute('query', $queryAsXML)
        let $null := session:set-attribute('sort', $queryAsXML)
        let $null := biblio:add-to-history($queryAsXML)
        return
            count($results)
    else
        0
};

(:~
    Clear the last query result.
:)
declare function biblio:clear() {
    let $null := session:remove-attribute('mods:cached')
    let $null := session:remove-attribute('query')
    let $null := session:remove-attribute('sort')
    return
        ()
};

(:~
    Scan the input HTML template and expand the biblio:* tags
    found therein.
:)
declare function biblio:process-templates($query as element()?, $hitCount as xs:integer?, $node as node()) {
    typeswitch ($node)
        case element(biblio:login) return
            let $user := request:get-attribute("xquery.user")
            return (
                if ($user eq 'guest') then
                    <div id="login"><a href="#" id="login-link">Login</a></div>
                else
                    <div id="login">Logged in as <span class="username">{$user}</span>. <a href="?logout=1">Logout</a></div>
            )
        case element(biblio:form-from-query) return
            biblio:form-from-query($query)
        case element(biblio:result-count) return
            text { $hitCount }
        case element(biblio:query-history) return
            biblio:query-history()
        case element(biblio:collection-path) return
            let $collection := request:get-parameter("collection", $biblio:COLLECTION)
            return (
                <span class="collection-path">{$collection}</span>,
                <input type="hidden" name="collection" value="{$collection}"/>
            )
        case element(biblio:form-select-current-user-groups) return
            biblio:form-select-current-user-groups($node/@name)
        case element(biblio:current-user) return
            <span>{request:get-attribute("xquery.user")}</span>
        case element(biblio:conditional) return
            let $result := biblio:conditional($node) return
                if($result)then
                (
                    for $child in $result/node() return
                        biblio:process-templates($query, $hitCount, $child)
                )
                else()
        case element(biblio:is-collection-owner) return
            biblio:is-collection-owner()
        case element(biblio:has-collection-write-permissions) return
            biblio:has-collection-write-permissions(request:get-parameter("collection", $biblio:COLLECTION))
        case element(biblio:form-select-collection) return
            biblio:form-select-collection($node/@name)
        case element() return
            element { node-name($node) } {
                $node/@*,
                for $child in $node/node() return
                    biblio:process-templates($query, $hitCount, $child)
            }
        default return
            $node
};

declare function biblio:form-select-current-user-groups($select-name as xs:string) as element(select) {
    let $user := request:get-attribute("xquery.user") return
        <select name="{$select-name}">
        {
            for $group in xmldb:get-user-groups($user) return
                <option value="{$group}">{$group}</option>
        }
        </select>
};

declare function biblio:form-select-collection($select-name as xs:string) as element(select) {

    let $current-collection := request:get-parameter("collection", $biblio:COLLECTION) return

        <select name="{$select-name}">
        {
            if(biblio:has-collection-write-permissions("/db"))then
            (
                element option {
                    if("/db" eq $current-collection)then
                    (
                        attribute selected { "selected" }
                    )else(),
                    text { "/db" }
                }
            )else(),
        
            for $sub-collection-path in biblio:get-writeable-subcollection-paths("/db") return
                element option {
                    if($sub-collection-path eq $current-collection)then
                    (
                        attribute selected { "selected" }
                    )else(),
                    text { $sub-collection-path }
                }
        }
        </select>
};

declare function biblio:get-writeable-subcollection-paths($path as xs:string) {
    
	for $sub in xmldb:get-child-collections($path)
	let $col := concat($path, "/", $sub) return
		(
			if(biblio:has-collection-write-permissions($col))then(
			 $col
			)else(),
			biblio:get-writeable-subcollection-paths($col)
		)
};

declare function biblio:conditional($node as element(biblio:conditional)) {
    let $test-result := biblio:process-templates((), (), $node/biblio:test/biblio:*) return
        if($test-result) then
        (
            $node/biblio:result
        )else()
};

declare function biblio:is-collection-owner() as xs:boolean {
    let $user := request:get-attribute("xquery.user"),
    $collection-owner := xmldb:get-owner(request:get-parameter("collection", $biblio:COLLECTION)) return
        $user eq $collection-owner
};

declare function biblio:has-collection-write-permissions($collection as xs:string) as xs:boolean {
	if (empty(collection($collection))) then
		false()
	else
    let $user := request:get-attribute("xquery.user"),
    $collection-permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection)) return
        if(fn:matches($collection-permissions, ".......w."))then
        (
            (: world writeable :)
            true()
        )
        else if(xmldb:get-user-groups($user) = xmldb:get-group($collection) and fn:matches($collection-permissions, "....w...."))then
        (
            (: group writeable :)
            true()
        )
        else if($user eq xmldb:get-owner($collection) and fn:matches($collection-permissions, ".w......."))then
        (
            (: owner writeable :)
            true()
        )
        else
        (
            false()
        )
};

(:~
    Filter an existing result set by applying an additional
    clause with "and".
:)
declare function biblio:apply-filter() {
    let $prevQuery := session:get-attribute("query")
    let $filter := request:get-parameter("filter", ())
    let $value := request:get-parameter("value", ())
    return
        if ($filter = ('All', 'Title') and count($prevQuery/field) eq 1) then
            <query>
                <field>{$prevQuery/field/@name, 
                    normalize-space(concat($prevQuery/field/string(), ' ', $value))
                }</field>
            </query>
        else
            <query>
                <and>
                { $prevQuery/* }
                { <field name="{$filter}">{$value}</field> }
                </and>
            </query>
};

session:create(),
(: We receive an HTML template as input :)
let $input := request:get-data()
let $filter := request:get-parameter("filter", ())
let $history := request:get-parameter("history", ())
let $clear := request:get-parameter("clear", ())
let $mylist := request:get-parameter("mylist", ())
let $collection := request:get-parameter("collection", ())

(: Process request parameters and generate an XML representation of the query :)
let $queryAsXML :=
    if ($history) then
        biblio:query-from-history($history)
    else if ($clear) then
        biblio:clear()
    else if ($filter) then 
        biblio:apply-filter() 
    else if ($mylist eq 'display') then
        ()
    else 
        biblio:process-form()
let $log := util:log("DEBUG", ("$queryAsXML: ", $queryAsXML))
(: Evaluate the query :)
let $results :=
    if ($mylist) then (
        if ($mylist eq 'clear') then
            session:set-attribute("personal-list", ())
        else
            (),
        let $list := session:get-attribute("personal-list")
        let $items :=
            for $item in $list/listitem
            return
                util:node-by-id(doc(substring-before($item/@id, '#')), substring-after($item/@id, '#'))
        let $null := session:set-attribute('mods:cached', $items)
        return
            count($items)
    ) else
        biblio:eval-query($queryAsXML)
(:  Process the HTML template received as input :)
let $output :=
    jquery:process-templates(
        biblio:process-templates(if ($queryAsXML//field) then $queryAsXML else $biblio:TEMPLATE_QUERY, $results, $input)
    )
return
    $output
