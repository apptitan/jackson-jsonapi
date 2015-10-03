package io.scriptorials.jsonapi;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.util.Date;

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

public class JsonApiDeserializer extends JsonDeserializer<Object> implements
		ContextualDeserializer {

	private Class<?> targetClass;

	@Override
	public Object deserialize(JsonParser jp, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		try {
			Object target = targetClass.newInstance();

			JsonNode node = jp.getCodec().readTree(jp);
			ObjectNode data = (ObjectNode) node.get("data");
			ObjectNode attributes = (ObjectNode) data.get("attributes");
			if (attributes != null) {
				attributes.fields().forEachRemaining(
						entry -> {
							String propertyName = CaseFormat.LOWER_HYPHEN.to(
									CaseFormat.LOWER_CAMEL, entry.getKey());
							try {
								PropertyDescriptor descriptor = PropertyUtils
										.getPropertyDescriptor(target,
												propertyName);
								Object value = parseValue(
										descriptor.getPropertyType(),
										entry.getValue());
								PropertyUtils.setProperty(target, propertyName,
										value);
							} catch (Exception e) {
								e.printStackTrace();
							}
						});
			}

			ObjectNode relationships = (ObjectNode) data.get("relationships");
			if (relationships != null) {
				relationships.fields().forEachRemaining(
						entry -> {
							try {
								String propertyName = CaseFormat.LOWER_HYPHEN
										.to(CaseFormat.LOWER_CAMEL,
												entry.getKey());
								PropertyDescriptor descriptor = PropertyUtils
										.getPropertyDescriptor(target,
												propertyName);
								JsonNode idNode = entry.getValue().get("data")
										.get("id");

								Object relationshipInstance = descriptor
										.getPropertyType().newInstance();
								PropertyUtils.setProperty(relationshipInstance,
										"id", idNode.asLong());
								PropertyUtils.setProperty(target, propertyName,
										relationshipInstance);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						});
			}

			return target;
		} catch (InstantiationException | IllegalAccessException e1) {
			e1.printStackTrace();
			return null;
		}
	}

	@Override
	public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
			BeanProperty property) throws JsonMappingException {
		targetClass = ctxt.getContextualType().getRawClass();
		return this;
	}

	private Object parseValue(Class<?> clazz, JsonNode value) {
		Object result = null;

		if (clazz == String.class)
			result = value.asText();
		if (clazz == Long.class || clazz == long.class)
			result = value.asLong();
		if (clazz == Double.class || clazz == double.class)
			result = value.asDouble();
		if (clazz == Integer.class || clazz == int.class)
			result = value.asInt();
		if (clazz == Date.class)
			result = new Date(value.asInt());
		if (clazz == Boolean.class)
			result = value.asBoolean();
		return result;
	}

}
