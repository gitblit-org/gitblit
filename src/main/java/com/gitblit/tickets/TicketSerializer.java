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
import java.util.Date;
import java.util.List;

import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Score;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JsonUtils.ExcludeField;
import com.gitblit.utils.JsonUtils.GmtDateTypeAdapter;
import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
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
		Collection<Change> list = gson().fromJson(json, JOURNAL_TYPE);
		return new ArrayList<Change>(list);
	}

	public static TicketModel deserializeTicket(String json) {
		return gson().fromJson(json, TicketModel.class);
	}

	public static TicketLabel deserializeLabel(String json) {
		return gson().fromJson(json, TicketLabel.class);
	}

	public static TicketMilestone deserializeMilestone(String json) {
		return gson().fromJson(json, TicketMilestone.class);
	}


	public static String serializeJournal(List<Change> changes) {
		try {
			Gson gson = gson();
			return gson.toJson(changes);
		} catch (Exception e) {
			// won't happen
		}
		return null;
	}

	public static String serialize(TicketModel ticket) {
		if (ticket == null) {
			return null;
		}
		try {
			Gson gson = gson(
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
			Gson gson = gson(
					new ExcludeField("com.gitblit.models.TicketModel$Attachment.content"));
			return gson.toJson(change);
		} catch (Exception e) {
			// won't happen
		}
		return null;
	}

	public static String serialize(TicketLabel label) {
		if (label == null) {
			return null;
		}
		try {
			Gson gson = gson();
			return gson.toJson(label);
		} catch (Exception e) {
			// won't happen
		}
		return null;
	}

	public static String serialize(TicketMilestone milestone) {
		if (milestone == null) {
			return null;
		}
		try {
			Gson gson = gson();
			return gson.toJson(milestone);
		} catch (Exception e) {
			// won't happen
		}
		return null;
	}

	// build custom gson instance with GMT date serializer/deserializer
	// http://code.google.com/p/google-gson/issues/detail?id=281
	public static Gson gson(ExclusionStrategy... strategies) {
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Date.class, new GmtDateTypeAdapter());
		builder.registerTypeAdapter(Score.class, new ScoreTypeAdapter());
		if (!ArrayUtils.isEmpty(strategies)) {
			builder.setExclusionStrategies(strategies);
		}
		return builder.create();
	}

	private static class ScoreTypeAdapter implements JsonSerializer<Score>, JsonDeserializer<Score> {

		private ScoreTypeAdapter() {
		}

		@Override
		public synchronized JsonElement serialize(Score score, Type type,
				JsonSerializationContext jsonSerializationContext) {
				return new JsonPrimitive(score.getValue());
		}

		@Override
		public synchronized Score deserialize(JsonElement jsonElement, Type type,
				JsonDeserializationContext jsonDeserializationContext) {
			try {
				int value = jsonElement.getAsInt();
				for (Score score : Score.values()) {
					if (score.getValue() == value) {
						return score;
					}
				}
				return Score.not_reviewed;
			} catch (Exception e) {
				throw new JsonSyntaxException(jsonElement.getAsString(), e);
			}
		}
	}
}
