package leolee3914.real_lift;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;

public class MovingLift {

	public HashMap<UUID, Entity> insideEntities = new HashMap<>();

	public LinkedHashMap<Integer, QueueEntry> queue = new LinkedHashMap<>();

	public boolean hasQueue = false;

	public Location position;
	public RealLiftMain.Movement movement;
	public @Nullable Integer waiting;
	public boolean moving;
	public boolean playSound;
	public int targetY;
	public boolean unset;
	public final RealLiftMain.LiftSize liftSize;
	public boolean fastMode;

	public MovingLift (
			Location position,
			RealLiftMain.Movement movement,
			@Nullable Integer waiting,
			boolean moving,
			boolean playSound,
			int targetY,
			boolean unset,
			RealLiftMain.LiftSize liftSize,
			boolean fastMode
	) {
		this.position = position;
		this.movement = movement;
		this.waiting = waiting;
		this.moving = moving;
		this.playSound = playSound;
		this.targetY = targetY;
		this.unset = unset;
		this.liftSize = liftSize;
		this.fastMode = fastMode;
	}

}