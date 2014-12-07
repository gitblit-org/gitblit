package com.gitblit.wicket.panels;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.gitblit.models.TicketModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.TicketsPage;

public abstract class TicketRelationEditorPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	
	private IModel<List<String>> dependenciesModel;
	private IModel<String> addDependencyModel;
	private Long baseTicketId;
	
	public TicketRelationEditorPanel(String wicketId, IModel<List<String>> pdependenciesModel, Long baseTicketId) {
		super(wicketId);
		this.dependenciesModel = pdependenciesModel;
		this.addDependencyModel = Model.of();
		this.baseTicketId = baseTicketId;
		
		
		add(new ListView<String>("dependencyList", dependenciesModel) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<String> item) {
				final String ticketId = item.getModelObject();

				PageParameters tp = WicketUtils.newObjectParameter(getRepositoryModel().name, ticketId);
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
					if (checkCycle(ticketIdStr)) {
						List<String> list = (List<String>) dependenciesModel.getObject();
						list.add(ticketIdStr.trim());
						addDependencyModel.setObject("");
					}
				}
				target.addComponent(form);
			}
		});
	}
	
	private boolean checkCycle(String ticketId) {
		Set<Long> tickets = new HashSet<Long>();
		if (baseTicketId != null) {
			tickets.add(baseTicketId);
		}
		return checkCycle(tickets, ticketId);
	}

	private boolean checkCycle(Set<Long> tickets, String ticketIdStr) {
		try {
			long ticketId = Long.parseLong(ticketIdStr);
			if (tickets.contains(ticketId)) {
				return false;
			}
			ITicketService ticketService = app().tickets();
			RepositoryModel r = getRepositoryModel();
			if (ticketService.hasTicket(r, ticketId)) {
				TicketModel ticket = ticketService.getTicket(r, ticketId);
				tickets.add(ticketId);
				for (String subTicketIdStr : ticket.getDependencies()) {
					if (!checkCycle(tickets, subTicketIdStr)) {
						return false;
					}
				}
				tickets.remove(ticketId);
				return true;
			} else {
				return false;
			}
		} catch (NumberFormatException e) {
			return false;
		}
	}	
	
	protected abstract RepositoryModel getRepositoryModel();

}
