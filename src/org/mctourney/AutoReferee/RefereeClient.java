package org.mctourney.AutoReferee;

import java.io.*;
import java.net.Socket;

public class RefereeClient implements Runnable 
{
	private Socket socket = null;
	private AutoReferee refPlugin = null;
	
	// this will let us know if something went wrong, if we should just
	// return to a degenerate case (perhaps offline mode)
	private boolean failure = false;
	public boolean hasFailed() { return failure; }
	
	public RefereeClient(AutoReferee ref, Socket s)
	{
		// if the socket is connected, save it
		// we should only be passed CONNECTED sockets
		failure = !s.isConnected(); socket = s;
		
		refPlugin = ref;
	}
	
	public void run()
	{
		BufferedReader inp; 
		PrintStream oup;
		
		try
		{
			// get an input and output stream from this socket, to communicate
			inp = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			oup = new PrintStream(socket.getOutputStream());
		}
		catch (IOException e)
		{
			// log the error, stop running the thread and report failure
			refPlugin.log.severe("Could not initialize communication with server.");
			failure = true; return;
		}
		
		try
		{
			// read lines from the socket, process them
			for (String resp; (resp = inp.readLine()) != null; )
			{
				refPlugin.log.info("ECHO: " + resp); // TODO
				oup.println(resp);
			}
		}
		catch (IOException e)
		{
			// log the error, stop running the thread and report failure
			refPlugin.log.severe("Server communication failure: " + e);
			failure = true; return;
		}
	}
}
