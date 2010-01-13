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
import module namespace jquery="http://exist-db.org/xquery/jquery" at "jquery.xql";

declare namespace biblio="http:/exist-db.org/xquery/biblio";
import module namespace mods="http://www.loc.gov/mods/v3" at "retrieve-mods.xql";

declare option exist:serialize "media-type=text/xml omit-xml-declaration=no";

declare variable $biblio:COLLECTION := "collection('/db')//";

(:~
    Simple mapping from field names to an XPath expression
:)
declare variable $biblio:FIELDS :=
	<fields>
		<field name="Title">mods:mods[ft:query(mods:titleInfo, '$q')]</field>
		<field name="Author">mods:mods[ft:query(mods:name, '$q')]</field>
		<field name="All">mods:mods[ft:query(., '$q')]</field>
	</fields>;

(:
    Default template to be used for form generation if no
    query was specified
:)
declare variable $biblio:TEMPLATE_QUERY :=
    <query>
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
                    if ($field/preceding-sibling::field) then
                        $field/parent/local-name(.)
                    else
                        ()
                return
                    <select name="operator{$pos}">
                    { if (empty($operator)) then attribute style { "display: none;" } else () }
                    {
                        for $opt in ("and", "or")
                        return
                            <option>
                            { $opt }
                            {
                                if ($opt eq $operator) then
                                    attribute selected { "selected" }
                                else
                                    ()
                            }
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
                        matchContains="false">
                        <jquery:param name="field" function="autocompleteCallback"/>
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
            biblio:generate-query($xml/*[1])
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
        case element(field) return
            let $expr0 := $biblio:FIELDS/field[@name = $xml/@name]
            let $expr := if ($expr0) then $expr0 else $biblio:FIELDS/field[last()]
            return
                ($biblio:COLLECTION, replace($expr, '\$q', $xml/string()))
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
declare function biblio:orderExpr($field as xs:string) as xs:string
{
    if ($field = "Author") then
        "biblio:orderByName($hit)"
    else if ($field = "Title") then
        "$hit/mods:titleInfo[1]/mods:title[1]"
    else
        "$hit/mods:originInfo/mods:dateCreated[1] descending"
};

(:~
    Evaluate the actual XPath query and order the results
:)
declare function biblio:evaluate-query($query as xs:string, $sort as xs:string) {
    let $sortExpr := biblio:orderExpr($sort)
    let $orderedQuery :=
            concat("for $hit in ", $query, " order by ", $sortExpr, " return $hit")
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
        let $log := util:log("DEBUG", ("Query: ", $queryAsXML))
        let $sort0 := request:get-parameter("sort", ())
        let $sort := if ($sort0) then $sort0 else session:get-attribute("sort")
        let $results := biblio:evaluate-query($query, $sort)
        (:~ Take the query results and store them into the HTTP session. :)
        let $null := session:set-attribute('cached', $results)
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
    let $null := session:remove-attribute('cached')
    let $null := session:remove-attribute('query')
    let $null := session:remove-attribute('sort')
    return
        ()
};

(:~
    Scan the input HTML template and expand the biblio:* tags
    found therein.
:)
declare function biblio:process-templates($query as element(), $hitCount as xs:integer, $node as node()) {
    typeswitch ($node)
        case element(biblio:form-from-query) return
            biblio:form-from-query($query)
        case element(biblio:result-count) return
            text { $hitCount }
        case element(biblio:query-history) return
            biblio:query-history()
        case element() return
            element { node-name($node) } {
                $node/@*,
                for $child in $node/node()
                return
                    biblio:process-templates($query, $hitCount, $child)
            }
        default return
            $node
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
(: Process request parameters and generate an XML representation of the query :)
let $queryAsXML :=
    if ($history) then
        biblio:query-from-history($history)
    else if ($clear) then
        biblio:clear()
    else if ($filter) then 
        biblio:apply-filter() 
    else 
        biblio:process-form()
(: Evaluate the query :)
let $results := biblio:eval-query($queryAsXML)
(:  Process the HTML template received as input :)
let $output :=
    jquery:process-templates(
        biblio:process-templates(if ($queryAsXML) then $queryAsXML else $biblio:TEMPLATE_QUERY, $results, $input)
    )
return
    $output