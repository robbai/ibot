package ibot.bot.step.steps;

import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.controls.Marvin;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class DriveStep extends Step {

	private final static double GOAL_SAFE_WIDTH = (Constants.GOAL_WIDTH - 150);

	public Vector3 target;
	private OptionalDouble targetTime = OptionalDouble.empty(), targetVelocity = OptionalDouble.empty();
	public boolean conserveBoost = false, dodge = true, dontBoost = false, reverse = true, gentleSteer = false;

	public DriveStep(Bundle bundle, Vector3 target){
		super(bundle);
		this.target = new Vector3(target);
	}

	public DriveStep(Bundle bundle, Vector2 target){
		this(bundle, target.withZ(Constants.CAR_HEIGHT));
	}

	public DriveStep(Bundle bundle){
		super(bundle);
	}

	@Override
	public Output getOutput(){
		Info info = this.bundle.info;
		DataPacket packet = this.bundle.packet;
		Car car = packet.car;

		this.setFinished(this.target.distance(car.position) < 60);

		Vector3 carTarget = this.target;

		// Escape goal.
		if(Math.abs(this.target.y) > Constants.PITCH_LENGTH_SOCCAR != Math
				.abs(car.position.y) > Constants.PITCH_LENGTH_SOCCAR){
			carTarget = this.target.withX(MathsUtils.clamp(this.target.x, -GOAL_SAFE_WIDTH, GOAL_SAFE_WIDTH));
		}

		// Escape wall.
		if(!car.onFlatGround && this.target.minus(car.position).dot(car.orientation.up) > 800){
			carTarget = carTarget.withZ(0).scale(0.5);
		}

		// Pre-process.
		Vector3 local = MathsUtils.local(car, carTarget);
		if(!car.onFlatGround && Math.abs(local.z) > 300){
			carTarget = carTarget.withZ(0);
			local = MathsUtils.local(car, carTarget);
		}
		Vector3 localNormalised = local.normalised();
		double forwardDot = Vector2.Y.dot(localNormalised.flatten().normalised());
		double velocityTowards = car.velocity.dot(carTarget.minus(car.position).normalised());
		double velocityStraight = (car.forwardVelocityAbs / car.velocity.magnitude());

		// Turning.
		double radians = Vector2.Y.correctionAngle(local.flatten());
		boolean reverse;
		if(Math.abs(car.position.y) > Constants.PITCH_LENGTH_SOCCAR
				&& Math.signum(car.position.y) * carTarget.y < car.position.y){
			reverse = (car.orientation.forward.y * car.position.y > 0);
		}else{
			reverse = (car.forwardVelocity < (Math.cos(radians) < 0 ? 400 : -200));
		}
		reverse &= this.reverse;
		double reverseSign = (reverse ? -1 : 1);
		if(reverse){
			radians = MathsUtils.invertAngle(radians);
		}

		// Velocity.
		double maxTurnVel = DrivePhysics.maxSpeedForTurn(car, carTarget.setDistanceFrom(car.position,
				Math.min(car.onFlatGround ? 3000 : Double.MAX_VALUE, carTarget.distance(car.position))));
		double desiredVelocity = maxTurnVel;
		if(this.targetTime.isPresent()){
			double distance = carTarget.distance(car.position);
			desiredVelocity = Math.min(desiredVelocity, distance / (this.targetTime.getAsDouble() - car.time));
		}else if(this.targetVelocity.isPresent()){
			desiredVelocity = Math.copySign(Math.min(desiredVelocity, Math.abs(this.targetVelocity.getAsDouble())),
					desiredVelocity);
		}
		desiredVelocity *= reverseSign;

		// Throttle.
		double throttle = Marvin.throttleVelocity(car.forwardVelocity, desiredVelocity,
				info.lastControls.getThrottle());

		// Boost.
		boolean boost = Marvin.boostVelocity(car.forwardVelocity, desiredVelocity, info.lastControls.holdBoost());
		boost &= !car.isSupersonic;
		boost &= (throttle > 0 && forwardDot > 0.5);

		// Dodge.
		if(boost || car.forwardVelocity < 0){
			if(this.conserveBoost){
				boost &= ((!car.hasWheelContact && car.boost > 70)
						|| (Constants.SUPERSONIC_VELOCITY - car.forwardVelocity)
								/ Constants.BOOST_GROUND_ACCELERATION < (car.boost - 40) / Constants.BOOST_USAGE);
			}
			double dodgeDistance = DrivePhysics.estimateDodgeDistance(car);
			double flatDistance = local.flatten().magnitude();
			boolean commitKickoff = packet.isKickoffPause && info.commit;
			if((info.getTimeOnGround() > 0.05 || commitKickoff) && dodge
					&& (commitKickoff || Math.abs(velocityTowards) > (car.forwardVelocity < 0 ? 800 : 1250))
					&& (dodgeDistance < flatDistance || car.forwardVelocity < 0)
					&& (!commitKickoff || dodgeDistance > flatDistance - 300)){
				if(car.forwardVelocity < 0 && Math.abs(radians) < Math.toRadians(20)){
					return new HalfFlipStep(bundle);
				}else if(commitKickoff || ((!boost || car.boost < 10) && velocityStraight > 0.9
						&& Math.abs(radians) < Math.toRadians(30)
						&& car.forwardVelocity + Constants.DODGE_IMPULSE < desiredVelocity)){
					return new FastDodgeStep(bundle, carTarget.minus(car.position));
				}
			}
		}

		boost &= !this.dontBoost;

		// Wavedash.
		boolean wavedash = (!car.hasWheelContact && !car.hasDoubleJumped && car.orientation.up.z > 0.65
				&& info.timeToHitGround < 0.275);
		boolean wavedashTime = (wavedash && info.timeToHitGround < 0.07);
		boost &= !wavedash;

		// Boost back to ground.
		boolean boostDown = (info.timeToHitGround > 1.2 && car.boost > 0);
		if(boostDown){
			boost = car.orientation.forward.z < -0.8;
		}

		// Handbrake.
		boolean handbrake = (velocityStraight < 0.8 && info.getTimeOnGround() < 0.3)
				|| (car.onFlatGround && (Math.abs(forwardDot) < 0.5 && car.forwardVelocityAbs > 300
						&& velocityStraight > 0.9 && !this.gentleSteer)
//				|| (maxTurnVel < 600 && car.forwardVelocityAbs < 800)
				);
//		handbrake &= (car.angularVelocity.yaw * radians * reverseSign < 0 && car.forwardVelocity * throttle > 0);

		return new Controls().withThrottle(throttle).withBoost(boost).withHandbrake(handbrake)
				.withSteer(gentleSteer ? radians * -1.5
						: Math.pow(-radians - car.angularVelocity.yaw * Constants.DT * 2, 3) * 10000)
				.withOrient(car.hasWheelContact || wavedashTime
						? new double[] { 0, wavedashTime ? -Math.signum(Math.cos(radians)) : 0, 0 }
						: AirControl.getRollPitchYaw(car,
								wavedash ? carTarget.minus(car.position).flatten()
										.withAngleZ(Math.toRadians(20 * reverseSign))
										: (boostDown ? Vector3.Z.scale(-1) : carTarget.minus(car.position).withZ(0))))
				.withJump(wavedashTime);
	}

	@Override
	public int getPriority(){
		return Priority.DRIVE;
	}

	public DriveStep withTargetVelocity(OptionalDouble targetVelocity){
		this.targetVelocity = targetVelocity;
		return this;
	}

	public DriveStep withTargetVelocity(double targetVelocity){
		return this.withTargetVelocity(OptionalDouble.of(targetVelocity));
	}

	public DriveStep withTargetTime(OptionalDouble targetTime){
		this.targetTime = targetTime;
		return this;
	}

	public DriveStep withTargetTime(double targetTime){
		return this.withTargetTime(OptionalDouble.of(targetTime));
	}

}
