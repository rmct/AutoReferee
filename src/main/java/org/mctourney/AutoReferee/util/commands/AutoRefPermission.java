package org.mctourney.AutoReferee.util.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.mctourney.AutoReferee.AutoRefMatch.Role;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)

public @interface AutoRefPermission
{
	// permissions nodes required
	public String[] nodes() default {};

	// AutoReferee role required (this role or higher)
	public Role role() default Role.NONE;

	// can console send this command?
	public boolean console() default true;
}
