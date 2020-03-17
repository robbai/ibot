package ibot.bot.controls;

import java.util.OptionalDouble;

import ibot.bot.actions.FastDodge;
import ibot.bot.actions.HalfFlip;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.output.ControlsOutput;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class Handling {

	private final static double GOAL_SAFE_WIDTH = (Constants.GOAL_WIDTH - 150);

	public static Output drive(DataBot bot, Vector3 target, boolean dodge, boolean conserveBoost, OptionalDouble targetTime, OptionalDouble targetVelocity){
		Car car = bot.car;

		if(Math.abs(target.y) > Constants.PITCH_LENGTH_SOCCAR != Math.abs(car.position.y) > Constants.PITCH_LENGTH_SOCCAR){
			target = target.withX(MathsUtils.clamp(target.x, -GOAL_SAFE_WIDTH, GOAL_SAFE_WIDTH));
		}

		Vector3 local = MathsUtils.local(car, target);
		if(!car.onFlatGround && Math.abs(local.z) > 300){
			target = target.withZ(0);
			local = MathsUtils.local(car, target);
		}

		Vector3 localNormalised = local.normalised();

		double forwardDot = Vector2.Y.dot(localNormalised.flatten());

		double velocityTowards = car.velocity.dot(target.minus(car.position).normalised());
		double velocityStraight = (car.forwardVelocityAbs / car.velocity.magnitude());

		// Turning.
		double radians = Vector2.Y.correctionAngle(local.flatten());
		boolean reverse = (car.forwardVelocity < (Math.cos(radians) < 0 ? 400 : -200));
		double reverseSign = (reverse ? -1 : 1);
		if(reverse){
			radians = MathsUtils.invertAngle(radians);
		}

		double maxTurnVel = DrivePhysics.maxVelForTurn(car, target.setDistanceFrom(car.position, Math.min(3000, target.distance(car.position))));
		double desiredVelocity = maxTurnVel;
		if(targetTime.isPresent()){
			double distance = target.distance(car.position);
			desiredVelocity = Math.min(desiredVelocity, distance / (targetTime.getAsDouble() - car.time));
		}else if(targetVelocity.isPresent()){
			desiredVelocity = Math.min(desiredVelocity, targetVelocity.getAsDouble());
		}
		desiredVelocity *= reverseSign;

		// Throttle.
		double throttle = Marvin.throttleVelocity(car.forwardVelocity, desiredVelocity, bot.lastControls.getThrottle());

		// Boost.
		boolean boost = Marvin.boostVelocity(car.forwardVelocity, desiredVelocity, bot.lastControls.holdBoost());
		boost &= !car.isSupersonic;
//		boost &= (throttle > 0 && forwardDot > 0.9);
		boost &= (throttle > 0);
		if(boost || car.forwardVelocity < 0){
			if(conserveBoost){
				boost &= ((!car.hasWheelContact && car.boost > 70) || (Constants.SUPERSONIC_VELOCITY - car.forwardVelocity) / Constants.BOOST_GROUND_ACCELERATION < (car.boost - 40) / Constants.BOOST_USAGE);
			}
			double dodgeDistance = DrivePhysics.estimateDodgeDistance(car);
			double flatDistance = local.flatten().magnitude();
			boolean commitKickoff = bot.isKickoff && bot.commit;
			if((bot.getTimeOnGround() > 0.05 || commitKickoff) && dodge && (commitKickoff || Math.abs(velocityTowards) > (car.forwardVelocity < 0 ? 800 : 1250))
					&& (dodgeDistance < flatDistance || car.forwardVelocity < 0) && (!commitKickoff || dodgeDistance > flatDistance - 300)){
				if(car.forwardVelocity < 0 && Math.abs(radians) < Math.toRadians(30)){
					return new HalfFlip(bot);
				}else if(commitKickoff || ((!boost || car.boost < 10) && velocityStraight > 0.9 && Math.abs(radians) < Math.toRadians(30) && car.forwardVelocity + Constants.DODGE_IMPULSE < desiredVelocity)){
					return new FastDodge(bot, target.minus(car.position));
				}
			}
		}

		boolean wavedash = (!car.hasWheelContact && !car.hasDoubleJumped && car.orientation.up.z > 0.65);
		boolean wavedashTime = (wavedash && bot.timeToHitGround < 0.07);

		boolean boostDown = (bot.timeToHitGround > 1.05 && car.boost > 0);
		if(boostDown){
			boost = car.orientation.forward.z < -0.8;
		}

		// Handbrake.
		boolean handbrake = (velocityStraight < 0.8 && bot.getTimeOnGround() < 0.3) || (car.onFlatGround &&
				(Math.abs(forwardDot) < 0.5 && car.forwardVelocityAbs > 300 && velocityStraight > 0.9) || (maxTurnVel < 600 && car.forwardVelocityAbs < 800));
//		if(car.angularVelocity.yaw * radians > 0 || car.forwardVelocity * throttle < 0){
//			handbrake = false;
//		}

		return new ControlsOutput()
				.withThrottle(throttle)
				.withBoost(boost)
				.withHandbrake(handbrake)
//				.withSteer(Marvin.steerPoint(-radians, car.angularVelocity.yaw))
				.withSteer(radians * -3)
				.withOrient(car.hasWheelContact || wavedashTime ? new double[] {0, wavedashTime ? -Math.signum(Math.cos(radians)) : 0, 0} :
					AirControl.getRollPitchYaw(car, wavedash ? target.minus(car.position).flatten().withAngleZ(Math.toRadians(20 * reverseSign)) : (boostDown ? Vector3.Z.scale(-1) : target.minus(car.position).withZ(0))))
				.withJump(wavedashTime);
	}

	public static Output drive(DataBot bot, Vector3 target, boolean dodge, boolean conserveBoost){
		return drive(bot, target, dodge, conserveBoost, OptionalDouble.empty(), OptionalDouble.empty());
	}

	public static Output driveTime(DataBot bot, Vector3 target, boolean dodge, boolean conserveBoost, OptionalDouble targetTime){
		return drive(bot, target, dodge, conserveBoost, targetTime, OptionalDouble.empty());
	}

	public static Output driveTime(DataBot bot, Vector3 target, boolean dodge, boolean conserveBoost, double targetTime){
		return drive(bot, target, dodge, conserveBoost, OptionalDouble.of(targetTime), OptionalDouble.empty());
	}

	public static Output driveVelocity(DataBot bot, Vector3 target, boolean dodge, boolean conserveBoost, double velocity){
		return drive(bot, target, dodge, conserveBoost, OptionalDouble.empty(), OptionalDouble.of(velocity));
	}

}
