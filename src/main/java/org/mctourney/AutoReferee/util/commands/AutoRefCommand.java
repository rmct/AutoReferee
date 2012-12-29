package org.mctourney.AutoReferee.util.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)

public @interface AutoRefCommand
{
	// name of command
	public String[] name();

	// command description
	public String description() default "";

	// number of arguments
	public int argmin() default 0;
	public int argmax() default Integer.MAX_VALUE;

	// options
	public String options() default "";
}
