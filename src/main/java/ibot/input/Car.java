package ibot.input;

import ibot.vectors.Vector3;
import rlbot.flat.Physics;

public class Car {

	public final Vector3 position, velocity, angularVelocityVector;
	public final Rotator angularVelocity;
	public final CarOrientation orientation;

	public final boolean hasWheelContact, isSupersonic, hasDoubleJumped, isDemolished, onFlatGround, onSuperFlatGround;
	public final double time, boost, sign, forwardVelocity, forwardVelocityAbs;
	public final int team, index;
	public final String name;

	public Car(rlbot.flat.PlayerInfo playerInfo, double time, int index){
		Physics physics = playerInfo.physics();
		this.position = new Vector3(physics.location());
		this.velocity = new Vector3(physics.velocity());
		this.angularVelocityVector = new Vector3(physics.angularVelocity());

		this.orientation = CarOrientation.fromFlatbuffer(playerInfo);
		this.forwardVelocity = this.orientation.forward.dot(this.velocity);
		this.forwardVelocityAbs = Math.abs(forwardVelocity);

		//      this.angularVelocity = new Vector3(physics.angularVelocity());
		this.angularVelocity = Rotator.fromAngularVelocity(this.orientation, physics.angularVelocity());

		this.boost = playerInfo.boost();
		this.isSupersonic = playerInfo.isSupersonic();
		this.team = playerInfo.team();
		this.sign = (this.team == 0 ? 1 : -1);
		this.hasWheelContact = playerInfo.hasWheelContact();
		this.time = time;
		this.index = index;
		this.hasDoubleJumped = playerInfo.doubleJumped();
		this.isDemolished = playerInfo.isDemolished();
		this.name = playerInfo.name();

		this.onFlatGround = (this.hasWheelContact && this.orientation.up.z > 0.75);
		this.onSuperFlatGround = (this.onFlatGround && this.orientation.up.z > 0.95);
	}

}
