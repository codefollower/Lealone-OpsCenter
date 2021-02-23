﻿const opsQuery = { 
    data() {
        return {
			sql: "",
			autoComplete,
			autoSelect
        }
    },
    methods: {
        run() {
            lealone.get("ops-header")._query(this.sql);
        },
        runSelected() {
            lealone.route('ops', 'result', {result: "runSelected sql=" + this.sql});
        },
        manualAutoComplete() {
            lealone.route('ops', 'result', {result: "manualAutoComplete sql=" + this.sql});
        }
    },
    mounted() {
    }
}

/*
 * Copyright 2004-2021 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 *  * Initial Developer: H2 Group
 */
addEvent(window, "load", initSort);

function addEvent(elm, evType, fn, useCapture) {
    // addEvent and removeEvent
    // cross-browser event handling for IE5+,  NS6 and Mozilla
    // By Scott Andrew
    if (elm.addEventListener){
        elm.addEventListener(evType, fn, useCapture);
        return true;
    } else if (elm.attachEvent){
        var r = elm.attachEvent("on"+evType, fn);
        return r;
    } else {
        alert("Handler could not be added");
    }
}

function initSort() {
    if (document.getElementById('editing') != undefined) {
        // don't allow sorting while editing
        return;
    }
    var tables = document.getElementsByTagName("table");
    for (var i=0; i<tables.length; i++) {
        table = tables[i];
        if (table.rows && table.rows.length > 0) {
            var header = table.rows[0];
            for(var j=0;j<header.cells.length;j++) {
                var cell = header.cells[j];
                var text = cell.innerHTML;
                cell.innerHTML = '<a href="#" style="text-decoration: none;" class="sortHeader" onclick="resortTable(this);">'+text+'<span class="sortArrow">&nbsp;&nbsp;</span></a>';
            }
        }
    }
}

