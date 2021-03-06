package io.apptitan.jsonapi;

class JsonApiConstants {

	private JsonApiConstants() {
		// Intentionally private
	}

	public static final String ID_FORMAT = "%s/%s/%s";
	public static final String RELATED_FORMAT = "%s/%s/%s/%s";
	public static final String RELATIONSHIP_FORMAT = "%s/%s/%s/relationships/%s";
	public static final String RELATED = "related";
	public static final String TYPE = "type";
	public static final String ID = "id";
	public static final String DATA = "data";
	public static final String SELF = "self";
	public static final String LINKS = "links";
	public static final String RELATIONSHIPS = "relationships";
	public static final String ATTRIBUTES = "attributes";
	public static final String META = "meta";
}
