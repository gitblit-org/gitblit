/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.manager;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

public interface IFederationManager extends IManager {

	/**
	 * Returns the path of the proposals folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 *
	 * @return the proposals folder path
	 * @since 1.4.0
	 */
	File getProposalsFolder();

	boolean canFederate();

	/**
	 * Returns the federation user account.
	 *
	 * @return the federation user account
	 * @since 1.4.0
	 */
	UserModel getFederationUser();

	/**
	 * Try to authenticate request as the Federation user.
	 *
	 * @param httpRequest
	 * @return the federation user, if authenticated
	 * @since 1.4.0
	 */
	UserModel authenticate(HttpServletRequest httpRequest);

	/**
	 * Returns the list of federated gitblit instances that this instance will
	 * try to pull.
	 *
	 * @return list of registered gitblit instances
	 * @since 1.4.0
	 */
	List<FederationModel> getFederationRegistrations();

	/**
	 * Retrieve the specified federation registration.
	 *
	 * @param name
	 *            the name of the registration
	 * @return a federation registration
	 * @since 1.4.0
	 */
	FederationModel getFederationRegistration(String url, String name);

	/**
	 * Returns the list of federation sets.
	 *
	 * @return list of federation sets
	 * @since 1.4.0
	 */
	List<FederationSet> getFederationSets(String gitblitUrl);

	/**
	 * Returns the list of possible federation tokens for this Gitblit instance.
	 *
	 * @return list of federation tokens
	 * @since 1.4.0
	 */
	List<String> getFederationTokens();

	/**
	 * Returns the specified federation token for this Gitblit instance.
	 *
	 * @param type
	 * @return a federation token
	 * @since 1.4.0
	 */
	String getFederationToken(FederationToken type);

	/**
	 * Returns the specified federation token for this Gitblit instance.
	 *
	 * @param value
	 * @return a federation token
	 * @since 1.4.0
	 */
	String getFederationToken(String value);

	/**
	 * Compares the provided token with this Gitblit instance's tokens and
	 * determines if the requested permission may be granted to the token.
	 *
	 * @param req
	 * @param token
	 * @return true if the request can be executed
	 * @since 1.4.0
	 */
	boolean validateFederationRequest(FederationRequest req, String token);

	/**
	 * Acknowledge and cache the status of a remote Gitblit instance.
	 *
	 * @param identification
	 *            the identification of the pulling Gitblit instance
	 * @param registration
	 *            the registration from the pulling Gitblit instance
	 * @return true if acknowledged
	 * @since 1.4.0
	 */
	boolean acknowledgeFederationStatus(String identification, FederationModel registration);

	/**
	 * Returns the list of registration results.
	 *
	 * @return the list of registration results
	 * @since 1.4.0
	 */
	List<FederationModel> getFederationResultRegistrations();

	/**
	 * Submit a federation proposal. The proposal is cached locally and the
	 * Gitblit administrator(s) are notified via email.
	 *
	 * @param proposal
	 *            the proposal
	 * @param gitblitUrl
	 *            the url of your gitblit instance to send an email to
	 *            administrators
	 * @return true if the proposal was submitted
	 * @since 1.4.0
	 */
	boolean submitFederationProposal(FederationProposal proposal, String gitblitUrl);

	/**
	 * Returns the list of pending federation proposals
	 *
	 * @return list of federation proposals
	 * @since 1.4.0
	 */
	List<FederationProposal> getPendingFederationProposals();

	/**
	 * Get repositories for the specified token.
	 *
	 * @param gitblitUrl
	 *            the base url of this gitblit instance
	 * @param token
	 *            the federation token
	 * @return a map of <cloneurl, RepositoryModel>
	 * @since 1.4.0
	 */
	Map<String, RepositoryModel> getRepositories(String gitblitUrl, String token);

	/**
	 * Creates a proposal from the token.
	 *
	 * @param gitblitUrl
	 *            the url of this Gitblit instance
	 * @param token
	 * @return a potential proposal
	 * @since 1.4.0
	 */
	FederationProposal createFederationProposal(String gitblitUrl, String token);

	/**
	 * Returns the proposal identified by the supplied token.
	 *
	 * @param token
	 * @return the specified proposal or null
	 * @since 1.4.0
	 */
	FederationProposal getPendingFederationProposal(String token);

	/**
	 * Deletes a pending federation proposal.
	 *
	 * @param a
	 *            proposal
	 * @return true if the proposal was deleted
	 * @since 1.4.0
	 */
	boolean deletePendingFederationProposal(FederationProposal proposal);

}