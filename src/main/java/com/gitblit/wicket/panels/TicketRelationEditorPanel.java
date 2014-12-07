package com.gitblit.wicket.panels;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.jsoup.helper.StringUtil;

import com.gitblit.models.RepositoryModel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.TicketsPage;

public class TicketRelationEditorPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	
	private IModel<List<String>> dependenciesModel;
	private IModel<String> addDependencyModel;

	
	public TicketRelationEditorPanel(String wicketId, IModel<List<String>> pdependenciesModel, final RepositoryModel repositoryModel) {
		super(wicketId);
		this.dependenciesModel = pdependenciesModel;
		this.addDependencyModel = Model.of();
		
		
		add(new ListView<String>("dependencyList", dependenciesModel) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<String> item) {
				final String ticketId = item.getModelObject();

				PageParameters tp = WicketUtils.newObjectParameter(repositoryModel.name, ticketId);
				item.add(new LinkPanel("dependencyLink", "list subject", "#"+ticketId, TicketsPage.class, tp));
				
				item.add(new AjaxButton("removeDependencyLink") {
					private static final long serialVersionUID = 1L;
					@Override
					public void onSubmit(AjaxRequestTarget target, Form<?> form) {
						List<String> list = dependenciesModel.getObject();
						list.remove(ticketId);
						dependenciesModel.setObject(list);
						target.addComponent(form);
					}
				});
			}
		});
		add(new TextField<String>("addDependencyText", addDependencyModel));
		add(new AjaxButton("addDependency") {
			private static final long serialVersionUID = 1L;
			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				String ticketIdStr = addDependencyModel.getObject();
				if (!StringUtil.isBlank(ticketIdStr)) {
					try {
						long ticketId = Long.parseLong(ticketIdStr);
						if (app().tickets().hasTicket(repositoryModel, ticketId)) {
							List<String> list = (List<String>) dependenciesModel.getObject();
							list.add(String.valueOf(ticketId));
							addDependencyModel.setObject("");
						}
					} catch (NumberFormatException e) {
						// TODO : not allowed
						
					}
				}
				target.addComponent(form);
			}
		});
		
		
	}

}
