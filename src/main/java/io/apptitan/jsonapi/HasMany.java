package io.apptitan.jsonapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface HasMany {

	/**
	 * Should the collection be Serialized into the response
	 * 
	 * @return
	 */
	public boolean lazy() default true;
}
