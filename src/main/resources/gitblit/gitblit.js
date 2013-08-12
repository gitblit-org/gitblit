if (typeof Gitblit !== 'object') {
  Gitblit = {
    view : {},
    repository : {}
  };
  
  Gitblit.view.enableFlash = function() {
    if (swfobject.hasFlashPlayerVersion('9.0.0')) {
      $('html').addClass('has-flash');
    }
  };
  
  Gitblit.repository.selectUrl = function(selector) {
    var node = $(selector);
    node.click(function() {
      node.focus();
      node.select();
    });
  };
}