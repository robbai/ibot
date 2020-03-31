package ibot.bot.physics;

import ibot.bot.actions.Jump;
import ibot.bot.utils.Constants;
import ibot.input.Car;
import ibot.input.DataPacket;

public class JumpPhysics {

	// https://wikimedia.org/api/rest_v1/media/math/render/svg/8876516f71a06f98f87c759f9df9f4100b1e7072

	public static double timeZ(DataPacket packet, double targetHeight, double holdTime, boolean doubleJump){
		Car car = packet.car;
		double gravity = packet.gravity;
		double initialVelocity = (car.velocity.dot(car.orientation.up) + Constants.JUMP_IMPULSE);
		double height = (initialVelocity * holdTime
				+ 0.5 * (gravity + Constants.JUMP_ACCELERATION) * Math.pow(holdTime, 2));
		if(targetHeight > 0 && targetHeight < height){
			double time1 = -((Math
					.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * targetHeight + Math.pow(initialVelocity, 2))
					+ initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			double time2 = ((Math
					.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * targetHeight + Math.pow(initialVelocity, 2))
					- initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			if(time1 < 0 || Double.isNaN(time1))
				return time2;
			if(time2 < 0 || Double.isNaN(time2))
				return time1;
			return Math.min(time1, time2);
		}
		double holdVelocity = (initialVelocity + (gravity + Constants.JUMP_ACCELERATION) * holdTime);
		if(doubleJump){ // TODO
			height += holdVelocity * Jump.DOUBLE_JUMP_DELAY + 0.5 * gravity * Math.pow(Jump.DOUBLE_JUMP_DELAY, 2);
			holdVelocity += (gravity * Jump.DOUBLE_JUMP_DELAY) + Constants.JUMP_IMPULSE;
		}
		targetHeight -= height;
		double time1 = -((Math.sqrt(2 * gravity * targetHeight + Math.pow(holdVelocity, 2)) + holdVelocity) / gravity);
		double time2 = ((Math.sqrt(2 * gravity * targetHeight + Math.pow(holdVelocity, 2)) - holdVelocity) / gravity);
		if(time1 < 0 || Double.isNaN(time1))
			return time2;
		if(time2 < 0 || Double.isNaN(time2))
			return time1;
		return Math.min(time1, time2);
	}

	public static double maxZ(Car car, double gravity, double holdTime, boolean useInitialVelocity, boolean doubleJump){
		final double acceleration = (gravity + Constants.JUMP_ACCELERATION);
		double velocity = ((useInitialVelocity ? car.velocity.dot(car.orientation.up) : 0) + Constants.JUMP_IMPULSE);
		double height = (velocity * holdTime + 0.5 * acceleration * Math.pow(holdTime, 2));
		velocity += (acceleration * holdTime);
		if(doubleJump){
			height += velocity * Jump.DOUBLE_JUMP_DELAY + 0.5 * gravity * Math.pow(Jump.DOUBLE_JUMP_DELAY, 2);
			velocity += (gravity * Jump.DOUBLE_JUMP_DELAY) + Constants.JUMP_IMPULSE;
		}
		return height + (-Math.pow(velocity, 2) / (2 * gravity));
	}

}
