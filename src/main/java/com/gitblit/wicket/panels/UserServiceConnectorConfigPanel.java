package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Set;

import org.apache.wicket.markup.html.form.Check;
import org.apache.wicket.markup.html.form.CheckGroup;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SimpleFormComponentLabel;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.PropertyModel;

public class UserServiceConnectorConfigPanel extends BasePanel {

	private static final long serialVersionUID = -8150990049317770448L;

	
	public UserServiceConnectorConfigPanel(String wicketId,
			Set<String> connectorParams, Form form) {
		super(wicketId);
		
		ArrayList<String> params = new ArrayList<String>();		
		for (String string : connectorParams) {
			params.add(string);
		}
		CheckGroup<String> checks = new CheckGroup<String>("numbersCheckGroup");
        form.add(checks);
        ListView<String> checksList = new ListView<String>("numbers", params)
        {
           private static final long serialVersionUID = -7342400677052405658L;

			@Override
            protected void populateItem(ListItem<String> item)
            {
                Check<String> check = new Check<String>("check", item.getModel());
                check.setLabel(item.getModel());
                item.add(check);
                item.add(new SimpleFormComponentLabel("number", check));
            }
        }.setReuseItems(true);
        checks.add(checksList);
		
//		ListView<String> paramValueList = new LinesListView("paramValueList");
//		add(paramValueList);
//		ListView<String> paramValueList = new ListView<String>("paramValueList",params) {
//		
//
//			
//			private static final long serialVersionUID = 1L;
//			
//			@Override
//			protected void populateItem(ListItem<String> item) {
//				// TODO Auto-generated method stub
//					System.err.println("SHB populated");
//				 item.add(new TextField<String>("lineEdit", new PropertyModel<String>(
//			                item.getDefaultModel(), "text")));
//				 setReuseItems(true);
//			}
//			
//		};
//		System.err.println("SHB list size: "+paramValueList.size());
//		add(paramValueList);
				
//		for (String string : connectorParams) {
//			add(new Label(string, string));
//		}
	}

	


	private static final class LinesListView extends ListView<String>
	    {
		 private static final long serialVersionUID = -2265482722513128322L;

			/**
	         * Construct.
	         * 
	         * @param id
	         */
	        public LinesListView(String id)
	        {
	            super(id);
	            // always do this in forms!
	            setReuseItems(true);
	        }

	        @Override
	        protected void populateItem(ListItem<String> item)
	        {
	            // add a text field that works on each list item model (returns
	            // objects of
	            // type FormInputModel.Line) using property text.
	            item.add(new TextField<String>("lineEdit", new PropertyModel<String>(
	                item.getDefaultModel(), "text")));
	        }
	    }

}
