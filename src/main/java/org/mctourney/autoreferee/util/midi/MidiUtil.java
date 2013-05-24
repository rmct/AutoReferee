package org.mctourney.autoreferee.util.midi;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;

import org.bukkit.entity.Player;

/**
 * Utility for playing midi files for players to hear.
 *
 * @author authorblues
 */
public class MidiUtil
{
	public static void playMidi(File file, float tempo, Set<Player> listeners)
		throws InvalidMidiDataException, IOException, MidiUnavailableException
	{
		Sequencer sequencer = MidiSystem.getSequencer(false);
		sequencer.setSequence(MidiSystem.getSequence(file));
		sequencer.open();

		// slow it down just a bit
		sequencer.setTempoFactor(tempo);

		NoteBlockReceiver noteblockRecv = new NoteBlockReceiver(listeners);
		sequencer.getTransmitter().setReceiver(noteblockRecv);
		sequencer.start();
	}

	public static boolean playMidiQuietly(File file, float tempo, Set<Player> listeners)
	{
		try { MidiUtil.playMidi(file, tempo, listeners); }
		catch (MidiUnavailableException e) { e.printStackTrace(); return false; }
		catch (InvalidMidiDataException e) { e.printStackTrace(); return false; }
		catch (IOException e) { e.printStackTrace(); return false; }

		return true;
	}

	public static boolean playMidiQuietly(File file, Set<Player> listeners)
	{
		return playMidiQuietly(file, 1.0f, listeners);
	}
}
