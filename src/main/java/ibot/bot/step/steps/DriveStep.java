package ibot.bot.step.steps;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.controls.Marvin;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.physics.Car1D;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.physics.Routing;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class DriveStep extends Step {

	private final static double GOAL_SAFE_WIDTH = (Constants.GOAL_WIDTH - 150);

	public Vector3 target;
	private OptionalDouble targetTime = OptionalDouble.empty(), targetVelocity = OptionalDouble.empty();
	public double popDistance = 100;
	public boolean conserveBoost = false, dodge = true, dontBoost = false, reverse = true, gentleSteer = false,
			ignoreRadius = false, routing = true, canPop = false;

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
		Pencil pencil = this.bundle.pencil;

		if(this.canPop){
			Vector3 liftedTarget = this.target.withZ(Math.max(this.target.z, Constants.CAR_HEIGHT));
			double distance = liftedTarget.distance(car.position);
			pencil.stackRenderString((int)distance + "uu", Color.YELLOW);
			pencil.renderer.drawRectangle3d(Color.YELLOW, car.position.setDistanceFrom(liftedTarget, this.popDistance),
					8, 8, true);
			if(distance < this.popDistance)
				return new PopStack();
		}

		Vector3 carTarget = this.target;

		// Routing.
		boolean route = false;
		if(!this.targetVelocity.isPresent() && !info.isKickoff && this.routing && car.boost < 90){
			double time = this.targetTime.isPresent() ? this.targetTime.getAsDouble()
					: new Car1D(car, carTarget).stepDisplacement(1, true, carTarget.distance(car.position)).getTime()
							+ Routing.estimateTurnTime(car, carTarget, false);
			Vector2 routeTarget = Routing.quickRoute(car, new Slice(carTarget, time));
			if(!routeTarget.equals(carTarget.flatten())){
				route = true;
				carTarget = routeTarget.withZ(Constants.CAR_HEIGHT);
				this.bundle.pencil.renderer.drawLine3d(Color.MAGENTA, car.position, carTarget);
			}
		}

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

		// Steering and direction.
		double radians = Vector2.Y.correctionAngle(local.flatten());
		boolean reverse = (this.reverse && !route && fasterBackwards(car, carTarget));
		double reverseSign = (reverse ? -1 : 1);
		if(reverse)
			radians = MathsUtils.invertAngle(radians);
		double steer;
		if(this.gentleSteer){
			steer = radians * -2;
		}else{
			steer = Math.pow(-radians - car.angularVelocity.yaw * reverseSign * Constants.DT * 2, 3) * 10000;
		}

		// Velocity.
		double maxSpeedTurn = (this.ignoreRadius ? Constants.MAX_CAR_VELOCITY
				: (DrivePhysics.maxSpeedForTurn(car, carTarget)));
		double desiredVelocity = maxSpeedTurn;
		if(this.targetTime.isPresent()){
			double distance = carTarget.distance(car.position);
			desiredVelocity = Math.min(desiredVelocity, distance / (this.targetTime.getAsDouble() - car.time));
		}else if(this.targetVelocity.isPresent()){
			desiredVelocity = Math.copySign(Math.min(desiredVelocity, Math.abs(this.targetVelocity.getAsDouble())),
					desiredVelocity);
		}
		if(forwardDot < 0){
			// Big turn!
			desiredVelocity = Routing.findQuickerTurningVelocity(desiredVelocity,
					car.forwardVelocityAbs < maxSpeedTurn || Math.abs(desiredVelocity) < maxSpeedTurn);
		}
		desiredVelocity *= reverseSign;
//		this.bundle.pencil.stackRenderString((int)car.forwardVelocity + " -> " + (int)desiredVelocity + "uu/s",
//				Color.BLACK);

		// Throttle and boost.
		double throttle = Marvin.throttleVelocity(car.forwardVelocity, desiredVelocity,
				info.lastControls.getThrottle());
		boolean boost = Marvin.boostVelocity(car.forwardVelocity, desiredVelocity, info.lastControls.holdBoost())
				&& !car.isSupersonic;

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
			if((info.getTimeOnGround() > 0.075 || commitKickoff) && this.dodge
					&& (commitKickoff || Math.abs(velocityTowards) > (car.forwardVelocity < 0 ? 700 : 1250))
					&& (dodgeDistance < flatDistance
//							|| (route && MathsUtils.localAngleBetween(car, carTarget, this.target) < 0.35)
					) && (!commitKickoff || dodgeDistance > flatDistance - 400)){
				if(car.forwardVelocity < 0 && Math.abs(radians) < 0.25 && reverse){
					return new HalfFlipStep(this.bundle, carTarget.minus(car.position));
				}else if((commitKickoff || ((!boost || car.boost < 10 || this.dontBoost) && velocityStraight > 0.9
						&& Math.abs(radians) < 0.35
						&& (car.forwardVelocity + Constants.DODGE_IMPULSE < desiredVelocity || car.boost < 1)))
						&& !reverse){
					return new FastDodgeStep(this.bundle, carTarget.minus(car.position));
				}
			}
		}

		boost &= !this.dontBoost;

		// Wavedash.
		boolean wavedash = (!car.hasWheelContact && !car.hasDoubleJumped && car.orientation.up.z > 0.65
				&& info.timeToHitGround < 0.275);
		boolean wavedashTime = (wavedash && info.timeToHitGround < 0.06);
		boost &= !wavedash;

		// Boost back to ground.
		boolean boostDown = (info.timeToHitGround > 1.3 && car.boost > 0);
		if(boostDown){
			boost = car.orientation.forward.z < -0.8;
		}

		// Handbrake.
		boolean handbrake = (velocityStraight < 0.8 && info.getTimeOnGround() < 0.3)
				|| (car.onFlatGround && (Math.abs(forwardDot) < 0.5 && car.forwardVelocityAbs > 300
						&& velocityStraight > 0.9 && !this.gentleSteer)
						|| (maxSpeedTurn < 800 && car.forwardVelocityAbs < 1000));
