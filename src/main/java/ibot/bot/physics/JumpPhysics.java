package ibot.bot.physics;

import ibot.bot.step.steps.JumpStep;
import ibot.bot.utils.Constants;
import ibot.bot.utils.StaticClass;

public class JumpPhysics extends StaticClass {

	// https://wikimedia.org/api/rest_v1/media/math/render/svg/8876516f71a06f98f87c759f9df9f4100b1e7072

	public static double timeZ(double targetZ, double gravity, double holdTime, boolean doubleJump){
		double initialVelocity = Constants.JUMP_IMPULSE;
		double z = (initialVelocity * holdTime + 0.5 * (gravity + Constants.JUMP_ACCELERATION) * Math.pow(holdTime, 2));
		if(targetZ > 0 && targetZ < z){
			double time1 = -((Math
					.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * targetZ + Math.pow(initialVelocity, 2))
					+ initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			double time2 = ((Math
					.sqrt(2 * (gravity + Constants.JUMP_ACCELERATION) * targetZ + Math.pow(initialVelocity, 2))
					- initialVelocity) / (gravity + Constants.JUMP_ACCELERATION));
			return solutionT(time1, time2);
		}
		double holdVelocity = (initialVelocity + (gravity + Constants.JUMP_ACCELERATION) * holdTime);
		if(doubleJump){ // TODO
			z += holdVelocity * JumpStep.DOUBLE_JUMP_DELAY + 0.5 * gravity * Math.pow(JumpStep.DOUBLE_JUMP_DELAY, 2);
			holdVelocity += (gravity * JumpStep.DOUBLE_JUMP_DELAY) + Constants.JUMP_IMPULSE;
		}
		targetZ -= z;
		double time1 = -((Math.sqrt(2 * gravity * targetZ + Math.pow(holdVelocity, 2)) + holdVelocity) / gravity);
		double time2 = ((Math.sqrt(2 * gravity * targetZ + Math.pow(holdVelocity, 2)) - holdVelocity) / gravity);
		return solutionT(time1, time2) + holdTime;
	}

	public static double maxZ(double gravity, double holdTime, boolean doubleJump){
		final double acceleration = (gravity + Constants.JUMP_ACCELERATION);
		double velocity = Constants.JUMP_IMPULSE;
		double height = (velocity * holdTime + 0.5 * acceleration * Math.pow(holdTime, 2));
		velocity += (acceleration * holdTime);
		if(doubleJump){
			height += (velocity * JumpStep.DOUBLE_JUMP_DELAY + 0.5 * gravity * Math.pow(JumpStep.DOUBLE_JUMP_DELAY, 2));
			velocity += (gravity * JumpStep.DOUBLE_JUMP_DELAY) + Constants.JUMP_IMPULSE;
		}
		return height - Math.pow(velocity, 2) / (2 * gravity);
	}

	private static double solutionT(double time1, double time2){
		if(time1 < 0 || Double.isNaN(time1))
			return time2;
		if(time2 < 0 || Double.isNaN(time2))
			return time1;
		return Math.min(time1, time2);
	}

}
