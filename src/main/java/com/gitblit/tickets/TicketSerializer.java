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
package com.gitblit.tickets;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.JsonUtils.ExcludeField;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Serializes and deserializes tickets, change, and journals.
 *
 * @author James Moger
 *
 */
public class TicketSerializer {

	protected static final Type JOURNAL_TYPE = new TypeToken<Collection<Change>>() {}.getType();

	public static List<Change> deserializeJournal(String json) {
		Collection<Change> list = JsonUtils.fromJsonString(json, JOURNAL_TYPE);
		return new ArrayList<Change>(list);
	}

	public static TicketModel deserializeTicket(String json) {
		return JsonUtils.fromJsonString(json, TicketModel.class);
	}

	public static String serialize(TicketModel ticket) {
		if (ticket == null) {
			return null;
		}
		try {
			Gson gson = JsonUtils.gson(
					new ExcludeField("com.gitblit.models.TicketModel$Attachment.content"),
					new ExcludeField("com.gitblit.models.TicketModel$Attachment.deleted"),
					new ExcludeField("com.gitblit.models.TicketModel$Comment.deleted"));
			return gson.toJson(ticket);
		} catch (Exception e) {
			// won't happen
		}
		return null;
	}

	public static String serialize(Change change) {
		if (change == null) {
			return null;
		}
		try {
			Gson gson = JsonUtils.gson(
					new ExcludeField("com.gitblit.models.TicketModel$Attachment.content"));
			return gson.toJson(change);
		} catch (Exception e) {
			// won't happen
		}
		return null;
	}
}
