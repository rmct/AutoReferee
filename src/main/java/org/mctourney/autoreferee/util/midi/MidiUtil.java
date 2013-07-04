package org.mctourney.autoreferee.util.midi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Utility for playing midi files for players to hear.
 *
 * @author authorblues
 */
public class MidiUtil
{
	private static void playMidi(Sequence seq, float tempo, Set<Player> listeners)
		throws InvalidMidiDataException, IOException, MidiUnavailableException
	{
		Sequencer sequencer = MidiSystem.getSequencer(false);
		sequencer.setSequence(seq);
		sequencer.open();

		// slow it down just a bit
		sequencer.setTempoFactor(tempo);

		NoteBlockReceiver noteblockRecv = new NoteBlockReceiver(listeners);
		sequencer.getTransmitter().setReceiver(noteblockRecv);
		sequencer.start();
	}

	public static void playMidi(File file, float tempo, Set<Player> listeners)
		throws InvalidMidiDataException, IOException, MidiUnavailableException
	{ playMidi(MidiSystem.getSequence(file), tempo, listeners); }

	public static void playMidi(InputStream stream, float tempo, Set<Player> listeners)
		throws InvalidMidiDataException, IOException, MidiUnavailableException
	{ playMidi(MidiSystem.getSequence(stream), tempo, listeners); }

	public static boolean playMidiQuietly(File file, float tempo, Set<Player> listeners)
	{
		try { MidiUtil.playMidi(file, tempo, listeners); }
		catch (MidiUnavailableException e) { e.printStackTrace(); return false; }
		catch (InvalidMidiDataException e) { e.printStackTrace(); return false; }
		catch (IOException e) { e.printStackTrace(); return false; }

		return true;
	}
	public static boolean playMidiQuietly(InputStream stream, float tempo, Set<Player> listeners)
	{
		try { MidiUtil.playMidi(stream, tempo, listeners); }
		catch (MidiUnavailableException e) { e.printStackTrace(); return false; }
		catch (InvalidMidiDataException e) { e.printStackTrace(); return false; }
		catch (IOException e) { e.printStackTrace(); return false; }

		return true;
	}

	public static boolean playMidiQuietly(File file, Set<Player> listeners)
	{ return playMidiQuietly(file, 1.0f, listeners); }

	public static boolean playMidiQuietly(InputStream stream, Set<Player> listeners)
	{ return playMidiQuietly(stream, 1.0f, listeners); }

	// provided by github.com/sk89q/craftbook
	private static final int[] instruments = {
		0, 0, 0, 0, 0, 0, 0, 5, //   8
		6, 0, 0, 0, 0, 0, 0, 0, //  16
		0, 0, 0, 0, 0, 0, 0, 5, //  24
		5, 5, 5, 5, 5, 5, 5, 5, //  32
		6, 6, 6, 6, 6, 6, 6, 6, //  40
		5, 5, 5, 5, 5, 5, 5, 2, //  48
		5, 5, 5, 5, 0, 0, 0, 0, //  56
		0, 0, 0, 0, 0, 0, 0, 0, //  64
		0, 0, 0, 0, 0, 0, 0, 0, //  72
		0, 0, 0, 0, 0, 0, 0, 0, //  80
		0, 0, 0, 0, 0, 0, 0, 0, //  88
		0, 0, 0, 0, 0, 0, 0, 0, //  96
		0, 0, 0, 0, 0, 0, 0, 0, // 104
		0, 0, 0, 0, 0, 0, 0, 0, // 112
		1, 1, 1, 3, 1, 1, 1, 5, // 120
		1, 1, 1, 1, 1, 2, 4, 3, // 128
	};

	public static Sound patchToInstrument(int patch)
	{
		// look up the instrument matching the patch
		switch (instruments[patch])
		{
			case 1: return Sound.NOTE_BASS_GUITAR;
			case 2: return Sound.NOTE_SNARE_DRUM;
			case 3: return Sound.NOTE_STICKS;
			case 4: return Sound.NOTE_BASS_DRUM;
			case 5: return Sound.NOTE_PLING;
			case 6: return Sound.NOTE_BASS;
		}

		// if no instrument match is found, use piano
		return Sound.NOTE_PIANO;
	}
}
