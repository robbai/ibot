package ibot.input;

import rlbot.flat.PlayerInfo;
import ibot.vectors.Vector3;

public class CarOrientation extends Rotator {

	public final Vector3 forward, right, up;

	public CarOrientation(double roll, double pitch, double yaw, Vector3 forward, Vector3 up){
		super((float)roll, (float)pitch, (float)yaw);
		this.forward = forward;
		this.right = forward.cross(up);
		this.up = up;
	}

	public CarOrientation(CarOrientation orientation){
		this(orientation.roll, orientation.pitch, orientation.yaw, orientation.forward, orientation.up);
	}

	public static CarOrientation fromFlatbuffer(PlayerInfo playerInfo){
		return convert(playerInfo.physics().rotation().roll(), playerInfo.physics().rotation().pitch(),
				playerInfo.physics().rotation().yaw());
	}

	private static CarOrientation convert(double roll, double pitch, double yaw){
		double forwardX = -1 * Math.cos(pitch) * Math.cos(yaw);
		double forwardY = Math.cos(pitch) * Math.sin(yaw);
		double forwardZ = Math.sin(pitch);

		double upX = Math.cos(roll) * Math.sin(pitch) * Math.cos(yaw) + Math.sin(roll) * Math.sin(yaw);
		double upY = Math.cos(yaw) * Math.sin(roll) - Math.cos(roll) * Math.sin(pitch) * Math.sin(yaw);
		double upZ = Math.cos(roll) * Math.cos(pitch);

		return new CarOrientation(roll, pitch, yaw, new Vector3(forwardX, forwardY, forwardZ),
				new Vector3(upX, upY, upZ));
	}

}