//		handbrake &= (car.angularVelocity.yaw * radians * reverseSign < 0 && car.forwardVelocity * throttle > 0);
//		handbrake &= MathsUtils.local(car.orientation, car.velocity).withZ(0).dot(MathsUtils.local(car, target)) > 0;

		return new Controls().withThrottle(throttle).withBoost(boost).withHandbrake(handbrake).withSteer(steer)
				.withOrient(car.hasWheelContact || wavedashTime
						? new double[] { 0, wavedashTime ? -Math.signum(Math.cos(radians)) : 0, 0 }
						: AirControl.getRollPitchYaw(car,
								wavedash ? carTarget.minus(car.position).flatten()
										.withAngleZ(Math.toRadians(20 * reverseSign))
										: (boostDown ? Vector3.Z.scale(-1) : carTarget.minus(car.position).withZ(0))))
				.withJump(wavedashTime);
	}

	private static boolean fasterBackwards(Car car, Vector3 carTarget){
		final double DT = 0.05;
		double distance = car.position.distance(carTarget);
		double radians = Vector2.Y.angle(MathsUtils.local(car, carTarget).flatten());
		double initialVelocity = car.velocity.dot(carTarget.minus(car.position).normalised())
				* Math.signum(car.forwardVelocity);
		double backwardsTime = new Car1D(car.time, 0, -initialVelocity, car.boost).withDT(DT)
				.stepDisplacement(-1, false, -distance).getTime()
				+ Routing.estimateTurnTimeRadians(MathsUtils.invertAngle(radians));
		double forwardsTime = new Car1D(car.time, 0, initialVelocity, car.boost).withDT(DT)
				.stepDisplacement(1, true, distance).getTime() + Routing.estimateTurnTimeRadians(radians);
		return (backwardsTime < forwardsTime);
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

	public void manipulateControls(Controls controls){
		controls.withBoost(controls.holdBoost() && !this.dontBoost);
	}

}
