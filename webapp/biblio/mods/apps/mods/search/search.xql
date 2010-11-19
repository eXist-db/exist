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
declare namespace biblio="http:/exist-db.org/xquery/biblio";
declare namespace group="http://exist-db.org/mods/sharing/group";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace functx = "http://www.functx.com";

import module namespace config="http://exist-db.org/mods/config" at "config.xqm";
import module namespace jquery="http://exist-db.org/xquery/jquery" at "resource:org/exist/xquery/lib/jquery.xql";

import module namespace mods="http://www.loc.gov/mods/v3" at "retrieve-mods.xql";
import module namespace sort="http://exist-db.org/xquery/sort" at "java:org.exist.xquery.modules.sort.SortModule";
import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace sharing="http://exist-db.org/mods/sharing" at "sharing.xqm";

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";

declare option exist:serialize "method=xhtml media-type=application/xhtml+xml omit-xml-declaration=no enforce-xhtml=yes";

declare function functx:replace-first( $arg as xs:string?, $pattern as xs:string, $replacement as xs:string )  as xs:string {       
   replace($arg, concat('(^.*?)', $pattern),
             concat('$1',$replacement))
 } ;

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
        <field name="All">(
        mods:mods[ft:query(.//*, '$q', $options)]
		)</field>
        <field name="Id">mods:mods[@ID = '$q']</field>
	</fields>;

(:
    Default template to be used for form generation if no
    query was specified
:)
declare variable $biblio:TEMPLATE_QUERY :=
    <query>
        <collection>{$config:mods-root}</collection>
        <and>
            <field m="1" name="All"></field>
        </and>
    </query>;
    
(:~
    Regenerate the HTML form to match the query, e.g. after adding more filter
    clauses.
:)
declare function biblio:form-from-query($incomingQuery as element()?) as element()+ {
    let $query := if ($incomingQuery//field) then $incomingQuery else $biblio:TEMPLATE_QUERY
    for $field at $pos in $query//field
    return
    
        <tr class="repeat">
            <td class="label">
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
                <jquery:input name="input{$pos}" value="{$field/string()}">
                    <jquery:autocomplete url="autocomplete.xql"
                        width="300" multiple="false"
                        matchContains="false"
                        paramsCallback="autocompleteCallback">
                    </jquery:autocomplete>
                </jquery:input>
            </td>
            <td>
                in <select name="field{$pos}">
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
        </tr>
};

declare function local:create-group-collections-string($group-ids as xs:string+,  $group-collections as xs:string*) as xs:string*
{
    if(not(empty($group-collections)))then(
        fn:concat(
            "collection(",
            fn:string-join(
                for $shared-collection in $group-collections return
                    fn:concat("'", $shared-collection, "'")
            , ","),
            ")//"
        )
    )
    else
    (
        util:log("debug", fn:concat("biblio: could not find any shared collections for the group with id: ", $group-ids)), 
        fn:concat("collection('", $config:mods-root, "')//")
    )
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
            let $log := util:log("DEBUG", ("$expr0: ", $expr0))
            let $collection-path := $xml/ancestor::query/collection/string()
            
            let $collection := if($collection-path eq $sharing:groups-collection or $collection-path eq fn:replace($sharing:groups-collection, "/db/", ""))then
            (
                let $group-ids := sharing:get-users-groups(security:get-user-credential-from-session()[1])/@id return
                    if(not(empty($group-ids)))then
                    (
                        let $group-collections := for $group-id in $group-ids return
                            sharing:find-group-collections($group-id)
                        return
                            local:create-group-collections-string($group-ids, $group-collections)
                    )
                    else
                    (
                         util:log("debug", "biblio: could not find any shared collections for this user"), 
                         fn:concat("collection('", $config:mods-root, "')//")
                    )
            )            
            else if(fn:starts-with($collection-path, $sharing:groups-collection) or fn:starts-with($collection-path, fn:replace($sharing:groups-collection, "/db/", "")))then
            (
                (: search inside a group of shared collections :)
                 let $group-id := fn:replace($collection-path, ".*/", ""),
                 $group-collections := sharing:find-group-collections($group-id) return
                    local:create-group-collections-string($group-id, $group-collections)
            )
            else
            (
                (: search a single collection :)
                fn:concat("collection('", $collection-path, "')//")
            )
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
            for $query-term in $xml/*
                return biblio:xml-query-to-string($query-term)
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
        case element(collection) return
            fn:concat("collection(""", $xml, """):")
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
    let $collection := request:get-parameter("collection", $config:mods-root)
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
            <query>
                <collection>{$collection}</collection>
            </query>
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
            <li><a href="?history={$query/@id}&amp;query-tabs=advanced-search-form">{biblio:xml-query-to-string($query)}</a></li>
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
        case element(biblio:optimize-trigger) return
            let $user := request:get-attribute("xquery.user")
            return
                if (xmldb:is-admin-user($user)) then
                    <a id="optimize-trigger" href="#">Create custom indexes for sorting</a>
                else
                    ()
        case element(biblio:collection-tree) return
            let $show := request:get-parameter("collection-tree", "hidden")
            return
                <div id="collection-tree" class="{$show}">
                {for $child in $node/node() return biblio:process-templates($query, $hitCount, $child)}
                </div>
        case element(biblio:form-from-query) return
            biblio:form-from-query($query)
        case element(biblio:result-count) return
            text { $hitCount }
        case element(biblio:query-history) return
            biblio:query-history()
        case element(biblio:collection-path) return
            let $collection := functx:replace-first(request:get-parameter("collection", $config:mods-root), "/db/", "")
            return
                <input class="collection-input" type="text" name="collection" value="{$collection}" readonly="true"/>
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
            security:is-collection-owner(security:get-user-credential-from-session()[1], request:get-parameter("collection", $config:mods-root))
        case element(biblio:has-collection-write-permissions) return
            security:can-write-collection(security:get-user-credential-from-session()[1], request:get-parameter("collection", $config:mods-root))
        case element(biblio:form-select-collection) return
            biblio:form-select-collection($node/@name)
        case element(biblio:form-collection-sharing) return
            biblio:form-collection-sharing(request:get-parameter("collection", $config:mods-root))
        case element(biblio:form-add-member-to-sharing-group) return
            biblio:form-add-member-to-sharing-group()
        case element(biblio:form-add-sharing-group) return
            biblio:form-add-sharing-group()
        case element(biblio:resource-types) return
            let $classifier := $node/@classifier/string()
            let $app-collection := $style:db-path-to-app
            let $code-tables := concat($app-collection, '/apps/mods/code-tables')
            let $document-path := concat($code-tables, '/document-type-codes.xml')
            let $language-path := concat($code-tables, '/language-2-type-codes.xml')
            let $code-table-type := doc($document-path)/code-table
            let $code-table-lang := doc($language-path)/code-table
            return 
                <div class="content">
                    <form id="{if ($classifier eq 'stand-alone') then 'new-resource-form' else 'add-related-form'}" action="../edit/edit.xq" method="GET">
                        <ul>
                            <li>
                                <input type="radio" name="type" value="default" selected="true"/><span> Default</span>
                            </li>
                        {
                            for $item in $code-table-type//item[classifier = $classifier]
                            let $label := $item/label/text()
                            order by $label
                            return
                                <li>
                                  <input type="radio" name="type" value="{$item/value/text()}"/><span> {$item/label/text()}</span>
                                </li>
                        }
                        </ul>
                        <div>
                            <label for="lang">Language: </label>
                            <select name="lang">
                            {
                                for $item in $code-table-lang//item
                                let $label := $item/label/text()
                                order by $label
                                return
                                    <option value="{$item/value/text()}">{$item/label/text()}</option>
                            }
                            </select>
                        </div>
                        <input type="hidden" name="collection"/>
                        <input type="hidden" name="host"/>
                    </form>
                </div>
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

    let $current-collection := request:get-parameter("collection", $config:mods-root) return

        <select name="{$select-name}">
        {
            if(security:can-write-collection(security:get-user-credential-from-session()[1], "/db"))then
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

declare function biblio:form-add-member-to-sharing-group() {
     <jquery:dialog id="add-member-to-group-sharing-dialog" modal="true" title="Add User to Group" width="200">
        <div>
            <jquery:input name="members-list" id="members-list">
                <jquery:autocomplete url="autocomplete-username.xql" width="300" multiple="false" matchContains="false"/>
            </jquery:input>
        </div>
        <input id="add-member-to-group-button" type="button" value="Add"/>
    </jquery:dialog>
};

declare function biblio:form-add-sharing-group() {
    <jquery:dialog id="new-group-sharing-dialog" modal="true" title="New Group" width="200">
        <label for="new-group-name" value="Group Name"/><input type="text" id="new-group-name"/>
        <input id="add-group-button" type="button" value="Add"/>
    </jquery:dialog>
};

declare function biblio:form-collection-sharing($collection as xs:string) {
    <jquery:dialog id="sharing-collection-dialog" modal="true" title="Collection Sharing" trigger="#collection-sharing" width="450">
        <jquery:button label="Update" function="updateCollectionSharing"/>
        <jquery:button id="cancel" label="Cancel"/>
        <form id="update-collection-sharing-form" action="operations.xql">
            <input id="sharing-collection-path_" type="hidden" name="collection"/>
            <div>Sharing settings for: <span id="sharing-collection-path_"></span></div>
            <div class="sharing-option">
                <input type="checkbox" id="sharing-collection-with-group" name="sharing-collection-with" value="group">
                {
                 (:   if(sharing:group-readable($collection))then(attribute checked{ "checked" })else()
                 :)
                 ()
                }
                </input>
                <label for="sharing-collection-with-group" class="labelWithCheckboxLeft">Share with Group</label>
                {
                let $groups := sharing:get-groups(),
                $collection-group-id := sharing:get-group-id($collection) return
                    <div id="group-sharing-panel" class="sharing-panel">
                        <div>
                            <label for="group-list">Group</label>
                            <select id="group-list" name="group">
                            {
                                for $group in $groups return
                                    if($group/@id eq $collection-group-id)then
                                    (
                                        <option selected="selected" value="{$group/@id}">{$group/group:name/text()}</option>
                                    )
                                    else
                                    (
                                        <option value="{$group/@id}">{$group/group:name/text()}</option>
                                    )
                            }
                            </select>
                            <input id="new-group-button" type="button" value="New Group"/>
                        </div>
                        <div id="group-members">
                            <div>
                                <label for="group-members-list">Members</label>
                                <ui id="group-members-list">
                                {
                                    (: get members of the selected group, or if none selected then the first group :)
                                    let $selected-group := if(not(empty($groups[@id eq $collection-group-id])))then($groups[@id eq $collection-group-id])else($groups[1]) return
                                        if($selected-group)then
                                        (
                                            for $group-member at $i in sharing:get-group-members($selected-group/@id) return
                                            <li>
                                                <input id="group-member-{$i}" type="checkbox" name="group-member" value="{$group-member}" checked="checked"/>
                                                <label id="group-member-{$i}" class="labelWithCheckboxLeft">{$group-member}</label>
                                            </li>
                                        )else()
                                }
                                </ui>
                            </div>
                            <input id="add-new-member-to-group-button" type="button" value="Add Member"/>
                        </div>
                        <div>
                            <span>
                                <input id="group-sharing-premissions-read" type="checkbox" name="group-sharing-permissions" value="read">
                                {
                                (:
                                    if(sharing:group-readable($collection))then(attribute checked{ "checked" })else()
                                    :)
                                    ()
                                }
                                </input>
                                <label for="group-sharing-premissions-read" class="labelWithCheckboxLeft">Read</label>
                            </span>
                            <span style="margin-left: 1em">
                                <input id="group-sharing-premissions-write" type="checkbox" name="group-sharing-permissions" value="write">
                                {
                                    (:
                                    if(sharing:group-writeable($collection))then(attribute checked{ "checked" })else()
                                    :)
                                    ()
                                }
                                </input>
                                <label for="group-sharing-premissions-write" class="labelWithCheckboxLeft">Write</label>
                            </span>
                        </div>
                    </div>
            }
            </div>
            <div class="sharing-option">
                <input type="checkbox" id="sharing-collection-with-other" name="sharing-collection-with" value="other">
                {
                    (:
                    if(sharing:other-readable($collection))then(attribute checked{ "checked" })else()
                    :)
                    ()
                }
                </input>
                <label for="sharing-collection-with-other" class="labelWithCheckboxLeft">Share with Everyone</label>
                <div id="other-sharing-panel" class="sharing-panel">
                    <div>
                        <span>
                            <input id="other-sharing-premissions-read" type="checkbox" name="other-sharing-permissions" value="read">
                            {
                                (:
                                if(sharing:other-readable($collection))then(attribute checked{ "checked" })else()
                                :)
                                ()
                            }
                            </input>
                            <label for="other-sharing-premissions-read" class="labelWithCheckboxLeft">Read</label>
                        </span>
                        <span style="margin-left: 1em">
                            <input id="other-sharing-premissions-write" type="checkbox" name="other-sharing-permissions" value="write">
                            {
                                (:
                                if(sharing:other-writeable($collection))then(attribute checked{ "checked" })else()
                                :)
                                ()
                            }
                            </input>
                            <label for="other-sharing-premissions-write" class="labelWithCheckboxLeft">Write</label>
                        </span>
                    </div>
                </div>
            </div>
        </form>
    </jquery:dialog>
};

declare function biblio:get-writeable-subcollection-paths($path as xs:string) {
    
	for $sub in xmldb:get-child-collections($path)
	let $col := concat($path, "/", $sub) return
		(
			if(security:can-write-collection(security:get-user-credential-from-session()[1], $col))then
			(
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

(:~
    Filter an existing result set by applying an additional
    clause with "and".
:)
declare function biblio:apply-filter() {
    let $prevQuery := session:get-attribute("query")
    let $filter := request:get-parameter("filter", ())
    let $value := request:get-parameter("value", ())
    
    return
        if (not($prevQuery/field)) then
            <query>
                { $prevQuery/collection }
                <field name="{$filter}">{$value}</field>
            </query>
        else
            <query>
                { $prevQuery/collection }
                <and>
                { $prevQuery/*[not(self::collection)] }
                <field name="{$filter}">{$value}</field>
                </and>
            </query>
};

session:create(),
(: We receive an HTML template as input :)
let $input := request:get-data()
let $filter := request:get-parameter("filter", ())
let $history := request:get-parameter("history", ())
let $reload := request:get-parameter("reload", ())
let $clear := request:get-parameter("clear", ())
let $mylist := request:get-parameter("mylist", ())
let $collection := request:get-parameter("collection", ())
let $id := request:get-parameter("id", ())

(: Process request parameters and generate an XML representation of the query :)
let $queryAsXML :=
    if (empty($collection)) then
        () (: no parameters sent :)
    else if ($reload) then
        session:get-attribute('query')
    else if ($history) then
        biblio:query-from-history($history)
    else if ($clear) then
        biblio:clear()
    else if ($filter) then 
        biblio:apply-filter()
    else if ($id) then
        <query>
            <collection>{$config:mods-root}</collection>
            <field m="1" name="Id">{$id}</field>
        </query>
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
    jquery:process(
        biblio:process-templates($queryAsXML, $results, $input)
        (: biblio:process-templates(if ($queryAsXML//field) then $queryAsXML else $biblio:TEMPLATE_QUERY, $results, $input) :)
    )
let $header := response:set-header("Content-Type", "application/xhtml+xml")
return
    $output
