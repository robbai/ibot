package ibot.bot.actions;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.controls.Handling;
import ibot.bot.intercept.Intercept;
import ibot.bot.physics.JumpPhysics;
import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class DriveStrike extends Action {

	public static final double DODGE_TIME = (Constants.DT * 4);

	public final Intercept intercept;

	private final double holdTime, startUpZ;

	private OptionalDouble jumpStart;

	public DriveStrike(DataBot bot, Intercept intercept){
		super(bot);
		this.intercept = intercept;

		//		this.holdTime = MathsUtils.clamp(intercept.time - bot.secondsElapsed - DODGE_TIME, 0, Constants.JUMP_MAX_HOLD);

		double z = MathsUtils.local(bot.car, intercept.intersectPosition).z;
		double minZ = JumpPhysics.maxZ(bot, 0, false);
		double maxZ = JumpPhysics.maxZ(bot, Constants.JUMP_MAX_HOLD, false);
		this.holdTime = MathsUtils.clamp(
				MathsUtils.lerp(
						Constants.DT * 2, Constants.JUMP_MAX_HOLD,
						Math.pow((z - minZ) / (maxZ - minZ), 2)),
				0, Constants.JUMP_MAX_HOLD);

		this.jumpStart = OptionalDouble.empty();
		this.startUpZ = bot.car.orientation.up.z;
	}

	@Override
	public ControlsOutput getOutput(DataPacket packet){
		double time = packet.time;
		double timeLeft = (this.intercept.time - time);

		Car car = packet.car;

		Vector3 localInterceptBall = MathsUtils.local(car, this.intercept.position);
		Vector3 localIntercept = MathsUtils.local(car, this.intercept.intersectPosition);

		double fullDistance = localIntercept.flatten().magnitude();
		double targetSpeed = (fullDistance / timeLeft);

		Vector3 localVelocity = MathsUtils.local(car.orientation, car.velocity);

		bot.stackRenderString(MathsUtils.round(timeLeft, 3) + "s", timeLeft < 0 ? Color.RED : Color.WHITE);

		this.setFinished(timeLeft <= (car.hasDoubleJumped ? -0.6 : 0) || Math.abs(startUpZ - bot.car.orientation.up.z) > 0.4);
		//		this.setFinished(timeLeft <= (car.hasDoubleJumped ? -0.6 : 0));

		if(!this.jumpStart.isPresent()){
			double maxZ = JumpPhysics.maxZ(bot, this.holdTime, true);
			double jumpTime = JumpPhysics.timeZ(bot, Math.min(localIntercept.z, maxZ), this.holdTime);

			//			// Drive calculations.
			//			double driveTime = Math.max(0, timeLeft);
			//			double initialVelocity = localVelocity.withZ(0).dot(localIntercept.normalised());
			//			double finalVelocity = (2 * fullDistance - driveTime * initialVelocity) / (driveTime + 2 * jumpTime);
			//			//double driveDistance = (fullDistance - finalVelocity * peakTime);
			//			double acceleration = ((finalVelocity - initialVelocity) / driveTime);
			//			bot.stackRenderString((int)acceleration + "uu/s^2", Color.WHITE);
			//			targetSpeed = initialVelocity + acceleration * Constants.DT;
			bot.stackRenderString((int)targetSpeed + "uu/s", Color.WHITE);

			Vector3 freeCar = localVelocity.scale(jumpTime);
			Vector3 globalCar = MathsUtils.global(car, freeCar);
			Vector3 floor = this.intercept.intersectPosition.withZ(car.position.z);
			bot.renderer.drawLine3d(bot.colour, car.position, globalCar);
			bot.renderer.drawLine3d(bot.colour, this.intercept.intersectPosition, floor);
			bot.renderer.drawLine3d(bot.altColour, globalCar, floor);

//			if(freeCar.minus(localIntercept).flatten().magnitude() < 30){
//			if(timeLeft - Constants.DT <= jumpTime){
			if(timeLeft <= jumpTime && freeCar.minus(localIntercept).flatten().magnitude() < 60){
				this.jumpStart = OptionalDouble.of(time);
			}
		}

		// Crosshair.
		final double size = 50;
		Vector3 line1 = this.intercept.intersectPosition.minus(car.position).withZ(0);
		Vector3 line2 = line1.flatten().rotate(Math.PI / 2).withZ(0).scaleToMagnitude(size);
		bot.renderer.drawLine3d(bot.altColour, this.intercept.intersectPosition.plus(line2), this.intercept.intersectPosition.minus(line2));
		line1 = line1.cross(line2).scaleToMagnitude(size);
		bot.renderer.drawLine3d(bot.altColour, this.intercept.intersectPosition.plus(line1), this.intercept.intersectPosition.minus(line1));

		if(this.jumpStart.isPresent()){
			double timeJumping = (time - this.jumpStart.getAsDouble());
			boolean dodge = (timeLeft < DODGE_TIME);
			if(!dodge){
//				Vector3 desiredForward = MathsUtils.global(car, localVelocity.normalised().withZ(localIntercept.plus(MathsUtils.local(car.orientation, Vector3.Z.scale(100))).normalised().z));
//				Vector3 desiredRoof = car.position.minus(this.intercept.intersectPosition).normalised().lerp(Vector3.Z, 0.65);

				Vector3 desiredForward = this.intercept.intersectPosition.plus(new Vector3(0, 0, 100)).minus(car.position);

				bot.renderer.drawLine3d(Color.RED, car.position, car.position.plus(desiredForward.scaleToMagnitude(200)));
//				bot.renderer.drawLine3d(Color.GREEN, car.position, car.position.plus(desiredRoof.scaleToMagnitude(200)));

//				double[] orient = AirControl.getRollPitchYaw(car, desiredForward, desiredRoof, true);
				double[] orient = AirControl.getRollPitchYaw(car, desiredForward);
				return new ControlsOutput().withJump(timeJumping < this.holdTime).withOrient(orient);
			}
			double radians = Vector2.Y.correctionAngle(localInterceptBall.flatten());
			return new ControlsOutput().withJump(true).withPitch(-Math.cos(radians)).withYaw(-Math.sin(radians) * 2);
		}

		return (ControlsOutput)Handling.driveVelocity(bot, this.intercept.intersectPosition, false, false, targetSpeed);
	}

}
