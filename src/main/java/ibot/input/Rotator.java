package ibot.input;

import ibot.vectors.Vector3;

public class Rotator {

	public final float roll, pitch, yaw;

	public Rotator(double roll, double pitch, double yaw){
		super();
		this.roll = (float)roll;
		this.pitch = (float)pitch;
		this.yaw = (float)yaw;
	}

	public Rotator(Rotator rotator){
		this(rotator.roll, rotator.pitch, rotator.yaw);
	}

	public static Rotator fromAngularVelocity(CarOrientation orientation, rlbot.flat.Vector3 vector3){
		Vector3 angularVelocity = new Vector3(vector3);
		return new Rotator(
				orientation.forward.dot(angularVelocity),
				orientation.right.dot(angularVelocity),
				orientation.up.dot(angularVelocity)
				);
	}

}
