package io.apptitan.jsonapi;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;

import org.apache.commons.beanutils.PropertyUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.CaseFormat;

public class JsonApiDeserializer extends JsonDeserializer<Object> implements ContextualDeserializer {

    private Class<?> targetClass;

    @Override
    public JsonDeserializer<?> createContextual(final DeserializationContext ctxt, final BeanProperty property)
            throws JsonMappingException {
        targetClass = ctxt.getContextualType().getRawClass();
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException, JsonProcessingException {
        try {
            final Object target = targetClass.newInstance();

            final JsonNode node = jp.getCodec().readTree(jp);
            final ObjectNode data = (ObjectNode) node.get(JsonApiConstants.DATA);
            final ObjectNode attributes = (ObjectNode) data.get(JsonApiConstants.ATTRIBUTES);
            if (attributes != null) {
                attributes.fields().forEachRemaining(entry -> {
                    final String propertyName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, entry.getKey());
                    try {
                        final PropertyDescriptor descriptor = PropertyUtils.getPropertyDescriptor(target, propertyName);
                        final Object value = parseValue(descriptor.getPropertyType(), entry.getValue());
                        PropertyUtils.setProperty(target, propertyName, value);
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            final ObjectNode relationships = (ObjectNode) data.get(JsonApiConstants.RELATIONSHIPS);
            if (relationships != null) {
                relationships.fields().forEachRemaining(entry -> {
                    try {
                        final String propertyName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, entry.getKey());
                        final PropertyDescriptor descriptor = PropertyUtils.getPropertyDescriptor(target, propertyName);
                        final JsonNode idNode = entry.getValue().get(JsonApiConstants.DATA).get(JsonApiConstants.ID);

                        if (descriptor.getPropertyType().isEnum()) {
                            final Class<? extends Enum> enumClass = (Class<? extends Enum>) descriptor.getPropertyType();
                            final Enum<?> enumValue = Enum.valueOf(enumClass, idNode.asText());
                            PropertyUtils.setProperty(target, propertyName, enumValue);
                        } else {
                            final Object relationshipInstance = descriptor.getPropertyType().newInstance();
                            final JsonApiRelationshipMap relationshipMap =
                                    new JsonApiRelationshipMap(relationshipInstance, idAnnotations(), Collections.emptyList(),
                                            Collections.emptyList());
                            // final Object idValue =
                            // parseValue(relationshipMap.getIdAttribute().getType(),
                            // idNode);
                            //
                            // final Type gType =
                            // relationshipMap.getIdAttribute().getGenericType();
                            // if (gType instanceof ParameterizedType) {
                            // final ParameterizedType pType = (ParameterizedType)
                            // gType;
                            // System.out.print("Raw type: " + pType.getRawType() +
                            // " - ");
                            // System.out.println("Type args: " +
                            // pType.getActualTypeArguments()[0]);
                            // } else {
                            // System.out.println("Type: " +
                            // relationshipMap.getIdAttribute().getType());
                            // }

                            Object idValue = null;
                        try {
                            idValue = Long.valueOf(idNode.textValue());
                        } catch (final Exception e) {
                            idValue = idNode.textValue();
                        }

                        PropertyUtils.setProperty(relationshipInstance, relationshipMap.getIdAttribute().getName(),
                                idValue);
                        PropertyUtils.setProperty(target, propertyName, relationshipInstance);
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }   );
            }

            return target;
        } catch (InstantiationException | IllegalAccessException e1) {
            e1.printStackTrace();
            return null;
        }
    }

    protected List<Class<? extends Annotation>> idAnnotations() {
        return Arrays.asList(JsonApiId.class, Id.class);
    }

    private Object parseValue(final Class<?> clazz, final JsonNode value) {
        Object result = null;

        if (clazz == String.class) {
            result = value.asText();
        }
        if ((clazz == Long.class) || (clazz == long.class)) {
            result = value.asLong();
        }
        if ((clazz == Double.class) || (clazz == double.class)) {
            result = value.asDouble();
        }
        if ((clazz == Integer.class) || (clazz == int.class)) {
            result = value.asInt();
        }
        if (clazz == Date.class) {
            result = new Date(value.asInt());
        }
        if (clazz == Boolean.class) {
            result = value.asBoolean();
        }
        return result;
    }

}
