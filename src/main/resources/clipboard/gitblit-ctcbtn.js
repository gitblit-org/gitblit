// Instantiate the clipboarding
var clipboard = new ClipboardJS('.ctcbtn');

clipboard.on('success', function (e) {
  showTooltip(e.trigger, "Copied!");
});

clipboard.on('error', function (e) {
  showTooltip(e.trigger, fallbackMessage(e.action));
});

// Attach events to buttons to clear tooltip again
var btns = document.querySelectorAll('.ctcbtn');
for (var i = 0; i < btns.length; i++) {
  btns[i].addEventListener('mouseleave', clearTooltip);
  btns[i].addEventListener('blur', clearTooltip);
}


function findTooltipped(elem) {
  do {
    if (elem.classList.contains('tooltipped')) return elem;
    elem = elem.parentElement;
  } while (elem != null);
  return null;
}

// Show or hide tooltip by setting the tooltipped-active class
// on a parent that contains tooltipped. Since the copy button
// could be and image, or be hidden after clicking, the tooltipped
// element might be higher in the hierarchy.
var ttset;
function showTooltip(elem, msg) {
  let ttelem = findTooltipped(elem);
  if (ttelem != null) {
    ttelem.classList.add('tooltipped-active');
    ttelem.setAttribute('data-tt-text', msg);
    ttset=Date.now();
  }
  else {
    console.warn("Could not find any tooltipped element for clipboard button.", elem);
  }
}

function clearTooltip(e) {
  let ttelem = findTooltipped(e.currentTarget);
  if (ttelem != null) {
    let now = Date.now();
    if (now - ttset < 500) {
        // Give the tooltip some time to display
        setTimeout(function(){ttelem.classList.remove('tooltipped-active')}, 1000)
    }
    else {
        ttelem.classList.remove('tooltipped-active');
    }
  }
  else {
    console.warn("Could not find any tooltipped element for clipboard button.", e.currentTarget);
  }
}

// If the API is not supported, at least fall back to a message saying
// that now that the text is selected, Ctrl-C can be used.
// This is still a problem in the repo URL dropdown. When it is hidden, Ctrl-C doesn't work.
function fallbackMessage(action) {
  var actionMsg = "";
  if (/Mac/i.test(navigator.userAgent)) {
    actionMsg = "Press ⌘-C to copy";
  }
  else {
    actionMsg = "Press Ctrl-C to copy";
  }
  return actionMsg;
}
