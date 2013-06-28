// Extended Tooltip Javascript
// copyright 9th August 2002, 3rd July 2005, 24th August 2008
// by Stephen Chapman, Felgall Pty Ltd

// permission is granted to use this javascript provided that the below code is not altered
function pw() {
    return window.innerWidth ||
        document.documentElement.clientWidth ||
        document.body.clientWidth
}
;
function mouseX(evt) {
    return evt.clientX ? evt.clientX + (document.documentElement.scrollLeft || document.body.scrollLeft) : evt.pageX;
}
function mouseY(evt) {
    return evt.clientY ? evt.clientY + (document.documentElement.scrollTop || document.body.scrollTop) : evt.pageY
}

function popUp(evt, oi) {
    if (document.getElementById) {
        var wp = pw();
        dm = document.getElementById(oi);
        ds = dm.style;
        st = ds.visibility;
        if (dm.offsetWidth)
            ew = dm.offsetWidth;
        else if (dm.clip.width)
            ew = dm.clip.width;
        if (st == "visible" || st == "show") {
            ds.visibility = "hidden";
        } else {
            tv = mouseY(evt) + 20;
            lv = mouseX(evt) - (ew / 4);
            if (lv < 2) lv = 2;
            else if (lv + ew > wp) lv -= ew / 2;
            lv += 'px';
            tv += 'px';
            ds.left = lv;
            ds.top = tv;
            ds.visibility = "visible";
        }
    }
}

function popXUp(x, y, oi) {
    if (document.getElementById) {
        var wp = pw();
        dm = document.getElementById(oi);
        ds = dm.style;
        st = ds.visibility;
        if (dm.offsetWidth)
            ew = dm.offsetWidth;
        else if (dm.clip.width)
            ew = dm.clip.width;
        if (st == "visible" || st == "show") {
            ds.visibility = "hidden";
        } else {
            tv = x + 20;
            lv = y - (ew / 4);
            if (lv < 2) lv = 2;
            else if (lv + ew > wp) lv -= ew / 2;
            lv += 'px';
            tv += 'px';
            ds.left = lv;
            ds.top = tv;
            ds.visibility = "visible";
        }
    }

}




