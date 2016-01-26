//Provide access to the editors using the standard names
CodeMirror = require('CodeMirror');
SimpleMDE = require('SimpleMDE');

attachDocumentEditor = function (editorElement, commitDialogElement)
	{
		return new SimpleMDE(
			{ 
				autoDownloadFontAwesome:false,
				element: editorElement,
				spellChecker:false,
				toolbar: [
					{
						name: 'custom',
						action: function(){ simplemde.codemirror.save(); commitDialogElement.modal({show:true});},
						className: 'fa fa-save',
						title: 'Save & Commit Changes'
					},
					"|",
					"heading-1",
					"heading-2",
					"heading-3",
					"|",
					"bold",
					"italic",
					"strikethrough",
					"|",
					"quote",
					"ordered-list",
					"unordered-list",
					"|",
					"link",
					"image",
					"|",
					"preview",
					"side-by-side",
					"fullscreen"
					]
			});

	}

