package com.gitblit.wicket.panels;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;

import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.client.Translation;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.GitBlitWebSession;

public class SelectProjectPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final UserModel user = GitBlitWebSession.get().getUser();
	private String projectName;
	private boolean isCurrentlySelected;
	private Form<?> selectProjectForm;
	private String buttonLabel;
	
	public SelectProjectPanel(String wicketId, String projectName) {
		super(wicketId);
		setOutputMarkupId(true);
		
		if (user == null) {
			return;
		}
		
		this.projectName = projectName;
		this.isCurrentlySelected = user.hasSelectedProject(projectName);
		this.buttonLabel = isCurrentlySelected ? Translation.get("gb.unselectProject") : 
			Translation.get("gb.selectProject");
		
		selectProjectForm = new Form<Void>("selectProjectForm");
		AjaxButton button = new AjaxButton("selectProject", selectProjectForm) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				
				// Toggle selection status
				if (!isCurrentlySelected) {
					user.addSelectedProject(SelectProjectPanel.this.projectName);
				} else {
					user.removeSelectedProject(SelectProjectPanel.this.projectName);
				}
				try {					
					GitBlit.self().updateUserModel(user.getName(), user, false);
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				
				// Change the label on the button
				SelectProjectPanel.this.isCurrentlySelected = 
						SelectProjectPanel.this.isCurrentlySelected ? false : true;
				SelectProjectPanel.this.buttonLabel = SelectProjectPanel.this.isCurrentlySelected ? 
						Translation.get("gb.unselectProject") : Translation.get("gb.selectProject");
				this.addOrReplace(new Label("buttonLabel", buttonLabel).setEscapeModelStrings(false));
				target.addComponent(this);
			
				// If we unselected a project:
				if (!SelectProjectPanel.this.isCurrentlySelected) {
					String selectedButtonName = RepositoriesViewSelectorPanel.SELECTEDBUTTONNAME;
					String currentButtonId = this.getMarkupId();
					String groupRowSelector = "tr." + RepositoriesPanel.GROUPROWCSSCLASS;
					
					// Check whether we're using the 'View Selected Projects Only' view
					// If so, hide the group and its subprojects on this page
					target.appendJavascript(
				        String.format("if($('a[name=%s]').hasClass('active')){var groupRow=$('#%s').parents('%s');groupRow.hide();"+
			        		"groupRow.nextUntil('%s').hide();}", selectedButtonName, currentButtonId, groupRowSelector, groupRowSelector)
			        );
				}
			}
		};
		
		button.add(new Label("buttonLabel", buttonLabel).setEscapeModelStrings(false));
		button.setOutputMarkupId(true);
		selectProjectForm.add(button);
		add(selectProjectForm);
	}

}
