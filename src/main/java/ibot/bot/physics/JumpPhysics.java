package ibot.bot.physics;

import ibot.bot.utils.Constants;
import ibot.input.Car;
import ibot.input.DataPacket;

public class JumpPhysics {

	// https://wikimedia.org/api/rest_v1/media/math/render/svg/8876516f71a06f98f87c759f9df9f4100b1e7072

	public static double timeZ(DataPacket packet, double z, double holdTime){
		Car car = packet.car;
		double gravity = packet.gravity;
		double initialVelocity = (car.velocity.dot(car.orientation.up) + Constants.JUMP_IMPULSE);
		double s = (initialVelocity * holdTime + 0.5 * (gravity + Constants.JUMP_ACCELERATION) * Math.pow(holdTime, 2));
		if(z > 0 && z < s){
			double time1 = -((Math.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * z + Math.pow(initialVelocity, 2))
					+ initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			double time2 = ((Math.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * z + Math.pow(initialVelocity, 2))
					- initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			if(time1 < 0 || Double.isNaN(time1))
				return time2;
			if(time2 < 0 || Double.isNaN(time2))
				return time1;
			return Math.min(time1, time2);
		}
		double holdVelocity = (initialVelocity + (gravity + Constants.JUMP_ACCELERATION) * holdTime);
		z -= s;
		double time1 = -((Math.sqrt(2 * gravity * z + Math.pow(holdVelocity, 2)) + holdVelocity) / gravity);
		double time2 = ((Math.sqrt(2 * gravity * z + Math.pow(holdVelocity, 2)) - holdVelocity) / gravity);
		if(time1 < 0 || Double.isNaN(time1))
			return time2;
		if(time2 < 0 || Double.isNaN(time2))
			return time1;
		return Math.min(time1, time2);
	}

	public static double maxZ(Car car, double gravity, double holdTime, boolean useInitialVelocity){
		double initialVelocity = ((useInitialVelocity ? car.velocity.dot(car.orientation.up) : 0)
				+ Constants.JUMP_IMPULSE);
		double initialHeight = (initialVelocity * holdTime
				+ 0.5 * (gravity + Constants.JUMP_ACCELERATION) * Math.pow(holdTime, 2));
		initialVelocity += ((gravity + Constants.JUMP_ACCELERATION) * holdTime);
		return initialHeight + (-Math.pow(initialVelocity, 2) / (2 * gravity));
	}

}
