package io.apptitan.jsonapi;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("serial")
public class JsonApiResponse extends ConcurrentHashMap<String, Object> {

	private static final ConcurrentMap<String, Object> jsonApiVersion = new ConcurrentHashMap<>();
	static {
		jsonApiVersion.put("version", 1);
	}

	private JsonApiResponse() {
		// Must use the builder
	}

	public static class Builder {
		private final Set<Object> includes = new HashSet<Object>();
		private final ConcurrentMap<String, Object> meta = new ConcurrentHashMap<String, Object>();
		private Object data = null;

		public Builder(final Object data) {
			this.data = data;
		}

		public Builder meta(String key, Object value) {
			meta.put(key, value);
			return this;
		}

		public Builder meta(ConcurrentMap<String, Object> meta) {
			meta.putAll(meta);
			return this;
		}

		public Builder include(Set<Object> includes) {
			this.includes.addAll(includes);
			return this;
		}

		public Builder include(Object... objects) {
			Set<Object> set = new HashSet<>(Arrays.asList(objects));
			this.includes.addAll(set);
			return this;
		}

		public JsonApiResponse build() {
			JsonApiResponse response = new JsonApiResponse();
			response.put("jsonapi", jsonApiVersion);
			response.put("data", this.data);
			// response.put("links", "TODO");
			response.put("included", this.includes);
			response.put("meta", this.meta);
			return response;
		}
	}
}
