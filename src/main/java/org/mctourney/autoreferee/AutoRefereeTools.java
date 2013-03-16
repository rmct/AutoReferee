package org.mctourney.autoreferee;

import java.io.File;

import org.mctourney.autoreferee.util.commands.CommandDocumentationGenerator;

public class AutoRefereeTools
{
	public static void main(String[] args)
	{
		File docfile = new File("documentation.dat");
		System.out.println("Generating documentation file: " + docfile.getAbsolutePath());
		CommandDocumentationGenerator.generateDocumentationFile(docfile);
	}
}
