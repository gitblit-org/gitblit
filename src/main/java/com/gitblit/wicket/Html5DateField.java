package com.gitblit.wicket;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.wicket.Session;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.AbstractTextComponent.ITextFormatProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.convert.IConverter;
import org.apache.wicket.util.convert.converter.DateConverter;

public class Html5DateField extends TextField<Date> implements ITextFormatProvider {

	private static final long serialVersionUID = 1L;

	private static final String DEFAULT_PATTERN = "MM/dd/yyyy";

	private String datePattern = null;

	private IConverter converter = null;

	/**
	 * Creates a new Html5DateField, without a specified pattern. This is the same as calling
	 * <code>new Html5DateField(id, Date.class)</code>
	 * 
	 * @param id
	 *            The id of the date field
	 */
	public Html5DateField(String id)
	{
		this(id, null, defaultDatePattern());
	}

	/**
	 * Creates a new Html5DateField, without a specified pattern. This is the same as calling
	 * <code>new Html5DateField(id, object, Date.class)</code>
	 * 
	 * @param id
	 *            The id of the date field
	 * @param model
	 *            The model
	 */
	public Html5DateField(String id, IModel<Date> model)
	{
		this(id, model, defaultDatePattern());
	}

	/**
	 * Creates a new Html5DateField bound with a specific <code>SimpleDateFormat</code> pattern.
	 * 
	 * @param id
	 *            The id of the date field
	 * @param datePattern
	 *            A <code>SimpleDateFormat</code> pattern
	 * 
	 */
	public Html5DateField(String id, String datePattern)
	{
		this(id, null, datePattern);
	}

	/**
	 * Creates a new DateTextField bound with a specific <code>SimpleDateFormat</code> pattern.
	 * 
	 * @param id
	 *            The id of the date field
	 * @param model
	 *            The model
	 * @param datePattern
	 *            A <code>SimpleDateFormat</code> pattern
	 */
	public Html5DateField(String id, IModel<Date> model, String datePattern)
	{
		super(id, model, Date.class);
		this.datePattern = datePattern;
		converter = new DateConverter()
		{
			private static final long serialVersionUID = 1L;

			/**
			 * @see org.apache.wicket.util.convert.converters.DateConverter#getDateFormat(java.util.Locale)
			 */
			@Override
			public DateFormat getDateFormat(Locale locale)
			{
				if (locale == null)
				{
					locale = Locale.getDefault();
				}
				return new SimpleDateFormat(Html5DateField.this.datePattern, locale);
			}
		};
	}

	/**
	 * Returns the default converter if created without pattern; otherwise it returns a
	 * pattern-specific converter.
	 * 
	 * @param type
	 *            The type for which the converter should work
	 * 
	 * @return A pattern-specific converter
	 */
	@Override
	public IConverter getConverter(Class<?> type)
	{
		if (converter == null)
		{
			return super.getConverter(type);
		}
		else
		{
			return converter;
		}
	}

	/**
	 * Returns the date pattern.
	 * 
	 * @see org.apache.wicket.markup.html.form.AbstractTextComponent.ITextFormatProvider#getTextFormat()
	 */
	public String getTextFormat()
	{
		return datePattern;
	}

	/**
	 * Try to get datePattern from user session locale. If it is not possible, it will return
	 * {@link #DEFAULT_PATTERN}
	 * 
	 * @return date pattern
	 */
	private static String defaultDatePattern()
	{
		Locale locale = Session.get().getLocale();
		if (locale != null)
		{
			DateFormat format = DateFormat.getDateInstance(DateFormat.SHORT, locale);
			if (format instanceof SimpleDateFormat)
			{
				return ((SimpleDateFormat)format).toPattern();
			}
		}
		return DEFAULT_PATTERN;
	}
	
	@Override
	protected String getInputType()
	{
		return "date";
	}
	
}