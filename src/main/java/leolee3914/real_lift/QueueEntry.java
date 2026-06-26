package leolee3914.real_lift;

import org.bukkit.Location;

public class QueueEntry {

	public int buttonBlinkTimer = 0;

	public Location position;
	private final int targetY;

	public QueueEntry ( Location position, int targetY ) {
		this.position = position;
		this.targetY = targetY;
	}

	public int getTargetY () {
		return targetY;
	}

}