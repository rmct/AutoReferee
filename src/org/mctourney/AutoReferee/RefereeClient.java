package org.mctourney.AutoReferee;

import java.io.*;
import java.net.Socket;

public class RefereeClient implements Runnable 
{
	public BufferedReader inp; 
	public PrintStream oup;
	
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
	
	public void close()
	{
		try
		{
			// close streams
			inp.close();
			oup.close();
			
			// close socket
			socket.close();
		}
		catch (IOException e) {  };
	}
	
	public void run()
	{
		
		// get the key from the configuration file (should be guaranteed not-null)
		String key = refPlugin.getConfig().getString("server-mode.server-key", null);
		if (key == null) { failure = true; close(); return; }
		
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
			failure = true; close(); return;
		}
		
		try
		{
			// send our key to the central server
			oup.println("START " + key);
			
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
			failure = true; close(); return;
		}
	}
}
