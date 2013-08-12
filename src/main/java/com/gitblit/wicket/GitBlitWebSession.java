/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.wicket;

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.wicket.Page;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RedirectToUrlException;
import org.apache.wicket.Request;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.protocol.http.WebRequestCycle;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;

import com.gitblit.Constants.AuthenticationType;
import com.gitblit.models.UserModel;

public final class GitBlitWebSession extends WebSession {

	private static final long serialVersionUID = 1L;

	/**
	 * Pattern has been modified from public domain code located at <a href="http://detectmobilebrowsers.com/">http://detectmobilebrowsers.com/</a>.
	 * 
	 * Unlicense license can be view at <a href="http://detectmobilebrowsers.com/about">here</a>.
	 */
	private final static Pattern MOBILE_USER_DEVICE = Pattern.compile("(?i).*((android|bb\\d+|meego).+mobile|avantgo|bada\\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\\.(browser|link)|vodafone|wap|windows (ce|phone)|xda|xiino).*", Pattern.CASE_INSENSITIVE);
	private final static Pattern MOBILE_USER_AGENT = Pattern.compile("(?i)1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\\-(n|u)|c55\\/|capi|ccwa|cdm\\-|cell|chtm|cldc|cmd\\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\\-s|devi|dica|dmob|do(c|p)o|ds(12|\\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\\-|_)|g1 u|g560|gene|gf\\-5|g\\-mo|go(\\.w|od)|gr(ad|un)|haie|hcit|hd\\-(m|p|t)|hei\\-|hi(pt|ta)|hp( i|ip)|hs\\-c|ht(c(\\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\\-(20|go|ma)|i230|iac( |\\-|\\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\\/)|klon|kpt |kwc\\-|kyo(c|k)|le(no|xi)|lg( g|\\/(k|l|u)|50|54|\\-[a-w])|libw|lynx|m1\\-w|m3ga|m50\\/|ma(te|ui|xo)|mc(01|21|ca)|m\\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\\-2|po(ck|rt|se)|prox|psio|pt\\-g|qa\\-a|qc(07|12|21|32|60|\\-[2-7]|i\\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\\-|oo|p\\-)|sdk\\/|se(c(\\-|0|1)|47|mc|nd|ri)|sgh\\-|shar|sie(\\-|m)|sk\\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\\-|v\\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\\-|tdg\\-|tel(i|m)|tim\\-|t\\-mo|to(pl|sh)|ts(70|m\\-|m3|m5)|tx\\-9|up(\\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\\-|your|zeto|zte\\-", Pattern.CASE_INSENSITIVE);
	
	protected TimeZone timezone;

	private UserModel user;

	private String errorMessage;
	
	private String requestUrl;
	
	private AtomicBoolean isForking;
	
	public AuthenticationType authenticationType;
	
	public AtomicBoolean mobileView;
	
	public GitBlitWebSession(Request request) {
		super(request);
		isForking = new AtomicBoolean();
		authenticationType = AuthenticationType.CREDENTIALS;
		mobileView = new AtomicBoolean(isMobileBrowser());
	}

	public void invalidate() {
		super.invalidate();
		user = null;
	}

	/**
	 * Checks whether the user agent is a mobile browser.
	 * 
	 * @return boolean
	 */
	private boolean isMobileBrowser() {
		String userAgent = ((WebClientInfo) getClientInfo()).getUserAgent();		
		return MOBILE_USER_DEVICE.matcher(userAgent).matches()
				|| MOBILE_USER_AGENT.matcher(userAgent.substring(0,4)).matches();
	}
	
	/**
	 * Cache the requested protected resource pending successful authentication.
	 * 
	 * @param pageClass
	 */
	public void cacheRequest(Class<? extends Page> pageClass) {
		// build absolute url with correctly encoded parameters?!
		Request req = WebRequestCycle.get().getRequest();
		Map<String, ?> params = req.getRequestParameters().getParameters();
		PageParameters pageParams = new PageParameters(params);
		String relativeUrl = WebRequestCycle.get().urlFor(pageClass, pageParams).toString();
		requestUrl = RequestUtils.toAbsolutePath(relativeUrl);
		if (isTemporary())
		{
			// we must bind the temporary session into the session store
			// so that we can re-use this session for reporting an error message
			// on the redirected page and continuing the request after
			// authentication.
			bind();
		}
	}
	
	/**
	 * Continue any cached request.  This is used when a request for a protected
	 * resource is aborted/redirected pending proper authentication.  Gitblit
	 * no longer uses Wicket's built-in mechanism for this because of Wicket's
	 * failure to properly handle parameters with forward-slashes.  This is a
	 * constant source of headaches with Wicket.
	 *  
	 * @return false if there is no cached request to process
	 */
	public boolean continueRequest() {
		if (requestUrl != null) {
			String url = requestUrl;
			requestUrl = null;
			throw new RedirectToUrlException(url);
		}
		return false;
	}

	public boolean isLoggedIn() {
		return user != null;
	}

	public boolean canAdmin() {
		if (user == null) {
			return false;
		}
		return user.canAdmin();
	}
	
	public String getUsername() {
		return user == null ? "anonymous" : user.username;
	}

	public UserModel getUser() {
		return user;
	}

	public void setUser(UserModel user) {
		this.user = user;
		if (user != null) {
			Locale preferredLocale = user.getPreferences().getLocale();
			if (preferredLocale != null) {
				// set the user's preferred locale
				setLocale(preferredLocale);
			}
		}
	}

	public TimeZone getTimezone() {
		if (timezone == null) {
			timezone = ((WebClientInfo) getClientInfo()).getProperties().getTimeZone();
		}
		// use server timezone if we can't determine the client timezone
		if (timezone == null) {
			timezone = TimeZone.getDefault();
		}
		return timezone;
	}

	public void cacheErrorMessage(String message) {
		this.errorMessage = message;
	}

	public String clearErrorMessage() {
		String msg = errorMessage;
		errorMessage = null;
		return msg;
	}
	
	public boolean isForking() {
		return isForking.get();
	}
	
	public void isForking(boolean val) {
		isForking.set(val);
	}

	public static GitBlitWebSession get() {
		return (GitBlitWebSession) Session.get();
	}
	
	public boolean isMobileView() {
		return mobileView.get();
	}
	
	public void setMobileView(boolean isMobileView) {
		this.mobileView.set(isMobileView);
	}
	
	public void toggleMobileView() {
		this.mobileView.set(!isMobileView());
	}
}