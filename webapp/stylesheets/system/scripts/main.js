function toggle(id) {
    var element = document.getElementById(id);
    with (element.style) {
        if ( display == "none" ){
            display = ""
        } else{
            display = "none"
        }
    }
    var text = document.getElementById(id + "-switch").firstChild;
    if (text.nodeValue == "[show]") {
        text.nodeValue = "[hide]";
    } else {
        text.nodeValue = "[show]";
    }
}