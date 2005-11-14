xquery version "1.0";

declare option exist:serialize "media-type=text/xml";

<ajax-response>
    <option></option>
    {
        for $entry in //example-queries/query
        return
            <option value="{$entry/code}">{$entry/description}</option>
    }
</ajax-response>