package io.scriptorials.jsonapi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.PropertyUtils;
import org.atteo.evo.inflector.English;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.base.CaseFormat;

public abstract class JsonApiSerializer extends JsonSerializer<Object> {

	private static final String META = "meta";

	protected abstract List<Class<? extends Annotation>> belongsToAnnotations();

	protected abstract List<Class<? extends Annotation>> hasManyAnnotations();

	protected abstract List<Class<? extends Annotation>> idAnnotations();

	@Override
	public void serialize(Object object, JsonGenerator jgen,
			SerializerProvider provider) throws IOException,
			JsonProcessingException {
		try {
			JsonApiRelationshipMap JSONAPIRelationshipMap = getJSONAPIRelationshipMap(object);
			writeObjectAsJSONAPI(object, jgen, JSONAPIRelationshipMap);
		} catch (IllegalAccessException | InvocationTargetException
				| NoSuchMethodException | IOException e) {
			e.printStackTrace();
		}
	}

	private void writeObjectAsJSONAPI(Object object, JsonGenerator jgen,
			JsonApiRelationshipMap JSONAPIRelationshipMap)
			throws IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, IOException {
		// Model Information
		final String modelName = CaseFormat.UPPER_CAMEL.to(
				CaseFormat.LOWER_HYPHEN, object.getClass().getSimpleName());
		final String modelNamePlural = English.plural(modelName);

		// Root
		jgen.writeStartObject();

		// Write out the id
		final Object idValue = PropertyUtils.getProperty(object,
				JSONAPIRelationshipMap.idAttribute.getName());
		jgen.writeStringField("id", String.valueOf(idValue));
		jgen.writeStringField("type", modelNamePlural);

		jgen.writeObjectFieldStart("links");
		jgen.writeObjectField("self",
				String.format("/%s/%d", modelNamePlural, idValue));
		jgen.writeEndObject();

		// Attributes
		jgen.writeObjectFieldStart("attributes");
		for (Field field : JSONAPIRelationshipMap.attributes) {
			final String fieldName = field.getName();
			final Object value = PropertyUtils.getProperty(object, fieldName);
			jgen.writeObjectField(fieldName, value);
		}
		jgen.writeEndObject();

		// hasMany
		jgen.writeObjectFieldStart("relationships");
		for (Field field : JSONAPIRelationshipMap.hasManyRelationships) {
			final String fieldName = field.getName();
			jgen.writeObjectFieldStart(fieldName);
			jgen.writeObjectFieldStart("links");

			jgen.writeObjectField("self", String.format(
					"/%s/%d/relationships/%s", modelNamePlural, idValue,
					fieldName));
			jgen.writeObjectField("related", String.format("/%s/%d/%s",
					modelNamePlural, idValue, fieldName));

			jgen.writeEndObject();
			jgen.writeEndObject();
		}

		// belongsTo
		for (Field field : JSONAPIRelationshipMap.belongsToRelationships) {
			String fieldName = CaseFormat.UPPER_CAMEL.to(
					CaseFormat.LOWER_HYPHEN, field.getName());
			String fieldNamePlural = English.plural(fieldName);
			Object relatedEntity = PropertyUtils.getProperty(object,
					field.getName());

			jgen.writeObjectFieldStart(fieldName);
			jgen.writeObjectFieldStart("links");

			jgen.writeObjectField("self", String.format(
					"/%s/%d/relationships/%s", modelNamePlural, idValue,
					fieldName));
			jgen.writeObjectField("related", String.format("/%s/%d/%s",
					modelNamePlural, idValue, fieldName));

			jgen.writeEndObject();

			// Data
			boolean hasRelatedEntity = relatedEntity != null;
			if (hasRelatedEntity) {
				jgen.writeObjectFieldStart("data");
				jgen.writeObjectField("type", fieldNamePlural);

				Object entity = PropertyUtils.getProperty(object,
						field.getName());
				Object entityId = PropertyUtils.getProperty(entity, "id");

				// TODO get id by annotation
				jgen.writeObjectField("id", String.valueOf(entityId));

				jgen.writeEndObject();
			} else {
				jgen.writeObjectField("data", null);
			}

			jgen.writeEndObject();
		}
		jgen.writeEndObject();

		// Meta
		if (JSONAPIRelationshipMap.meta != null) {
			jgen.writeObjectField(META, JSONAPIRelationshipMap.meta);
		}

		// End Root
		jgen.writeEndObject();
	}

	private JsonApiRelationshipMap getJSONAPIRelationshipMap(Object object)
			throws IllegalAccessException, InvocationTargetException {
		// Get hold of all fields in the class hierarchy
		Set<Field> fields = getAllFields(object);

		// Get hold of field values
		JsonApiRelationshipMap objectDataMap = new JsonApiRelationshipMap();
		for (final Field field : fields) {
			final String fieldName = field.getName();
			final List<Class<? extends Annotation>> fieldAnnotations = Arrays
					.asList(field.getAnnotations()).stream()
					.map(o -> o.annotationType()).collect(Collectors.toList());

			// Must have a getter
			try {
				PropertyUtils.getProperty(object, fieldName);
			} catch (NoSuchMethodException e) {
				continue;
			}

			// Check for @JsonIgnore
			final boolean isIgnored = field.getAnnotation(JsonIgnore.class) != null;
			if (isIgnored) {
				continue;
			}

			// Check for @Id
			final boolean isIdentifier = !Collections.disjoint(idAnnotations(),
					fieldAnnotations);
			if (isIdentifier) {
				objectDataMap.idAttribute = field;
				continue;
			}

			// Check for meta
			if (META.equals(fieldName)) {
				objectDataMap.meta = field;
				continue;
			}

			// hasMany
			final boolean isHasManyRelationship = !Collections.disjoint(
					hasManyAnnotations(), fieldAnnotations);
			if (isHasManyRelationship) {
				objectDataMap.hasManyRelationships.add(field);
				continue;
			}

			// belongsTo
			final boolean isBelongsToRelationship = !Collections.disjoint(
					belongsToAnnotations(), fieldAnnotations);
			if (isBelongsToRelationship) {
				objectDataMap.belongsToRelationships.add(field);
				continue;
			}

			// attributes
			objectDataMap.attributes.add(field);
		}
		return objectDataMap;
	}

	private Set<Field> getAllFields(Object object) {
		Set<Field> fields = new HashSet<>();
		fields.addAll(Arrays.asList(object.getClass().getDeclaredFields()));
		Class<?> superclass = object.getClass().getSuperclass();
		while (superclass != null) {
			fields.addAll(Arrays.asList(superclass.getDeclaredFields()));
			superclass = superclass.getSuperclass();
		}
		return fields;
	}

}

class JsonApiRelationshipMap {
	Field idAttribute = null;
	Field meta = null;
	final Set<Field> attributes = new HashSet<>();
	final Set<Field> hasManyRelationships = new HashSet<>();
	final Set<Field> belongsToRelationships = new HashSet<>();
}
