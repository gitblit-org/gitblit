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
jQuery(function () {
	// Runs on jQuery's document.ready and sets up the scroll event handlers for all image diffs.
	jQuery(".imgdiff-slider-resizeable").each(function () {
		var $el = jQuery(this);
		var $img = jQuery('#' + this.id.substr(this.id.indexOf('-') + 1));
		function fade() {
			var w = Math.max(0, $el.width() - 18); // Must correspond to CSS: 18 px is handle width, 400 px is slider width
			w = Math.max(0, 1.0 - w / 400.0);
			$img.css("opacity", w);
		}
		// Unfortunately, not even jQuery triggers resize events for our resizeable... so let's track the mouse.
		$el.on('mousedown', function() { $el.on('mousemove', fade); });
		$el.on('mouseup', function() { $el.off('mousemove', fade); fade(); });
	});
});
