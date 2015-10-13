package io.apptitan.jsonapi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;

import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.apache.commons.beanutils.PropertyUtils;
import org.atteo.evo.inflector.English;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.base.CaseFormat;

class ClassAnnotationMeta {
    private final String plural;
    private final String singular;

    public ClassAnnotationMeta(final Class<?> clazz, final CaseFormat fromFormat, final CaseFormat toFormat) {
        String singular = fromFormat.to(toFormat, clazz.getSimpleName());
        final String plural = English.plural(singular);

        final JsonApiResource[] annotations = clazz.getAnnotationsByType(JsonApiResource.class);
        if (annotations.length > 0) {
            final JsonApiResource jsonApiResourceAnnotation = annotations[0];

            if (jsonApiResourceAnnotation.singular() != null) {
                singular = jsonApiResourceAnnotation.singular();
            }

            if (jsonApiResourceAnnotation.plural() != null) {
                singular = jsonApiResourceAnnotation.plural();
            }
        }

        this.singular = singular;
        this.plural = plural;
    }

    public String getPlural() {
        return plural;
    }

    public String getSingular() {
        return singular;
    }
}

public class JsonApiSerializer extends JsonSerializer<Object> {

    protected List<Class<? extends Annotation>> belongsToAnnotations() {
        return Arrays.asList(BelongsTo.class, ManyToOne.class, OneToOne.class, HasMany.class);
    }

    protected List<Class<? extends Annotation>> hasManyAnnotations() {
        return Arrays.asList(HasMany.class, OneToMany.class, ManyToMany.class, BelongsTo.class);
    }

    protected List<Class<? extends Annotation>> idAnnotations() {
        return Arrays.asList(JsonApiId.class, Id.class);
    }

    /**
     * @return the root url for jsonapi requests, defaults to "/jsonapi"
     */
    protected String namespace() {
        return "/jsonapi";
    }

    /**
     * @return url format to use when serializing. Defaults to
     *         {@link CaseFormat#LOWER_HYPHEN}
     */
    protected CaseFormat pathFormat() {
        return CaseFormat.LOWER_HYPHEN;
    }

    @Override
    public void serialize(
            final Object object,
            final JsonGenerator jgen,
            final SerializerProvider provider) throws IOException, JsonProcessingException {
        try {
            final JsonApiRelationshipMap jsonApiRelationshipMap =
                    new JsonApiRelationshipMap(object, idAnnotations(), belongsToAnnotations(), hasManyAnnotations());
            writeObjectAsJSONAPI(object, jgen, jsonApiRelationshipMap);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | IOException e) {
            e.printStackTrace();
        }
    }

