package io.apptitan.jsonapi;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

public class JpaJsonApiSerializer extends JsonApiSerializer {

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

}