function editRow(row, session, write, undo) {
    var table = document.getElementById('editTable');
    var y = row < 0 ? table.rows.length - 1 : row;
    var i;
    for(i=1; i<table.rows.length; i++) {
        var cell = table.rows[i].cells[0];
        if (i == y) {
            var edit = '<img width=16 height=16 src="ico_ok.gif" onclick="editOk('+row+')" onmouseover = "this.className =\'icon_hover\'" onmouseout = "this.className=\'icon\'" class="icon" alt="'+write+'" title="'+write+'" border="1"/>';
            var undo = '<img width=16 height=16 src="ico_undo.gif" onclick="editCancel('+row+')" onmouseover = "this.className =\'icon_hover\'" onmouseout = "this.className=\'icon\'" class="icon" alt="'+undo+'" title="'+undo+'" border="1"/>';
            cell.innerHTML = edit + undo;
        } else {
            cell.innerHTML = '';
        }
    }
    var cells = table.rows[y].cells;
    for (i=1; i<cells.length; i++) {
        var cell = cells[i];
        var text = getInnerText(cell);
        // escape so we can edit data that contains HTML special characters
        // '&' needs to be replaced first
        text = text.replace(/&/g, '&amp;').
            replace(/'/g, '&apos;').
            replace(/"/g, '&quot;').
            replace(/</g, '&lt;').
            replace(/>/g, '&gt;');
        var size;
        var newHTML;
        if (text.indexOf('\n') >= 0) {
            size = 40;
            newHTML = '<textarea name="$rowName" cols="$size" onkeydown="return editKeyDown($row, this, event)">$t</textarea/>';
        } else {
            size = text.length+5;
            newHTML = '<input type="text" name="$rowName" value="$t" size="$size" onkeydown="return editKeyDown($row, this, event)" />';
        }
        newHTML = newHTML.replace('$rowName', 'r' + row + 'c' + i);
        newHTML = newHTML.replace('$row', row);
        newHTML = newHTML.replace('$t', text);
        newHTML = newHTML.replace('$size', size);
        cell.innerHTML = newHTML;
    }
}

function deleteRow(row, session, write, undo) {
    var table = document.getElementById('editTable');
    var y = row < 0 ? table.rows.length - 1 : row;
    var i;
    for(i=1; i<table.rows.length; i++) {
        var cell = table.rows[i].cells[0];
        if (i == y) {
            var edit = '<img width=16 height=16 src="ico_remove_ok.gif" onclick="deleteOk('+row+')" onmouseover = "this.className =\'icon_hover\'" onmouseout = "this.className=\'icon\'" class="icon" alt="'+write+'" title="'+write+'" border="1"/>';
            var undo = '<img width=16 height=16 src="ico_undo.gif" onclick="editCancel('+row+')" onmouseover = "this.className =\'icon_hover\'" onmouseout = "this.className=\'icon\'" class="icon" alt="'+undo+'" title="'+undo+'" border="1"/>';
            cell.innerHTML = edit + undo;
        } else {
            cell.innerHTML = '';
        }
    }
    var cells = table.rows[y].cells;
    for (i=1; i<cells.length; i++) {
        var s = cells[i].style;
        s.color = 'red';
        s.textDecoration = 'line-through';
    }
}

function editFinish(row, res) {
    var editing = document.getElementById('editing');
    editing.row.value = row;
    editing.op.value = res;
    editing.submit();
}

function editCancel(row) {
    editFinish(row, '3');
}

function editOk(row) {
    editFinish(row, '1');
}

function deleteOk(row) {
    editFinish(row, '2');
}

function editKeyDown(row, object, event) {
    var key=event.keyCode? event.keyCode : event.charCode;
    if (key == 46 && event.ctrlKey) {
        // ctrl + delete
        object.value = 'null';
        return false;
    } else if (key == 13) {
        if (!event.ctrlKey && !event.shiftKey && !event.altKey) {
            editOk(row);
            return false;
        }
    } else if (key == 27) {
        editCancel(row);
        return false;
    }
}

function getInnerText(el) {
    if (typeof el == "string") return el;
    if (typeof el == "undefined") { return el };
    if (el.innerText) {
        // not needed but it is faster
        return el.innerText;
    }
    var str = "";
    var cs = el.childNodes;
    var l = cs.length;
    for (var i = 0; i < l; i++) {
        switch (cs[i].nodeType) {
        case 1: //ELEMENT_NODE
            str += getInnerText(cs[i]);
            break;
        case 3:    //TEXT_NODE
            str += cs[i].nodeValue;
            break;
        }
    }
    return str;
}

function isNullCell(td) {
    return td.childNodes.length == 1 && (td.childNodes[0].nodeName == "I");
}

function resortTable(link) {
    // get the span
    var span;
    for (var ci=0;ci<link.childNodes.length;ci++) {
        if (link.childNodes[ci].tagName && link.childNodes[ci].tagName.toLowerCase() == 'span') {
            span = link.childNodes[ci];
        }
    }
    var spantext = getInnerText(span);
    var td = link.parentNode;
    var column = td.cellIndex;
    var table = getParent(td,'TABLE');
    var rows = table.rows;
    if (rows.length <= 1) return;

    // detect sort type
    var sortNumeric = true;
    for (i = 1; i < rows.length; i++) {
        var td = rows[i].cells[column];
        if (!isNullCell(td)) {
            var x = getInnerText(td);
            // H2 does not return numeric values with leading +, but may return
            // values in scientific notation
            if (!x.match(/^\-?\d*\.?\d+(?:[Ee][\+\-]?\d+)?$/)) {
                sortNumeric = false;
                break;
            }
        }
    }
    var newRows = new Array();
    for (i=1; i<rows.length; i++) {
        var o = new Object();
        o.data = rows[i];
        o.id = i;
        var td = o.data.cells[column];
        var n = isNullCell(td);
        o.isNull = n;
        if (!n) {
            var txt = getInnerText(td);
            if (sortNumeric) {
                o.sort = parseFloat(txt);
                if (isNaN(o.sort)) o.sort = 0;
            } else {
                o.sort = txt;
            }
        }
        newRows[i-1] = o;
    }
    newRows.sort(sortCallback);
    var arrow;
    if (span.getAttribute("sortDir") == 'down') {
        arrow = '&nbsp;<span style="color:gray">&#x25b2;</span>';
        newRows.reverse();
        span.setAttribute('sortDir','up');
    } else {
        arrow = '&nbsp;<span style="color:gray">&#x25bc;</span>';
        span.setAttribute('sortDir','down');
    }

    // we appendChild rows that already exist to the tbody,
    // so it moves them rather than creating new ones
    var body = table.tBodies[0];
    for (i=0; i<newRows.length; i++) {
        body.appendChild(newRows[i].data);
    }

    // delete any other arrows there may be showing
    var allSpans = document.getElementsByTagName("span");
    for (var i=0;i<allSpans.length;i++) {
        if (allSpans[i].className == 'sortArrow') {
            // in the same table as us?
            if (getParent(allSpans[i],"table") == getParent(link,"table")) {
                allSpans[i].innerHTML = '&nbsp;&nbsp;';
            }
        }
    }
    span.innerHTML = arrow;
}

function getParent(el, pTagName) {
    if (el == null) return null;
    else if (el.nodeType == 1 && el.tagName.toLowerCase() == pTagName.toLowerCase())    {
        // Gecko bug, supposed to be uppercase
        return el;
    } else {
        return getParent(el.parentNode, pTagName);
    }
}

function sortCallback(ra, rb) {
    if (ra.isNull) {
        return rb.isNull ? (ra.id - rb.id) : -1;
    } else if (rb.IsNull) {
        return 1;
    }
    return (ra.sort==rb.sort) ? (ra.id-rb.id) : (ra.sort<rb.sort ? -1 : 1);
}

var agent=navigator.userAgent.toLowerCase();
var is_opera = agent.indexOf("opera") >= 0;
var autoComplete = 0; // 0: off, 1: normal, 2: full
var autoSelect = 1; // 0: off, 1: on
var selectedRow = -1;
var lastList = '';
var lastQuery = null;
var columnsByTable = new Object();
var tableAliases = new Object();
var showAutoCompleteWait = 0;
var autoCompleteManual = false;
var req;

function refreshTables() {
    columnsByTable = new Object();
    var tables = parent.h2menu.tables;
    for(var i=0; i<tables.length; i++) {
        columnsByTable[tables[i].name] = tables[i].columns;
    }
}

function sizeTextArea() {
    var height=document.body.clientHeight;
    var sql = document.h2query.sql;
    sql.style.height=(height-sql.offsetTop)+'px';
}

function buildTableAliases(input) {
    tableAliases = new Object();
    var list = splitSQL(input);
    var last = "";
    for(var i=0; i<list.length; i++) {
        var word = list[i].toUpperCase();
        if (word != "AS") {
            if (columnsByTable[last]) {
                tableAliases[word] = last;
            }
            last = word;
        }
    }
}

function splitSQL(s) {
    var list = new Array();
    s = s.toUpperCase() + ' ';
    var e = s.length;
    for(var i=0; i<e; i++) {
        var ch = s.charAt(i);
        if (ch == '_' || (ch >= 'A' && ch <= 'Z')) {
            var start = i;
            do {
                ch = s.charAt(++i);
            } while (ch == '_' || (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z'));
            list[list.length] = s.substring(start, i);
        }
    }
    return list;
}

function help() {
    var input = document.h2query.sql;
    setSelection(input);
    var pos = input.selectionStart;
    if (pos > 0) {
        var s = input.value.substring(0, pos).toUpperCase();
        var e = pos-1;
        for(; e>=0; e-=1) {
            var ch = s.charAt(e);
            if (ch != '_' && (ch < '0' || ch > '9') && (ch < 'A' || ch > 'Z')) {
                break;
            }
        }
        s = s.substring(e+1, s.length);
        buildTableAliases(input.value);
        if (!columnsByTable[s]) {
            s = tableAliases[s];
        }
        if (columnsByTable[s]) {
            if (parent.h2menu.goToTable(s)) {
                parent.h2menu.document.location='tables.do?jsessionid=${sessionId}#' + s;
                // parent.h2menu.window.blur();
                document.h2query.sql.focus();
            }
        }
    }
}

function setSelection(element) {
    if (document.all && !is_opera) {
        try {
            var range = document.selection.createRange();
            var copy = range.duplicate();
            copy.moveToElementText(element);
            copy.setEndPoint('EndToEnd', range);
            element.selectionStart = copy.text.length - range.text.length;
            element.selectionEnd = element.selectionStart + range.text.length;
        } catch (e) {
            element.selectionEnd = element.selectionStart = 0;
        }
    }
}

function set(field, combo) {
    field.value=combo.value;
    combo.value='';
    field.focus();
}

function trim(s) {
    while (s.charAt(0)==' ' && s.length>0) {
        s=s.substring(1);
    }
    while (s.charAt(s.length-1)==' ' && s.length>0) {
        s=s.substring(0, s.length-1);
    }
    return s;
}

function trimCommas(s) {
    while (s.charAt(0)==',' && s.length>0) {
        s=s.substring(1);
    }
    while (s.charAt(s.length-1)==',' && s.length>0) {
        s=s.substring(0, s.length-1);
    }
    return s;
}

function insert(field, combo) {
    insertText(combo.value);
    combo.value='';
}

function insertText(s, isTable, event) {
    event.preventDefault();
    s = decodeURIComponent(s);
    var field = document.h2query.sql; 
    var last = s.substring(s.length-1);
    if (last != '.' && last != '\'' && last != '"' && last > ' ') {
        s += ' ';
    }
    if (isTable && trim(field.value)=='') {
        field.value = 'SELECT * FROM ' + s;
    } else {
        if (document.selection) {
            // IE
            field.focus();
            selection = document.selection.createRange();
            selection.text = s;
        } else if (field.selectionStart || field.selectionStart == '0') {
            // Firefox
            var startPos = field.selectionStart;
            var endPos = field.selectionEnd;
            field.value = field.value.substring(0, startPos) + s + field.value.substring(endPos, field.value.length);
            var pos = endPos + s.length;
            field.selectionStart = pos;
            field.selectionEnd = pos;
        } else {
            field.value += s;
        }
    }
    lealone.get("query").sql = field.value;
    field.focus();
}

function showAutoComplete() {
    if (showAutoCompleteWait==0) {
        showAutoCompleteWait=5;
        setTimeout('showAutoCompleteNow()', 100);
    } else {
        showAutoCompleteWait-=1;
    }
}

function showAutoCompleteNow() {
    var input = document.h2query.sql;
    setSelection(input);
    var pos = input.selectionStart;
    var s = input.value.substring(0, pos);
    if (s != lastQuery) {
        lastQuery = s;
        retrieveList(s);
    }
    showAutoCompleteWait = 0;
}

function keyDown(event) {
    var key=event.keyCode? event.keyCode : event.charCode;
    if (key == null) {
        return false;
    }
    if (key == 13 && (event.ctrlKey || event.metaKey)) {
        // ctrl + return, cmd + return
        submitAll();
        return false;
    } else if (key == 13 && event.shiftKey) {
        // shift + return
        submitSelected();
        return false;
    } else if (key == 32 && (event.ctrlKey || event.altKey)) {
        // ctrl + space
        manualAutoComplete();
        return false;
    } else if (key == 190 && autoComplete == 0) {
        // dot
        help();
        return true;
    }
    var table = getAutoCompleteTable();
    if (table.rows.length > 0) {
        if (key == 27) {
            // escape
            while (table.rows.length > 0) {
                table.deleteRow(0);
            }
            showOutput('');
            return false;
        } else if ((key == 9 && !event.shiftKey) || (key == 13 && !event.shiftKey && !event.ctrlKey && !event.altKey)) {
            // tab
            if (table.rows.length > selectedRow) {
                var row = table.rows[selectedRow];
                if (row.cells.length>1) {
                    insertText(row.cells[1].innerHTML);
                }
                removeAutoComplete();
                return false;
            }
        } else if (key == 38 && !event.shiftKey) {
            // up
            if (table.rows.length > selectedRow) {
                selectedRow = selectedRow <= 0 ? table.rows.length-1 : selectedRow-1;
                highlightRow(selectedRow);
                return false;
            }
        } else if (key == 40 && !event.shiftKey) {
            // down
            if (table.rows.length > selectedRow) {
                selectedRow = selectedRow >= table.rows.length-1 ? 0 : selectedRow+1;
                highlightRow(selectedRow);
                return false;
            }
        }
        if (autoComplete == 0) {
            // remove auto-complete if manually started
            while (table.rows.length > 0) {
                table.deleteRow(0);
            }
            showOutput('');
        }
    }
    // alert('key:' + key);
    return true;
    // bs:8 ret:13 lt:37 up:38 rt:39 dn:40 tab:9
    // pgup:33 pgdn:34 home:36 end:35 del:46 shift:16
    // ctrl, alt gr:17 alt:18 caps:20 5(num):12 ins:45
    // pause:19 f1..13:112..123 win-start:91 win-ctx:93 esc:27
    // cmd:224
}

function keyUp(event) {
    var key = event == null ? 0 : (event.keyCode ? event.keyCode : event.charCode);
    if (autoComplete != 0) {
        if (key != 37 && key != 38 && key != 39 && key != 40) {
            // left, right, up, down: don't show autocomplete
            showAutoComplete();
        }
    }
    if (key == 13 && event.shiftKey) {
        // shift + return
        return false;
    }
    return true;
}

function setAutoComplete(value) {
    autoComplete = value;
    if (value == 0) {
        removeAutoComplete();
    } else {
        var s = lastList;
        lastList = '';
        showList(s);
    }
}

function setAutoSelect(value) {
    autoSelect = value;
}

function manualAutoComplete() {
    autoCompleteManual = true;
    lastQuery = null;
    lastList = '';
    showAutoCompleteNow();
}

function removeAutoComplete() {
    var table = getAutoCompleteTable();
    while (table.rows.length > 0) {
        table.deleteRow(0);
    }
    showOutput('');
}

function highlightRow(row) {
    if (row != null) {
        selectedRow = row;
    }
    var table = getAutoCompleteTable();
    highlightThisRow(table.rows[selectedRow]);
}

function highlightThisRow(row) {
    var table = getAutoCompleteTable();
    for(var i=0; i<table.rows.length; i++) {
        var r = table.rows[i];
        var col = (r == row) ? '#95beff' : '';
        var cells = r.cells;
        if (cells.length > 0) {
            cells[0].style.backgroundColor = col;
        }
    }
    showOutput('none');
}

function getAutoCompleteTable() {
    return parent.h2result.document.getElementById('h2auto');
}

function showOutput(x) {
//     parent.h2result.document.getElementById('output').style.display=x;
}

function showList(s) {
    if (lastList == s) {
        return;
    }
    lastList = s;
    var list = s.length == 0 ? null : s.split('|');
    var table = getAutoCompleteTable();
    if (table == null) {
        return;
    }
    while (table.rows.length > 0) {
        table.deleteRow(0);
    }
    selectedRow = 0;
    var count = 0;
    var doc = parent.h2result.document;
    var tbody = table.tBodies[0];
    for(var i=0; list != null && i<list.length; i++) {
        var kv = list[i].split('#');
        var type = kv[0];
        if (type > 0 && autoComplete != 2 && !autoCompleteManual) {
            continue;
        }
        var row = doc.createElement("tr");
        tbody.appendChild(row);
        var cell = doc.createElement("td");
        var key = kv[1];
        var value = kv[2];
        if (!key || !value) {
            break;
        }
        count++;
        cell.className = 'autoComp' + type;
        key = decodeURIComponent(key);
        row.onmouseover = function(){highlightThisRow(this)};
        if (!document.all || is_opera) {
            row.onclick = function(){insertText(this.cells[1].innerHTML);keyUp()};
        }
    //    addEvent(row, "click", function(e){var row=e?e.target:window.event.srcElement;alert(row);insertText(row.cells[1].innerHTML)});
//        addEvent(row, "mouseover", function(e){var row=e?e.target:window.event.srcElement;alert(row);highlightThisRow(row)});
//        addEvent(row, "click", function(e){var row=e?e.target:window.event.srcElement;alert(row);insertText(row.cells[1].innerHTML)});
//            addEvent(row, "mouseover", eval('function(){highlightRow('+i+')}'));
//            addEvent(row, "click", eval('function(){insertText(\''+value+'\')}'));
        var text = doc.createTextNode(key);
        cell.appendChild(text);
        row.appendChild(cell);
        cell = doc.createElement("td");
        cell.style.display='none';
        text = doc.createTextNode(value);
        cell.appendChild(text);
        row.appendChild(cell);
    }
    if (count > 0) {
        highlightRow();
        showOutput('none');
    } else {
        showOutput('');
    }
    // scroll to the top left
    parent.h2result.scrollTo(0, 0);
    autoCompleteManual = false;
}

function retrieveList(s) {
    if (s.length > 2000) {
        s = s.substring(s.length - 2000);
    }
    sendAsyncRequest('autoCompleteList.do?jsessionid=${sessionId}&query=' + encodeURIComponent(s));
}

function addEvent2(element, eventType, fn) {
    // cross-browser event handling for IE5+,  NS6 and Mozilla by Scott Andrew
    if (fn == null) {
        return;
    }
    if (element.addEventListener) {
        element.addEventListener(eventType, fn, true);
        return true;
    } else if (element.attachEvent){
        return element.attachEvent('on'+eventType, fn);
    } else {
        alert('Event handler could not be added');
    }
}

function sendAsyncRequest(url) {
    req = false;
    if (window.XMLHttpRequest) {
        try {
            req = new XMLHttpRequest();
        } catch(e) {
            req = false;
        }
    } else if (window.ActiveXObject) {
        try {
            req = new ActiveXObject("Msxml2.XMLHTTP");
        } catch(e) {
            try {
                req = new ActiveXObject("Microsoft.XMLHTTP");
            } catch(e) {
                req = false;
            }
        }
    }
    if (req) {
        req.onreadystatechange = processAsyncResponse;
        req.open("GET", url, true);
        req.send("");
    } else {
        var getList = document.getElementById('h2iframeTransport');
        getList.src = url;
    }
}

function processAsyncResponse() {
    if (req.readyState == 4) {
        if (req.status == 200) {
            showList(req.responseText);
        } else {
            // alert('Could not retrieve data');
        }
    }
}

function submitAll() {
    document.h2querysubmit.sql.value = document.h2query.sql.value;
    document.h2querysubmit.submit();
}

function submitSelected() {
    var field = document.h2query.sql;
    //alert('contents ' + field.selectionStart + '  ' + field.selectionEnd);
    if (field.selectionStart == field.selectionEnd) {
        if (autoSelect == 0) {
            return;
        }
        doAutoSelect();
        if (field.selectionStart == field.selectionEnd) {
            return;
        }
    }
    var startPos = field.selectionStart;
    var endPos = field.selectionEnd;
    var selectedText = field.value.substring(startPos, endPos);
    document.h2querysubmit.sql.value = selectedText;
    document.h2querysubmit.submit();
}

function doAutoSelect() {
    var field = document.h2query.sql;
    var position = field.selectionStart;
    try {
        var prevDoubleLine = field.value.lastIndexOf('\n\n',position - 1) + 2;
        if (prevDoubleLine == 1) prevDoubleLine = 0;
        var nextDoubleLine = field.value.indexOf('\n\n',position);
        if (nextDoubleLine == -1) nextDoubleLine = field.value.length;
        field.setSelectionRange(prevDoubleLine,nextDoubleLine);
    } catch (e) {
        field.selectionStart = field.selectionEnd = position;
    }
}
