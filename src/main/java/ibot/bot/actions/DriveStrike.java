package ibot.bot.actions;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.controls.Handling;
import ibot.bot.intercept.Intercept;
import ibot.bot.physics.DrivePhysics;
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

	/*
	 * Constants.
	 */
	public static final double MIN_JUMP_TIME = (Constants.DT * 2), DODGE_TIME = (Constants.DT * 5);

	public final Intercept intercept;

	private final Vector3 enemyGoal;

	private final boolean curve;

	private final double holdTime, startUpZ;

	private OptionalDouble jumpStart = OptionalDouble.empty(), radians = OptionalDouble.empty();

	private boolean go = false, lastGo = go;

	private double lastGoTimeChange;

	public DriveStrike(DataBot bot, Intercept intercept, Vector3 enemyGoal){
		super(bot);
		this.intercept = intercept;
		this.enemyGoal = enemyGoal;

		Car car = bot.car;
		double angle = intercept.intersectPosition.minus(car.position).flatten()
				.angle(enemyGoal.minus(car.position).flatten());
		this.curve = (angle > Math.toRadians(40) && car.onFlatGround);

		double z = MathsUtils.local(bot.car, intercept.intersectPosition).z;
		double minZ = JumpPhysics.maxZ(bot, 0, false);
		double maxZ = JumpPhysics.maxZ(bot, Constants.JUMP_MAX_HOLD, false);
		this.holdTime = MathsUtils.clamp(
				MathsUtils.lerp(0, Constants.JUMP_MAX_HOLD, Math.pow((z - minZ) / (maxZ - minZ), 2)), MIN_JUMP_TIME,
				Constants.JUMP_MAX_HOLD);

		this.startUpZ = bot.car.orientation.up.z;
	}

	@Override
	public ControlsOutput getOutput(DataPacket packet){
		double time = packet.time;
		double timeLeft = (this.intercept.time - time);

		Car car = packet.car;

		Vector3 localIntercept = MathsUtils.local(car, this.intercept.intersectPosition);

		// Calculations.
		double fullDistance = localIntercept.flatten().magnitude();
		double targetSpeed = (fullDistance / timeLeft);
		double maxZ = JumpPhysics.maxZ(bot, this.holdTime, true);
		double jumpTime = JumpPhysics.timeZ(bot, Math.min(localIntercept.z, maxZ), this.holdTime);
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

		bot.stackRenderString(MathsUtils.round(timeLeft, 3) + "s", timeLeft < 0 ? Color.RED : Color.WHITE);
		bot.stackRenderString(MathsUtils.round(this.holdTime, 3) + "s", this.go ? Color.GREEN : Color.RED);

		this.setFinished(
				timeLeft <= (car.hasDoubleJumped ? -0.6 : 0) || Math.abs(this.startUpZ - car.orientation.up.z) > 0.4);
		// this.setFinished(timeLeft <= (car.hasDoubleJumped ? -0.6 : 0));

		if(!this.jumpStart.isPresent()){
			bot.stackRenderString(MathsUtils.round(jumpTime, 3) + "s", Color.WHITE);
			bot.stackRenderString((int)targetSpeed + "uu/s", Color.WHITE);

			Vector3 freeCar = localVelocity.scale(jumpTime);
			Vector3 globalCar = MathsUtils.global(car, freeCar);
			Vector3 floor = this.intercept.intersectPosition.withZ(car.position.z);
			bot.renderer.drawLine3d(bot.colour, car.position, globalCar);
			bot.renderer.drawLine3d(bot.colour, this.intercept.intersectPosition, floor);
			bot.renderer.drawLine3d(bot.altColour, globalCar, floor);

			double accuracy = freeCar.minus(localIntercept).flatten().magnitude();
			if(timeLeft - Constants.DT <= jumpTime)
				System.out.println((int)accuracy + "uu");
			// if((timeLeft - Constants.DT <= jumpTime || car.forwardVelocity < 0) &&
			// accuracy < 50){
			if(timeLeft - Constants.DT <= jumpTime){
				this.jumpStart = OptionalDouble.of(time);
			}
		}

		// Crosshair.
		final double size = 50;
		Vector3 line1 = this.intercept.intersectPosition.minus(car.position).withZ(0);
		Vector3 line2 = line1.flatten().rotate(Math.PI / 2).withZ(0).scaleToMagnitude(size);
		bot.renderer.drawLine3d(bot.altColour, this.intercept.intersectPosition.plus(line2),
				this.intercept.intersectPosition.minus(line2));
		line1 = line1.cross(line2).scaleToMagnitude(size);
		bot.renderer.drawLine3d(bot.altColour, this.intercept.intersectPosition.plus(line1),
				this.intercept.intersectPosition.minus(line1));

		if(this.jumpStart.isPresent()){
			double timeJumping = (time - this.jumpStart.getAsDouble());
			boolean dodgeSoon = (timeLeft < DODGE_TIME + Constants.DT);
			boolean dodgeNow = (timeLeft < DODGE_TIME);

			bot.stackRenderString(MathsUtils.round(timeLeft, 3) + "s", dodgeSoon ? Color.YELLOW : Color.WHITE);

			if(!dodgeSoon){
				// Vector3 desiredForward = MathsUtils.global(car,
				// localVelocity.normalised().withZ(localIntercept.plus(MathsUtils.local(car.orientation,
				// Vector3.Z.scale(100))).normalised().z));
				// Vector3 desiredRoof =
				// car.position.minus(this.intercept.intersectPosition).normalised().lerp(Vector3.Z,
				// 0.65);

				Vector3 desiredForward = this.intercept.intersectPosition.plus(new Vector3(0, 0, 100))
						.minus(car.position);

				bot.renderer.drawLine3d(Color.RED, car.position,
						car.position.plus(desiredForward.scaleToMagnitude(200)));
				// bot.renderer.drawLine3d(Color.GREEN, car.position,
				// car.position.plus(desiredRoof.scaleToMagnitude(200)));

				// double[] orient = AirControl.getRollPitchYaw(car, desiredForward,
				// desiredRoof, true);
				double[] orient = AirControl.getRollPitchYaw(car, desiredForward);
				return new ControlsOutput().withJump(timeJumping < this.holdTime).withOrient(orient);
			}

			Vector3 localInterceptDodge = MathsUtils.local(car, getDodgeTarget(this.intercept));
			if(!this.radians.isPresent()){
				this.radians = OptionalDouble.of(Vector2.Y.correctionAngle(localInterceptDodge.flatten()));
			}
			return new ControlsOutput().withJump(dodgeNow).withPitch(-Math.cos(this.radians.getAsDouble()))
					.withRoll(-Math.sin(this.radians.getAsDouble()) * 2);
		}

		// Aim.
		Vector3 target = this.intercept.intersectPosition;
		if(this.curve){
			double distance = car.position.distance(target);
			distance *= MathsUtils.clamp((timeLeft - 1.5) * 0.6, 0, 0.6);
			if(distance > 100){
				target = target.plus(target.minus(this.enemyGoal).scaleToMagnitude(distance)).clamp();
				bot.stackRenderString((int)distance + "uu", Color.MAGENTA);
				bot.renderer.drawLine3d(Color.MAGENTA, car.position, target.withZ(car.position.z));
			}
		}

		ControlsOutput controls = (ControlsOutput)Handling.driveVelocity(bot, target, false, false, targetSpeed);
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

}
