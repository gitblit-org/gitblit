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
		var width = $elem.innerWidth();
		var handleWidth = $handle.outerWidth(false);
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
		var w = $elem.innerWidth();
		if (w <= 0 || $elem.is(':hidden')) return;
		lastRatio = Math.min( 1.0, Math.max(0, ratio));
		$handle.css('left', "" + Math.max(0, 100 * (lastRatio * (w - $handle.outerWidth(false))) / w) + '%');
		$elem.trigger('slider:pos', { ratio: lastRatio, handle: $handle[0] });
	}
	
	/**
	 * Moves the slider to the given ratio, clipped to [0..1], in duration milliseconds.
	 * Generates slider:pos events during the animation. If duration <= 30, same as setTo.
	 * Default duration is 500ms. If a callback is given, it's called once the animation
	 * has completed.
	 */
	function moveTo(ratio, duration, callback) {
		ratio = Math.min(1.0, Math.max(0, ratio));
		if (ratio === lastRatio) {
			if (typeof callback == 'function') callback();
			return;
		}
		if (typeof duration == 'undefined') duration = 500;
		if (duration <= 30) {
			 // Cinema is 24 or 48 frames/sec, so 20-40ms per frame. Makes no sense to animate for such a short duration.
			setTo(ratio);
			if (typeof callback == 'function') callback();
		} else {
			var target = ratio * ($elem.innerWidth() - $handle.outerWidth(false));
			if (ratio > lastRatio) target--; else target++;
			$handle.stop().animate({left: target},
				{ 'duration' : duration,
				  'step' : function() {
						lastRatio = Math.min(1.0, Math.max(0, $handle.position().left / ($elem.innerWidth() - $handle.outerWidth(false))));
						$elem.trigger('slider:pos', { ratio : lastRatio, handle : $handle[0] });
					},
				  'complete' : function() { setTo(ratio); if (typeof callback == 'function') callback(); } // Ensure we have again a % value
				}
			);
		}
	}
	
	/**
	 * As moveTo, but determines an appropriate duration in the range [0..maxDuration] on its own,
	 * depending on the distance the handle would move. If no maxDuration is given it defaults
	 * to 1500ms.
	 */
	function moveAuto(ratio, maxDuration, callback) {
		if (typeof maxDuration == 'undefined') maxDuration = 1500;
		var delta = ratio - lastRatio;
		if (delta < 0) delta = -delta;
		var speed = $elem.innerWidth() * delta * 2;
		if (speed > maxDuration) speed = maxDuration;
		moveTo(ratio, speed, callback);
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
		e.stopPropagation();
		e.preventDefault();
	});

	return { setRatio: setTo, moveRatio: moveTo, 'moveAuto': moveAuto, getRatio: getValue, handle: $handle[0] };
}

function setup() {
	$('.imgdiff-container').each(function() {
		var $this = $(this);
		var $overlaySlider = $this.find('.imgdiff-ovr-slider').first();
		var $opacitySlider = $this.find('.imgdiff-opa-slider').first();
		var overlayAccess = rangeSlider($overlaySlider, {handleClass: 'imgdiff-ovr-handle'});
		var opacityAccess = rangeSlider($opacitySlider, {handleClass: 'imgdiff-opa-handle'});
		var $img = $('#' + this.id.substr(this.id.indexOf('-')+1)); // Here we change opacity
		var $div = $img.parent(); // This controls visibility: here we change width.
		
		$overlaySlider.on('slider:pos', function(e, data) {
			var pos = $(data.handle).offset().left;
			var imgLeft = $img.offset().left; // Global
			var imgW = $img.outerWidth(true);
			var imgOff = $img.position().left; // From left edge of $div
			if (pos <= imgLeft) {
				$div.width(0);
			} else if (pos <= imgLeft + imgW) {
				$div.width(pos - imgLeft + imgOff);
			} else if ($div.width() < imgW + imgOff) {
				$div.width(imgW + imgOff);
			}
		});
		$overlaySlider.css('cursor', 'pointer');
		$overlaySlider.on('mousedown', function(e) {
			var newRatio = (e.pageX - $overlaySlider.offset().left) / $overlaySlider.innerWidth();
			var oldRatio = overlayAccess.getRatio();
			if (newRatio !== oldRatio) {
				overlayAccess.moveAuto(newRatio);
			}
		});
		$opacitySlider.on('slider:pos', function(e, data) {
			if ($div.width() <= 0) overlayAccess.moveAuto(1.0); // Make old image visible in a nice way
			$img.css('opacity', 1.0 - data.ratio);
		});
		$opacitySlider.css('cursor', 'pointer');
		$opacitySlider.on('mousedown', function(e) {
			var newRatio = (e.pageX - $opacitySlider.offset().left) / $opacitySlider.innerWidth();
			var oldRatio = opacityAccess.getRatio();
			if (newRatio !== oldRatio) {
				if ($div.width() <= 0) {
					overlayAccess.moveRatio(1.0, 500, function() {opacityAccess.moveAuto(newRatio);}); // Make old image visible in a nice way
				} else {
					opacityAccess.moveAuto(newRatio)
				}
			}
			e.preventDefault();
		});
			
	});
}

$(setup); // Run on jQuery's dom-ready

})(jQuery);