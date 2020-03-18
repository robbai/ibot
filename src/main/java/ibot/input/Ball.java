package ibot.input;

import rlbot.flat.BallInfo;
import rlbot.flat.Physics;
import ibot.vectors.Vector3;

public class Ball {

	public final Vector3 position, velocity, angularVelocity;
	public final Touch latestTouch;

	public final double time;

	public Ball(BallInfo rawBall, double time){
		if(rawBall != null){
			Physics physics = rawBall.physics();
			this.position = new Vector3(physics.location());
			this.velocity = new Vector3(physics.velocity());
			this.angularVelocity = new Vector3(physics.angularVelocity());

			this.latestTouch = (rawBall.latestTouch() == null ? null : new Touch(rawBall.latestTouch()));
		}else{
			this.position = new Vector3();
			this.velocity = new Vector3();
			this.angularVelocity = new Vector3();

			this.latestTouch = null;
		}

		this.time = time;
	}

}
