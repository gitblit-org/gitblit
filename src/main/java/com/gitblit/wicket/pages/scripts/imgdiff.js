/*
 * Copyright 2014 Tom <tw201207@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function($) {

/**
 * Sets up elem as a slider; returns an access object. Elem must be positioned!
 * Note that the element may contain other elements; this is used for instance
 * for the image diff overlay slider.
 *
 * The styling of the slider is to be done in CSS. Currently recognized options:
 * - initial: <float> clipped to [0..1], default 0
 * - handleClass: <string> to assign to the handle div element created.
 * If no handleClass is specified, a very plain default style is assigned.
 */
function rangeSlider(elem, options) {
	options = $.extend({ initial : 0 }, options || {});
	options.initial = Math.min(1.0, Math.max(0, options.initial));
	
	var $elem = $(elem);
	var $handle = $('<div></div>').css({ position: 'absolute', left: 0, cursor: 'ew-resize' });
	var $root = $(document.documentElement);
	var $doc = $(document);	
	var lastRatio = options.initial;

	/** Mousemove event handler to track the mouse and move the slider. Generates slider:pos events. */
	function track(e) {
		var pos = $elem.offset().left;
		var width = $elem.width();
		var handleWidth = $handle.width();
		var range = width - handleWidth;
		if (range <= 0) return;
		var delta = Math.min(range, Math.max (0, e.pageX - pos - handleWidth / 2));
		lastRatio = delta / range;
		$handle.css('left', "" + (delta * 100 / width) + '%');
		$elem.trigger('slider:pos', { ratio: lastRatio, handle: $handle[0] });
	}

	/** Mouseup event handler to stop mouse tracking. */
	function end(e) {
		$doc.off('mousemove', track);
		$doc.off('mouseup', end);
		$root.removeClass('no-select');
	}

    /** Snaps the slider to the given ratio and generates a slider:pos event with the new ratio. */
	function setTo(ratio) {
		var w = $elem.width();
		if (w <= 0 || $elem.is(':hidden')) return;
		lastRatio = Math.min( 1.0, Math.max(0, ratio));
		$handle.css('left', "" + Math.max(0, 100 * (lastRatio * (w - $handle.width())) / w) + '%');
		$elem.trigger('slider:pos', { ratio: lastRatio, handle: $handle[0] });
	}
	
	/**
	 * Moves the slider to the given ratio, clipped to [0..1], in duration milliseconds.
	 * Generates slider:pos events during the animation. If duration === 0, same as setTo.
	 * Default duration is 500ms.
	 */
	function moveTo(ratio, duration) {
		ratio = Math.min(1.0, Math.max(0, ratio));
		if (ratio === lastRatio) return;
		if (typeof duration == 'undefined') duration = 500;
		if (duration === 0) {
			setTo(ratio);
		} else {
			var target = ratio * ($elem.width() - $handle.width());
			if (ratio > lastRatio) target--; else target++;
			$handle.stop().animate({left: target},
				{ 'duration' : duration,
				  'step' : function() {
						lastRatio = Math.min(1.0, Math.max(0, $handle.position().left / ($elem.width() - $handle.width())));
						$elem.trigger('slider:pos', { ratio : lastRatio, handle : $handle[0] });
					},
				  'complete' : function() { setTo(ratio); } // Last step gives us a % value again.
				}
			);
		}
	}
	
	/** Returns the current ratio. */
	function getValue() {
		return lastRatio;
	}
		
	$elem.append($handle);
	if (options.handleClass) {
		$handle.addClass(options.handleClass);
	} else { // Provide a default style so that it is at least visible
		$handle.css({ width: '10px', height: '10px', background: 'white', border: '1px solid black' });
	}
	if (options.initial) setTo(options.initial);

	/** Install mousedown handler to start mouse tracking. */
	$handle.on('mousedown', function(e) {
		$root.addClass('no-select');
		$doc.on('mousemove', track);
		$doc.on('mouseup', end);
	});

	return { setRatio: setTo, moveRatio: moveTo, getRatio: getValue, handle: $handle[0] };
}

function setup() {
	$('.imgdiff-container').each(function() {
		var $this = $(this);
		var $overlaySlider = $this.find('.imgdiff-ovr-slider').first();
		var $opacitySlider = $this.find('.imgdiff-opa-slider').first();
		var overlayAccess = rangeSlider($overlaySlider, {handleClass: 'imgdiff-ovr-handle'});
		rangeSlider($opacitySlider, {handleClass: 'imgdiff-opa-handle'});
		var $img = $('#' + this.id.substr(this.id.indexOf('-')+1)); // Here we change opacity
		var $div = $img.parent(); // This controls visibility: here we change width.
		
		$overlaySlider.on('slider:pos', function(e, data) {
			var pos = $(data.handle).offset().left;
			var imgLeft = $img.offset().left; // Global
			var imgW = $img.outerWidth();
			var imgOff = $img.position().left; // From left edge of $div
			if (pos <= imgLeft) {
				$div.width(0);
			} else if (pos <= imgLeft + imgW) {
				$div.width(pos - imgLeft + imgOff);
			} else if ($div.width() < imgW + imgOff) {
				$div.width(imgW + imgOff);
			}
		});
		$opacitySlider.on('slider:pos', function(e, data) {
			if ($div.width() <= 0) overlayAccess.moveRatio(1.0, 500); // Make old image visible in a nice way
			$img.css('opacity', 1.0 - data.ratio);
		});
	});
}

$(setup); // Run on jQuery's dom-ready

})(jQuery);