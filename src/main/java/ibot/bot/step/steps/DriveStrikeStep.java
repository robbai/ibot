package ibot.bot.step.steps;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.controls.Handling;
import ibot.bot.input.Bundle;
import ibot.bot.input.Pencil;
import ibot.bot.intercept.Intercept;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.physics.JumpPhysics;
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

public class DriveStrikeStep extends Step {

	/*
	 * Constants.
	 */
	public static final double MIN_JUMP_TIME = (Constants.DT * 2), DODGE_TIME = (Constants.DT * 6);

	public final Intercept intercept;

	private final Vector3 enemyGoal;

	private final boolean curve;

	private final double holdTime, startUpZ;

	private OptionalDouble jumpStart = OptionalDouble.empty(), radians = OptionalDouble.empty();

	private boolean go = false, lastGo = go;

	private double lastGoTimeChange;

	private boolean doubleJump;

	public DriveStrikeStep(Bundle bundle, Intercept intercept, Vector3 enemyGoal, boolean doubleJump){
		super(bundle);
		this.intercept = intercept;
		this.enemyGoal = enemyGoal;

		this.doubleJump = doubleJump;

		DataPacket packet = bundle.packet;
		Car car = packet.car;

//		double angle = intercept.intersectPosition.minus(car.position).flatten()
//				.angle(enemyGoal.minus(car.position).flatten());
////		this.curve = (angle > Math.toRadians(30) && car.onFlatGround);
//		this.curve = car.onFlatGround;
//		this.curve = car.onFlatGround && !car.correctSide(intercept.position);
		this.curve = false;

		if(this.doubleJump){
			this.holdTime = Constants.JUMP_MAX_HOLD;
			this.go = true;
		}else{
			double z = MathsUtils.local(car, intercept.intersectPosition).z;
			double minZ = JumpPhysics.maxZ(car, packet.gravity, 0, false, doubleJump);
			double maxZ = JumpPhysics.maxZ(car, packet.gravity, Constants.JUMP_MAX_HOLD, false, doubleJump);
			this.holdTime = MathsUtils.clamp(
					MathsUtils.lerp(0, Constants.JUMP_MAX_HOLD, Math.pow((z - minZ) / (maxZ - minZ), 2)), MIN_JUMP_TIME,
					Constants.JUMP_MAX_HOLD);
		}

		this.startUpZ = car.orientation.up.z;
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		double time = packet.time;
		double timeLeft = (this.intercept.time - time);

		Car car = packet.car;

		Vector3 localIntercept = MathsUtils.local(car, this.intercept.intersectPosition);

		// Calculations.
		double fullDistance = localIntercept.flatten().magnitude();
		double targetSpeed = (fullDistance / timeLeft);
		double maxZ = JumpPhysics.maxZ(packet.car, packet.gravity, this.holdTime, true, this.doubleJump);
		double jumpTime = JumpPhysics.timeZ(packet, Math.min(localIntercept.z, maxZ), this.holdTime, this.doubleJump);
		double driveTime = Math.max(0, timeLeft - jumpTime);

		this.go |= this.curve;
		if(!this.go){
			double goNecessaryTime = (car.forwardVelocityAbs / Constants.BRAKE_ACCELERATION);
			boolean nowGo = targetSpeed >= DrivePhysics.maxVelocity(car.forwardVelocity, car.boost,
					timeLeft - jumpTime - goNecessaryTime - Constants.DT * 2);
			if(nowGo != this.lastGo){
				this.lastGoTimeChange = time;
				this.lastGo = nowGo;
			}
			this.go = (nowGo && time - this.lastGoTimeChange > 0);
		}

		Vector3 localVelocity = MathsUtils.local(car.orientation, car.velocity);

		pencil.stackRenderString(MathsUtils.round(timeLeft, 3) + "s", timeLeft < 0 ? Color.RED : Color.WHITE);
		pencil.stackRenderString(MathsUtils.round(this.holdTime, 3) + "s", this.go ? Color.GREEN : Color.RED);

		this.setFinished(timeLeft <= (car.hasDoubleJumped && !this.doubleJump ? -0.6 : 0)
				|| Math.abs(this.startUpZ - car.orientation.up.z) > 0.4);
		// this.setFinished(timeLeft <= (car.hasDoubleJumped ? -0.6 : 0));

		if(!this.jumpStart.isPresent()){
			pencil.stackRenderString(MathsUtils.round(jumpTime, 3) + "s", Color.WHITE);
			pencil.stackRenderString((int)targetSpeed + "uu/s", Color.WHITE);

			Vector3 freeCar = localVelocity.scale(jumpTime);
			Vector3 globalCar = MathsUtils.global(car, freeCar);
			Vector3 floor = this.intercept.intersectPosition.withZ(car.position.z);
			pencil.renderer.drawLine3d(pencil.colour, car.position, globalCar);
			pencil.renderer.drawLine3d(pencil.colour, this.intercept.intersectPosition, floor);
			pencil.renderer.drawLine3d(pencil.altColour, globalCar, floor);

			double accuracy = freeCar.minus(localIntercept).flatten().magnitude();
			if(timeLeft - Constants.DT <= jumpTime && (!this.doubleJump || accuracy < 80)){
				this.jumpStart = OptionalDouble.of(time);
			}
		}

		// Crosshair.
		final double size = 50;
		Vector3 line1 = this.intercept.intersectPosition.minus(car.position).withZ(0);
		Vector3 line2 = line1.flatten().rotate(Math.PI / 2).withZ(0).scaleToMagnitude(size);
		pencil.renderer.drawLine3d(pencil.altColour, this.intercept.intersectPosition.plus(line2),
				this.intercept.intersectPosition.minus(line2));
		line1 = line1.cross(line2).scaleToMagnitude(size);
		pencil.renderer.drawLine3d(pencil.altColour, this.intercept.intersectPosition.plus(line1),
				this.intercept.intersectPosition.minus(line1));

		if(this.jumpStart.isPresent()){
			double timeJumping = (time - this.jumpStart.getAsDouble());
			boolean dodgeSoon = (timeLeft < DODGE_TIME + Constants.DT);
			boolean dodgeNow = (timeLeft < DODGE_TIME);

			pencil.stackRenderString(MathsUtils.round(timeLeft, 3) + "s", dodgeSoon ? Color.YELLOW : Color.WHITE);

			if(this.doubleJump && timeJumping > this.holdTime + JumpStep.DOUBLE_JUMP_DELAY && !car.hasDoubleJumped){
				return new Controls().withJump(true);
			}

			if(!dodgeSoon){
				Vector3 desiredForward;
				if(!this.doubleJump){
					desiredForward = this.intercept.intersectPosition.plus(new Vector3(0, 0, 75)).minus(car.position);
				}else{
					desiredForward = this.intercept.position.minus(car.position);
				}

				pencil.renderer.drawLine3d(Color.RED, car.position,
						car.position.plus(desiredForward.scaleToMagnitude(200)));

				double[] orient = AirControl.getRollPitchYaw(car, desiredForward);
				return new Controls().withJump(timeJumping < this.holdTime).withOrient(orient);
			}

			Vector3 localInterceptDodge = MathsUtils.local(car, getDodgeTarget(this.intercept));
			if(!this.radians.isPresent()){
				this.radians = OptionalDouble.of(Vector2.Y.correctionAngle(localInterceptDodge.flatten()));
			}
			return new Controls().withJump(dodgeNow).withPitch(-Math.cos(this.radians.getAsDouble()))
					.withRoll(-Math.sin(this.radians.getAsDouble()) * 2);
		}

		// Aim.
		Vector3 target = this.intercept.intersectPosition;
		if(this.curve){
			double distance = car.position.distance(target);
			distance *= MathsUtils.clamp((driveTime - 0.2) * 0.7, 0, 0.6);
			if(distance > 100){
				target = target.plus(this.intercept.getOffset().scaleToMagnitude(distance)).clamp();
				pencil.stackRenderString((int)distance + "uu", Color.MAGENTA);
				pencil.renderer.drawLine3d(Color.MAGENTA, car.position, target.withZ(car.position.z));
			}
		}

		Controls controls = (Controls)Handling.driveVelocity(this.bundle, target, false, false, targetSpeed);
		if(!this.go){
			if(Math.abs(controls.getSteer()) < 0.2)
				controls.withThrottle(10 - car.forwardVelocity);
			controls.withBoost(false);
		}
		return controls;
	}

	public static Vector3 getDodgeTarget(Intercept intercept){
		return intercept.intersectPosition.lerp(intercept.position, 0.3);
	}

	@Override
	public int getPriority(){
		return Priority.STRIKE;
	}

}
