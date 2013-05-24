package org.mctourney.autoreferee.util.midi;

import org.bukkit.Note;
import org.bukkit.Note.Tone;

import javax.sound.midi.ShortMessage;

/**
 * Utility for converting between different representations of a tone.
 *
 * @author authorblues
 */
public class ToneUtil
{
	private static final byte BASE_NOTE = new Note(1, Tone.F, true).getId();

	private static final int MIDI_BASE_FSHARP = 6;

	public static double noteToPitch(Note note)
	{
		double semitones = note.getId() - BASE_NOTE;
		return Math.pow(2.0, semitones / 12.0);
	}

	// converts midi events into Note objects
	public static Note midiToNote(ShortMessage smsg)
	{
		assert smsg.getCommand() == ShortMessage.NOTE_ON;

		int semitones = smsg.getData1() - MIDI_BASE_FSHARP;
		return new Note(semitones % 24);
	}
}