    private void writeObjectAsJSONAPI(
            final Object object,
            final JsonGenerator jgen,
            final JsonApiRelationshipMap
            jsonApiRelationshipMap)
                    throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, IOException {

        // Model Information
        final ClassAnnotationMeta modelNames = new ClassAnnotationMeta(object.getClass(), CaseFormat.UPPER_CAMEL, pathFormat());

        // Root
        jgen.writeStartObject();

        // Write out the id
        final Object idObjectValue;
        if (object.getClass().isEnum()) {
            idObjectValue = object.getClass().getMethod("name").invoke(object);
        } else {
            idObjectValue = PropertyUtils.getProperty(object, jsonApiRelationshipMap.getIdAttribute().getName());
        }
        final String modelIdentifier = String.valueOf(idObjectValue);

        jgen.writeStringField(JsonApiConstants.ID, modelIdentifier);
        jgen.writeStringField(JsonApiConstants.TYPE, modelNames.getPlural());

        jgen.writeObjectFieldStart(JsonApiConstants.LINKS);
        jgen.writeObjectField(JsonApiConstants.SELF,
                String.format(JsonApiConstants.ID_FORMAT, namespace(), modelNames.getPlural(), modelIdentifier));
        jgen.writeEndObject();

        // Attributes
        jgen.writeObjectFieldStart(JsonApiConstants.ATTRIBUTES);
        for (final Field field : jsonApiRelationshipMap.getAttributes()) {
            final String fieldName = field.getName();
            final Object value = PropertyUtils.getProperty(object, fieldName);
            final String attributeName = CaseFormat.LOWER_CAMEL.to(pathFormat(), fieldName);
            jgen.writeObjectField(attributeName, value);
        }
        jgen.writeEndObject();

        // hasMany
        jgen.writeObjectFieldStart(JsonApiConstants.RELATIONSHIPS);
        for (final Field field : jsonApiRelationshipMap.getHasManyRelationships()) {
            final String relationshipName = CaseFormat.LOWER_CAMEL.to(pathFormat(), field.getName());
            final ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            final Class<?> actualTypeClass = (Class<?>) parameterizedType.getActualTypeArguments()[0];

            final ClassAnnotationMeta relationshipNames = new ClassAnnotationMeta(actualTypeClass, CaseFormat.UPPER_CAMEL,
                    pathFormat());

            jgen.writeObjectFieldStart(relationshipName);
            jgen.writeObjectFieldStart(JsonApiConstants.LINKS);

            jgen.writeObjectField(
                    JsonApiConstants.SELF,
                    String.format(JsonApiConstants.RELATIONSHIP_FORMAT, namespace(), modelNames.getPlural(),
                            modelIdentifier, relationshipNames.getPlural()));
            jgen.writeObjectField(JsonApiConstants.RELATED, String.format(JsonApiConstants.RELATED_FORMAT, namespace(),
                    modelNames.getPlural(), modelIdentifier, relationshipNames.getPlural()));

            jgen.writeEndObject();
            jgen.writeEndObject();
        }

        // belongsTo
        for (final Field field : jsonApiRelationshipMap.getBelongsToRelationships()) {
            final String relationshipName = CaseFormat.UPPER_CAMEL.to(pathFormat(), field.getName());
            final Object relatedEntity = PropertyUtils.getProperty(object, field.getName());

            final ClassAnnotationMeta relationshipNames = new ClassAnnotationMeta(field.getType(), CaseFormat.UPPER_CAMEL,
                    pathFormat());

            jgen.writeObjectFieldStart(relationshipName);
            jgen.writeObjectFieldStart(JsonApiConstants.LINKS);

            jgen.writeObjectField(
                    JsonApiConstants.SELF,
                    String.format(JsonApiConstants.RELATIONSHIP_FORMAT, namespace(), modelNames.getPlural(),
                            modelIdentifier, relationshipNames.getSingular()));
            jgen.writeObjectField(JsonApiConstants.RELATED, String.format(JsonApiConstants.RELATED_FORMAT, namespace(),
                    modelNames.getPlural(), modelIdentifier, relationshipNames.getSingular()));

            jgen.writeEndObject();

            // Data
            final boolean hasRelatedEntity = relatedEntity != null;
            if (hasRelatedEntity) {
                jgen.writeObjectFieldStart(JsonApiConstants.DATA);
                jgen.writeObjectField(JsonApiConstants.TYPE, relationshipNames.getPlural());

                final Object entity = PropertyUtils.getProperty(object, field.getName());

                Object entityId = null;
                if (entity.getClass().isEnum()) {
                    entityId = entity.getClass().getMethod("name").invoke(entity);
                } else {
                    final JsonApiRelationshipMap relationshipMap =
                            new JsonApiRelationshipMap(entity, idAnnotations(), belongsToAnnotations(), hasManyAnnotations());
                    entityId = PropertyUtils.getProperty(entity, relationshipMap.getIdAttribute().getName());
                }

                jgen.writeObjectField(JsonApiConstants.ID, String.valueOf(entityId));

                jgen.writeEndObject();
            } else {
                jgen.writeObjectField(JsonApiConstants.DATA, null);
            }

            jgen.writeEndObject();
        }
        jgen.writeEndObject();

        // Meta
        if (jsonApiRelationshipMap.getMeta() != null) {
            jgen.writeObjectField(JsonApiConstants.META, jsonApiRelationshipMap.getMeta());
        }

        // End Root
        jgen.writeEndObject();
    }

}
