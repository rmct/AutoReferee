package org.mctourney.autoreferee.util.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.io.IOException;
import java.util.Set;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Midi Receiver for processing note events.
 *
 * @author authorblues
 */
public class NoteBlockReceiver implements Receiver
{
	private static final float VOLUME_RANGE = 10.0f;

	private final Set<Player> listeners;

	public NoteBlockReceiver(Set<Player> listeners) throws InvalidMidiDataException, IOException
	{
		this.listeners = listeners;
	}

	@Override
	public void send(MidiMessage m, long time)
	{
		if (m instanceof ShortMessage)
		{
			ShortMessage smessage = (ShortMessage) m;
			switch (smessage.getCommand())
			{
				case ShortMessage.NOTE_ON:
					this.playNote(smessage);
					break;

				case ShortMessage.NOTE_OFF:
					break;
			}
		}
	}

	public void playNote(ShortMessage message)
	{
		// if this isn't a NOTE_ON message, we can't play it
		if (ShortMessage.NOTE_ON != message.getCommand()) return;

		// get pitch and volume from the midi message
		float pitch = (float) ToneUtil.midiToPitch(message);
		float volume = VOLUME_RANGE * ((float) message.getData2() / 127.0f);

		for (Player player : listeners)
			player.playSound(player.getLocation(), Sound.NOTE_PIANO, volume, pitch);
	}

	@Override
	public void close()
	{
		listeners.clear();
	}
}
