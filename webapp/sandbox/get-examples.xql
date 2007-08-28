xquery version "1.0";
(: $Id$ :)

declare option exist:serialize "media-type=text/xml omit-xml-declaration=yes";

<select id="saved" name="saved">
    <option value=""></option>
    {
    for $entry in //example-queries/query
    return
        <option value="{$entry/code}">{$entry/description}</option>
    }
</select>