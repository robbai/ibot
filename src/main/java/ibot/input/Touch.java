package ibot.input;

import ibot.vectors.Vector3;

public class Touch {

	public final Vector3 location, normal;

	public final int team, playerIndex;
	public final double time;

	public Touch(rlbot.flat.Touch rawTouch){
		this.location = new Vector3(rawTouch.location());
		this.normal = new Vector3(rawTouch.normal());

		this.team = rawTouch.team();
		this.playerIndex = rawTouch.playerIndex();
		this.time = rawTouch.gameSeconds();
	}

}
