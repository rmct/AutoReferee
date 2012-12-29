package org.mctourney.AutoReferee.util.commands;

import org.mctourney.AutoReferee.AutoRefMatch.Role;

@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)

public @interface AutoRefPermission
{
	// permissions nodes required
	public String[] nodes() default {};

	// AutoReferee role required (this role or higher)
	public Role role() default Role.NONE;

	// can console send this command?
	public boolean console() default true;
}
