package io.apptitan.jsonapi;

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

import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.apache.commons.beanutils.PropertyUtils;
import org.atteo.evo.inflector.English;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.base.CaseFormat;

public class JsonApiSerializer extends JsonSerializer<Object> {

	protected String namespace() {
		return "/jsonapi";
	}

	protected List<Class<? extends Annotation>> belongsToAnnotations() {
		return Arrays.asList(BelongsTo.class, ManyToOne.class, OneToOne.class,
				HasMany.class);
	}

	protected List<Class<? extends Annotation>> hasManyAnnotations() {
		return Arrays.asList(HasMany.class, OneToMany.class, ManyToMany.class,
				BelongsTo.class);
	}

	protected List<Class<? extends Annotation>> idAnnotations() {
		return Arrays.asList(JsonApiId.class, Id.class);
	}

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
		// final String camelisedModelName = CaseFormat.UPPER_CAMEL.to(
		// CaseFormat.LOWER_CAMEL, object.getClass().getSimpleName());
		// final String camelisedModelNamePlural = English
		// .plural(camelisedModelName);
		final String hyphenatedModelName = CaseFormat.UPPER_CAMEL.to(
				CaseFormat.LOWER_HYPHEN, object.getClass().getSimpleName());
		final String hyphenatedModelNamePlural = English
				.plural(hyphenatedModelName);

		// Root
		jgen.writeStartObject();

		// Write out the id
		final Object idValue;
		if (object.getClass().isEnum()) {
			idValue = object.getClass().getMethod("name").invoke(object);
		} else {
			idValue = PropertyUtils.getProperty(object,
					JSONAPIRelationshipMap.idAttribute.getName());
		}
		jgen.writeStringField(JsonApiConstants.ID, String.valueOf(idValue));
		jgen.writeStringField(JsonApiConstants.TYPE, hyphenatedModelNamePlural);

		jgen.writeObjectFieldStart(JsonApiConstants.LINKS);
		jgen.writeObjectField(JsonApiConstants.SELF, String.format(
				JsonApiConstants.ID_FORMAT, namespace(),
				hyphenatedModelNamePlural, String.valueOf(idValue)));
		jgen.writeEndObject();

		// Attributes
		jgen.writeObjectFieldStart(JsonApiConstants.ATTRIBUTES);
		for (Field field : JSONAPIRelationshipMap.attributes) {
			String fieldName = field.getName();
			String hyphenatedFieldName = CaseFormat.LOWER_CAMEL.to(
					CaseFormat.LOWER_HYPHEN, field.getName());
			final Object value = PropertyUtils.getProperty(object, fieldName);
			jgen.writeObjectField(hyphenatedFieldName, value);
		}
		jgen.writeEndObject();

		// hasMany
		jgen.writeObjectFieldStart(JsonApiConstants.RELATIONSHIPS);
		for (Field field : JSONAPIRelationshipMap.hasManyRelationships) {
			String hyphenatedFieldName = CaseFormat.LOWER_CAMEL.to(
					CaseFormat.LOWER_HYPHEN, field.getName());

			jgen.writeObjectFieldStart(hyphenatedFieldName);
			jgen.writeObjectFieldStart(JsonApiConstants.LINKS);

			jgen.writeObjectField(JsonApiConstants.SELF, String.format(
					JsonApiConstants.RELATIONSHIP_FORMAT, namespace(),
					hyphenatedModelNamePlural, String.valueOf(idValue),
					hyphenatedFieldName));
			jgen.writeObjectField(JsonApiConstants.RELATED, String.format(
					JsonApiConstants.RELATED_FORMAT, namespace(),
					hyphenatedModelNamePlural, String.valueOf(idValue),
					hyphenatedFieldName));

			jgen.writeEndObject();
			jgen.writeEndObject();
		}

		// belongsTo
		for (Field field : JSONAPIRelationshipMap.belongsToRelationships) {
			String hyphenatedFieldName = CaseFormat.UPPER_CAMEL.to(
					CaseFormat.LOWER_HYPHEN, field.getName());
			String fieldNamePlural = English.plural(hyphenatedFieldName);
			Object relatedEntity = PropertyUtils.getProperty(object,
					field.getName());

			jgen.writeObjectFieldStart(hyphenatedFieldName);
			jgen.writeObjectFieldStart(JsonApiConstants.LINKS);

			jgen.writeObjectField(JsonApiConstants.SELF, String.format(
					JsonApiConstants.RELATIONSHIP_FORMAT, namespace(),
					hyphenatedModelNamePlural, String.valueOf(idValue),
					hyphenatedModelName));
			jgen.writeObjectField(JsonApiConstants.RELATED, String.format(
					JsonApiConstants.RELATED_FORMAT, namespace(),
					hyphenatedModelNamePlural, String.valueOf(idValue),
					hyphenatedModelName));

			jgen.writeEndObject();

			// Data
			boolean hasRelatedEntity = relatedEntity != null;
			if (hasRelatedEntity) {
				jgen.writeObjectFieldStart(JsonApiConstants.DATA);
				jgen.writeObjectField(JsonApiConstants.TYPE, fieldNamePlural);

				Object entity = PropertyUtils.getProperty(object,
						field.getName());
				Object entityId = PropertyUtils.getProperty(entity,
						JsonApiConstants.ID);

				// TODO get id by annotation
				jgen.writeObjectField(JsonApiConstants.ID,
						String.valueOf(entityId));

				jgen.writeEndObject();
			} else {
				jgen.writeObjectField(JsonApiConstants.DATA, null);
			}

			jgen.writeEndObject();
		}
		jgen.writeEndObject();

		// Meta
		if (JSONAPIRelationshipMap.meta != null) {
			jgen.writeObjectField(JsonApiConstants.META,
					JSONAPIRelationshipMap.meta);
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
			if (JsonApiConstants.META.equals(fieldName)) {
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
