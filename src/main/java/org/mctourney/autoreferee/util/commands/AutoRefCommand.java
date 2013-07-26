package org.mctourney.autoreferee.util.commands;

@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)

public @interface AutoRefCommand
{
	// name of command
	public String[] name();

	// command description
	public String usage() default "";

	// command description
	public String description() default "";

	// number of arguments
	public int argmin() default 0;
	public int argmax() default Integer.MAX_VALUE;

	// options
	public String options() default "";
	public String[] opthelp() default {};
}
