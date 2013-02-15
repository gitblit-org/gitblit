package com.gitblit.wicket.panels;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.form.Form;

import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class RepositoriesViewSelectorPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	
	public static final String SELECTEDBUTTONNAME = "showSelected";

	public enum Show {
		all, selected;
	}
	
	private Show activeState = Show.all;
	
	private final UserModel user = GitBlitWebSession.get().getUser();
	
	public RepositoriesViewSelectorPanel(String wicketId) {
		super(wicketId);
		setMarkupId(wicketId);
		setOutputMarkupId(true);
		
		if (user == null) {
			return;
		}
		
		/*
		 * Set the active state to the user's preferred setting.
		 */
		if (user.showSelectedProjectsOnly) {
			activeState = Show.selected;
		}

		/*
		 * Selected view toggle buttons
		 */
		Form<?> repoViewToggleForm = new Form<Void>("repoViewSelectorToggleForm");
		repoViewToggleForm.add(new ShowStateButton("showAll", Show.all));
		repoViewToggleForm.add(new ShowStateButton(SELECTEDBUTTONNAME, Show.selected));
		add(repoViewToggleForm);
	}
	
	private class ShowStateButton extends AjaxButton {
		private static final long serialVersionUID = 1L;

		Show buttonState;
		
		public ShowStateButton(String wicketId, Show state) {
			super(wicketId);
			this.buttonState = state;
			setOutputMarkupId(true);
		}
		
		@Override
		protected void onBeforeRender()
		{
			String cssClass = "btn";
			if (buttonState.equals(RepositoriesViewSelectorPanel.this.activeState)) {
				cssClass = "btn btn-info active";
			}
			WicketUtils.setCssClass(this, cssClass);
			super.onBeforeRender();
		}
		
		@Override
		protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
			RepositoriesViewSelectorPanel.this.activeState = buttonState;
			RepositoriesViewSelectorPanel.this.user.showSelectedProjectsOnly = buttonState.equals(Show.selected);
			try {					
				GitBlit.self().updateUserModel(user.getName(), user, false);
			} catch (GitBlitException e) {
				error(e.getMessage());
				return;
			}
			setResponsePage(this.getPage().getClass(), this.getPage().getPageParameters());
		}
	};

}
