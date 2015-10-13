package io.apptitan.jsonapi;

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

import com.fasterxml.jackson.annotation.JsonIgnore;

class JsonApiRelationshipMap {
    private final Set<Field> attributes = new HashSet<>();
    private final Set<Field> belongsToRelationships = new HashSet<>();
    private final Set<Field> hasManyRelationships = new HashSet<>();
    private Field idAttribute = null;
    private Field meta = null;

    public JsonApiRelationshipMap(
            final Object object,
            final List<Class<? extends Annotation>> idAnnotations,
            final List<Class<? extends Annotation>> belongsToAnnotations,
            final List<Class<? extends Annotation>> hasManyAnnotations)
                    throws IllegalAccessException, InvocationTargetException {
        // Get hold of all fields in the class hierarchy
        final Set<Field> fields = getAllFields(object);

        // Get hold of field values
        for (final Field field : fields) {
            final String fieldName = field.getName();
            final List<Class<? extends Annotation>> fieldAnnotations = Arrays.asList(field.getAnnotations()).stream()
                    .map(o -> o.annotationType()).collect(Collectors.toList());

            // Must have a getter
            try {
                PropertyUtils.getProperty(object, fieldName);
            } catch (final NoSuchMethodException e) {
                continue;
            }

            // Check for @JsonIgnore
            final boolean isIgnored = field.getAnnotation(JsonIgnore.class) != null;
            if (isIgnored) {
                continue;
            }

            // Check for Identifier
            final boolean isIdentifier = !Collections.disjoint(idAnnotations, fieldAnnotations);
            if (isIdentifier) {
                this.idAttribute = field;
                continue;
            }

            // Check for meta
            if (JsonApiConstants.META.equals(fieldName)) {
                this.meta = field;
                continue;
            }

            // hasMany
            final boolean isHasManyRelationship = !Collections.disjoint(hasManyAnnotations, fieldAnnotations);
            if (isHasManyRelationship) {
                this.hasManyRelationships.add(field);
                continue;
            }

            // belongsTo
            final boolean isBelongsToRelationship = !Collections.disjoint(belongsToAnnotations, fieldAnnotations);
            final boolean isEnum = field.getType().isEnum();
            if (isBelongsToRelationship || isEnum) {
                this.belongsToRelationships.add(field);
                continue;
            }

            // attributes
            this.attributes.add(field);
        }
    }

    private Set<Field> getAllFields(final Object object) {
        final Set<Field> fields = new HashSet<>();
        fields.addAll(Arrays.asList(object.getClass().getDeclaredFields()));
        Class<?> superclass = object.getClass().getSuperclass();
        while (superclass != null) {
            fields.addAll(Arrays.asList(superclass.getDeclaredFields()));
            superclass = superclass.getSuperclass();
        }
        return fields;
    }

    public Set<Field> getAttributes() {
        return attributes;
    }

    public Set<Field> getBelongsToRelationships() {
        return belongsToRelationships;
    }

    public Set<Field> getHasManyRelationships() {
        return hasManyRelationships;
    }

    public Field getIdAttribute() {
        return idAttribute;
    }

    public Field getMeta() {
        return meta;
    }
}