$(document).ready(function() {
    if (document.getElementById('xqueries-container')) {
    	setTimeout('reloadScheduledJobs()', 3000);
    	setTimeout('reloadJobs()', 3000);
    	setTimeout('reloadQueries()', 5000);
	}

    /* Tabs */
    $(".tab-container .content:first").show();
    $(".tabs li a:first").addClass("tab-active");
    $(".tabs li a").click(function () {
        var linkIndex = $(".tabs li a").index(this);
        $(".tabs li a").removeClass("tab-active");
        $(".tab-container .content:visible").hide();
        $(".tab-container .content:eq(" + linkIndex + ")").show();
        $(this).addClass("tab-active");
        return false;
    });
    
    /* Repository: retrieve packages */
    $("#retrieve-repo").click(function (ev) {
        ev.preventDefault();
        $("#loading-indicator").show();
        var repoURL = $("input[name=repository-url]").val();
        $.ajax({
            type: "GET",
            url: "get-packages.xql",
            data: { url: repoURL },
            success: function (html) {
                $("#loading-indicator").hide();
                $("#packages").html(html);
                initPackages();
            }
        });
    });
    initPackages();
});

function initPackages() {
    $(".package").each(function () {
        var pkg = $(this);
        $(".icon", pkg).click(function (ev) {
            $(".details").hide();
            $(".details", pkg).show();
            $(".close-details", pkg).click(function (ev) {
                $(".details", pkg).hide();
            });
        });
    });
    var tallest = 0;
    $("li.package").each(function () {
        if ($(this).height() > tallest) {
            tallest = $(this).height();
        }
    });
    console.log("Tallest = %d", tallest);
    $("li.package").each(function() {
        $(this).height(tallest);
    });
}

function reloadScheduledJobs() {
	$.get('proc.xql', { "mode": "p" }, function (data) {
        $('#scheduled-jobs-container').html(data);
        setTimeout('reloadScheduledJobs()', 3000);
	});
}

function reloadJobs() {
	$.get('proc.xql', { "mode": "p" }, function (data) {
    	$('#processes-container').innerHTML = data;
		setTimeout('reloadJobs()', 3000);
	});
}

function reloadQueries() {
	$.get('proc.xql', { mode: "q" }, function (response) {
    	$('#xqueries-container').innerHTML = response.responseText;
		setTimeout('reloadQueries()', 5000);
	});
}

function displayDiff(id, resource, revision) {
    var div = $("#" + id);
    div.toggle();
	if (div.empty()) {
        var params = { action: "diff", resource: resource, rev: revision };
        $.get("versions.xql", params, function (data) {
            div.html('<pre class="prettyprint lang-xml">' +
				escapeXML(data) +
				'</pre>');
			prettyPrint();
            return false;
        });
	}
	return false;
}

function escapeXML(xml) {
	var out = '';
	for (var i = 0; i < xml.length; i++) {
		ch = xml.charAt(i);
		if (ch == '<') out += '&lt;'
		else if (ch == '>') out += '&gt;'
		else if (ch == '&') out += '&amp;'
		else out += ch;
	}
	return out;
}
