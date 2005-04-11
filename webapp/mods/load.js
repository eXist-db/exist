var req;
var detailsDiv;

function loadXMLDoc(url) {
    // branch for native XMLHttpRequest object
    if (window.XMLHttpRequest) {
        req = new XMLHttpRequest();
        req.onreadystatechange = processReqChange;
        req.open("GET", url, true);
        req.send(null);
    // branch for IE/Windows ActiveX version
    } else if (window.ActiveXObject) {
        req = new ActiveXObject("Microsoft.XMLHTTP");
        if (req) {
            req.onreadystatechange = processReqChange;
            req.open("GET", url, true);
            req.send();
        }
    }
}

function processReqChange() {
    if (req.readyState == 4) {
        // only if "OK"
        if (req.status == 200) {
            details.innerHTML = req.responseText;
        } else {
            alert("There was a problem retrieving the XML data:\n" +
            req.statusText);
        }
    }
}

function loadDetails(pos) {
    var full = document.getElementById("f_" + pos);
    if (full) {
        full.setAttribute("class", "visible");
    } else {
        var rec = document.getElementById("r_" + pos);
        var tr = document.createElement("tr");
        tr.setAttribute("id", "f_" + pos);
        tr.setAttribute("class", "visible");
        details = document.createElement("td");
        details.setAttribute("colspan", "5");
        tr.appendChild(details);
        rec.parentNode.insertBefore(tr, rec.nextSibling);
        
        try {
            loadXMLDoc("details.xq?item=" + pos);
        } catch (e) {
            var msg = (typeof e == "string") ? e : ((e.message) ? e.message : "Unknown Error");
            alert("Unable to retrieve data:\n" + msg);
        }
    }   
    var link = document.getElementById("a_" + pos);
    if (link) {
        link.href = "javascript:hideDetails(" + pos + ")";
        var img = link.firstChild;
        img.setAttribute("src", "images/up.png");
    }
}

function hideDetails(pos) {
    var full = document.getElementById("f_" + pos);
    full.setAttribute("class", "hidden");
    
    var link = document.getElementById("a_" + pos);
    if (link) {
        link.href = "javascript:loadDetails(" + pos + ")";
        var img = link.firstChild;
        img.setAttribute("src", "images/down.png");
    }
}

function expandAll(start, end) {
    for (var i = start; i < end; i++) {
        loadDetails(i);
    }
}

function collapseAll(start, end) {
    for (var i = start; i < end; i++) {
        hideDetails(i);
    }
}

function toggleCheckboxes() {
    a = document.mainForm.elements;
    l = a.length;
    for (var i = 0; i < l; i++)
    if (a[i].type == "checkbox") a[i].checked = true;
}

function exportData() {
    document.mainForm.action = "export.xq";
    document.mainForm.target = "_new";
    document.mainForm.submit
}

function showError(message) {
    alert(message)
}
