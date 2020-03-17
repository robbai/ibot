package ibot.bot.controls;

import ibot.input.Car;
import ibot.input.CarOrientation;
import ibot.vectors.Vector3;

public class AirControl {

	/**
	 * https://github.com/DomNomNom/RocketBot/blob/32e69df4f2841501c5f1da97ce34673dccb670af/NomBot_v1.5/NomBot_v1_5.py#L56-L103
	 */
	public static double[] getRollPitchYaw(Car car, Vector3 desiredForward, Vector3 desiredRoof, boolean useRoof){
		CarOrientation orientation = car.orientation;
		
		desiredForward = desiredForward.normalised();
		desiredRoof = desiredRoof.normalised();
		
	    Vector3 desiredFacingAngVel = car.orientation.forward.cross(desiredForward);
	    Vector3 desiredUpVel = car.orientation.up.cross(desiredRoof);

//	    double pitch = desiredFacingAngVel.dot(orientation.right);
	    double pitch;
	    if(useRoof){
	    	pitch = desiredUpVel.dot(orientation.right);
	    }else{
	    	pitch = desiredFacingAngVel.dot(orientation.right);
	    }
	    double yaw = -desiredFacingAngVel.dot(orientation.up);
	    double roll = desiredUpVel.dot(orientation.forward);

	    double pitchVel = car.angularVelocity.pitch;
	    double yawVel = -car.angularVelocity.yaw;
	    double rollVel = car.angularVelocity.roll;

	    if(orientation.up.dot(desiredRoof) < -0.8 && orientation.forward.dot(desiredForward) > 0.8){
	        if(roll == 0) roll = 1;
	        roll *= Math.pow(10, 10);
	    }
	    if(orientation.forward.dot(desiredForward) < -0.8){
	    	if(pitch == 0) pitch = 1;
	    	pitch *= Math.pow(10, 10);
	    }

	    roll  = 5 * roll  + 0.30 * rollVel;
	    yaw   = 5 * yaw   + 0.70 * yawVel;
	    pitch = 5 * pitch + 0.90 * pitchVel;

	    if(orientation.forward.dot(desiredForward) < 0){
	        roll = 0;
	    }

//	    return new double[] {roll, pitch, yaw};
	    return new double[] {Math.signum(roll), Math.signum(pitch), Math.signum(yaw)};
//	    return new double[] {Marvin.curve1(roll), Marvin.curve1(pitch), Marvin.curve1(yaw)};
//	    final double threshold = 0.1;
//	    return new double[] {
//	    		Math.abs(roll) > threshold ? Math.signum(roll) : roll,
//	    		Math.abs(pitch) > threshold ? Math.signum(pitch) : pitch, 
//	    		Math.abs(yaw) > threshold ? Math.signum(yaw) : yaw
//	    };
	}
	
	public static double[] getRollPitchYaw(Car car, Vector3 desiredForward){
//		final double border = 500;
////		Vector3 desiredRoof = car.orientation.right.scale(Math.signum(car.position.y * car.sign));
//		Vector3 desiredRoof = car.velocity;
//		Vector3 soonPosition = car.position.plus(car.velocity.scale(0.15));
//		if(soonPosition.z < border){
//			desiredRoof = Vector3.Z;
//		}else if(soonPosition.z > Constants.CEILING - border){
//			desiredRoof = Vector3.Z.scale(-1);
//		}else if(Math.abs(soonPosition.x) > Constants.PITCH_WIDTH - border){
//			desiredRoof = Vector3.X.scale(-Math.signum(soonPosition.x));
//		}else if(Math.abs(soonPosition.y) > Constants.PITCH_LENGTH - border){
//			desiredRoof = Vector3.Y.scale(-Math.signum(soonPosition.y));
//		}
		
		Vector3 desiredRoof = Vector3.Z;
		
		return getRollPitchYaw(car, desiredForward, desiredRoof, false);
	}
	
	public static double[] getRollPitchYaw(Car car, Vector3 desiredForward, Vector3 desiredRoof){
		return getRollPitchYaw(car, desiredForward, desiredRoof, false);
	}

}