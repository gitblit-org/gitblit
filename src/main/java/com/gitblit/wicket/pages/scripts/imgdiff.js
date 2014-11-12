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
	jQuery(".imgdiff-slider").scroll(function() {
		var w = 1.0 - (this.scrollLeft / (this.scrollWidth - (this.clientWidth || this.offsetWidth)));
		// We encode the target img id in the slider's id: slider-imgdiffNNN.
		jQuery('#' + this.id.substr(this.id.indexOf('-') + 1)).css("opacity", w);
	})
});
