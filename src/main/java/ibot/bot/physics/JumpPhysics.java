package ibot.bot.physics;

import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.input.Car;

public class JumpPhysics {

	// https://wikimedia.org/api/rest_v1/media/math/render/svg/8876516f71a06f98f87c759f9df9f4100b1e7072

	public static double timeZ(DataBot bot, double z, double holdTime){
		Car car = bot.car;
		double gravity = treatGravity(bot);
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

	public static double maxZ(DataBot bot, double holdTime, boolean useInitialVelocity){
		Car car = bot.car;
		double gravity = treatGravity(bot);
		double initialVelocity = ((useInitialVelocity ? car.velocity.dot(car.orientation.up) : 0)
				+ Constants.JUMP_IMPULSE);
		double s = (initialVelocity * holdTime + 0.5 * (gravity + Constants.JUMP_ACCELERATION) * Math.pow(holdTime, 2));
//		initialVelocity += (gravity * holdTime + Constants.JUMP_ACCELERATION * (holdTime - Constants.DT));
		initialVelocity += ((gravity + Constants.JUMP_ACCELERATION) * holdTime);
		return s + (-Math.pow(initialVelocity, 2) / (2 * gravity));
	}

	public static double treatGravity(DataBot bot){
		return bot.car.orientation.up.z > Math.cos(Math.toRadians(2)) ? bot.gravity
				: bot.gravity * bot.car.orientation.up.z;
//		return bot.gravity * bot.car.orientation.up.z;
//		return bot.gravity;
	}

}
