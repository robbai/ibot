package ibot.prediction;

import ibot.vectors.Vector3;

public class BallSlice extends Slice {

	public final Vector3 velocity;

	public BallSlice(Vector3 position, Vector3 velocity, double time){
		super(position, time);
		this.velocity = velocity;
	}

}
