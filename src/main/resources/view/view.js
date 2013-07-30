var view;
(function(){
  "use strict";  
  view = {
    enableFlash : function() {
      if (swfobject.hasFlashPlayerVersion('9.0.0')) {
        $('html').addClass('has-flash');
      }
    },
    disableResponsiveView: function() {
      $('.view-body .hidden-phone').removeClass('hidden-phone');
      $('.view-body .hidden-tablet').removeClass('hidden-tablet');
    }
  };
} ());