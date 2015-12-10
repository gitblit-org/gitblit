//This provides a basic patch/hack to allow Wicket 1.4 to support HTML5 input types

Wicket.Form.serializeInput_original = Wicket.Form.serializeInput;

Wicket.Form.serializeInput = function(input)
{
	if (input.type.toLowerCase() == "date")
	{
		return Wicket.Form.encode(input.name) + "=" + Wicket.Form.encode(input.value) + "&";
	}
	
	return Wicket.Form.serializeInput_original(input);
}
