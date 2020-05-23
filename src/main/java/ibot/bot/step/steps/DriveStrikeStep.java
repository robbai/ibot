package ibot.bot.step.steps;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.input.Pencil;
import ibot.bot.intercept.Intercept;
import ibot.bot.intercept.SeamIntercept;
import ibot.bot.physics.Car1D;
import ibot.bot.physics.JumpPhysics;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Plane;
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
	public static final double MIN_HOLD_TIME = (Constants.DT * 2), DODGE_TIME = (Constants.DT * 3);

	private OptionalDouble jumpStart = OptionalDouble.empty(), radians = OptionalDouble.empty();
	private boolean go = false, lastGo = go;
	private double lastGoTimeChange;
	private boolean doubleJump;

	private final double holdTime, gravityScale, jumpZ;
	public final Intercept intercept;
	private final DriveStep drive;
	private final boolean curve, wall;

	public DriveStrikeStep(Bundle bundle, Intercept intercept, boolean doubleJump){
		super(bundle);
		this.intercept = intercept;

		this.wall = (intercept instanceof SeamIntercept);
		this.doubleJump = doubleJump;

		DataPacket packet = bundle.packet;

//		this.curve = packet.car.onFlatGround && !this.wall;
		this.curve = false;

		this.jumpZ = (intercept.plane.getNormalDistance(intercept.position) - Constants.CAR_HEIGHT);
		this.gravityScale = Math.max(Math.abs(intercept.plane.normal.z), 0.01);

		if(this.doubleJump){
			this.holdTime = Constants.JUMP_MAX_HOLD;
			this.go = true;
		}else{
			double minZ = JumpPhysics.maxZ(packet.gravity * this.gravityScale, 0, doubleJump);
			double maxZ = JumpPhysics.maxZ(packet.gravity * this.gravityScale, Constants.JUMP_MAX_HOLD, doubleJump);
			this.holdTime = MathsUtils.clamp(
					MathsUtils.lerp(0, Constants.JUMP_MAX_HOLD, Math.pow((this.jumpZ - minZ) / (maxZ - minZ), 2)),
					MIN_HOLD_TIME, Constants.JUMP_MAX_HOLD);
//			this.go = this.wall;
			this.go = true;
		}

		this.drive = new DriveStep(bundle);
		this.drive.reverse = false;
		this.drive.dodge = false;
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		double time = packet.time;
		double timeLeft = (this.intercept.time - time);

		Car car = packet.car;
		Plane carPlane = Plane.asCar(car);
		Vector3 localIntercept = MathsUtils.local(car, this.intercept.intersectPosition);
		Vector3 freefall = car.position
				.plus(car.velocity.scale(timeLeft).plus(Vector3.Z.scale(0.5 * packet.gravity * Math.pow(timeLeft, 2))));

		// Calculations.
		double fullDistance;
		if(this.wall && carPlane.differentNormal(this.intercept.plane)){
			Vector3 seamPosition = ((SeamIntercept)this.intercept).seamPosition;
			fullDistance = (MathsUtils.local(car, seamPosition).flatten().magnitude()
					+ seamPosition.distance(this.intercept.position) - this.jumpZ);
		}else{
			fullDistance = localIntercept.flatten().magnitude();
		}
		double maxZ = JumpPhysics.maxZ(packet.gravity * this.gravityScale, this.holdTime, this.doubleJump);
		double jumpTime = JumpPhysics.timeZ(Math.min(this.jumpZ, maxZ), packet.gravity * this.gravityScale,
				this.holdTime, this.doubleJump);
		double driveTime = Math.max(0, timeLeft - jumpTime);
		double targetVelocity = fullDistance / timeLeft;

		this.go |= this.curve;
		if(!this.go){
			double goNecessaryTime = (car.forwardVelocityAbs / Constants.BRAKE_ACCELERATION);
			boolean nowGo = targetVelocity >= new Car1D(car)
					.stepTime(1, true, this.intercept.time - jumpTime - goNecessaryTime - Constants.DT * 2)
					.getVelocity();
			if(nowGo != this.lastGo){
				this.lastGoTimeChange = time;
				this.lastGo = nowGo;
			}
			this.go = (nowGo && time - this.lastGoTimeChange > 0);
		}

		pencil.stackRenderString("Left: " + MathsUtils.round(timeLeft, 3) + "s",
				timeLeft < 0 ? Color.RED : Color.WHITE);
		pencil.stackRenderString("Hold: " + MathsUtils.round(this.holdTime, 3) + "s",
				this.go ? Color.GREEN : Color.RED);

		this.setFinished(timeLeft <= (car.hasDoubleJumped && !this.doubleJump ? -0.6 : 0));

		if(!this.jumpStart.isPresent() && !this.intercept.plane.differentNormal(carPlane)){
			pencil.stackRenderString("Jump: " + MathsUtils.round(jumpTime, 3) + "s", Color.WHITE);
			pencil.stackRenderString((int)targetVelocity + "uu/s", Color.WHITE);

			Vector3 localFreefall = MathsUtils.local(car, freefall);
			Vector3 floor = this.intercept.intersectPosition.shadowOntoPlane(this.intercept.plane);
			pencil.renderer.drawLine3d(pencil.colour, car.position, freefall);
			pencil.renderer.drawLine3d(pencil.colour, this.intercept.intersectPosition, floor);
			pencil.renderer.drawLine3d(pencil.altColour, freefall, floor);

			// Render.
			Vector3 previousPosition = car.position,
					velocity = car.velocity.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE));
			double renderTime = 0, renderDeltaTime = Constants.DT * 3;
			while(renderTime <= timeLeft){
				velocity = velocity.plus(this.bundle.info.arena.getGravity().scale(renderDeltaTime));
				if(renderTime < this.holdTime)
					velocity = velocity.plus(car.orientation.up.scale(Constants.JUMP_ACCELERATION * renderDeltaTime));
				if(this.doubleJump && renderTime > AerialStep.DOUBLE_JUMP_TIME
						&& renderTime < AerialStep.DOUBLE_JUMP_TIME + renderDeltaTime)
					velocity = velocity.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE));
				velocity = velocity.scale(Math.min(1, Constants.MAX_CAR_VELOCITY / velocity.magnitude()));
				Vector3 position = previousPosition.plus(velocity.scale(renderDeltaTime));
				pencil.renderer.drawLine3d(Color.WHITE, previousPosition, position);
				previousPosition = position;
				renderTime += renderDeltaTime;
			}

//			boolean nowJump = (localFreefall.minus(localIntercept).flatten().magnitude() < 20);
//			boolean nowJump = (driveTime <= Constants.DT);
			double inaccuracy = previousPosition.distance(this.intercept.intersectPosition)
					- (Constants.BALL_RADIUS - Constants.CAR_HEIGHT);
			pencil.renderer.drawString3d((int)inaccuracy + "uu", Color.WHITE, this.intercept.intersectPosition, 2, 2);
			boolean nowJump = (inaccuracy < 15);
			if(nowJump){
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

			// Double-jump.
			if(this.doubleJump && timeJumping > this.holdTime + JumpStep.DOUBLE_JUMP_DELAY && !car.hasDoubleJumped){
				return new Controls().withJump(true);
			}

			// Orient.
			if(!dodgeSoon){
				Vector3 desiredForward = this.intercept.position.minus(freefall), desiredRoof;
				if(this.doubleJump){
					desiredRoof = Vector3.Z;
				}else{
					desiredRoof = freefall.minus(this.intercept.intersectPosition).normalised().lerp(Vector3.Z, 0.85);
				}

				pencil.renderer.drawLine3d(Color.RED, car.position,
						car.position.plus(desiredForward.scaleToMagnitude(200)));
				pencil.renderer.drawLine3d(Color.GREEN, car.position,
						car.position.plus(desiredRoof.scaleToMagnitude(200)));

				double[] orient = AirControl.getRollPitchYaw(car, desiredForward, desiredRoof);
				return new Controls().withJump(timeJumping < this.holdTime).withOrient(orient);
			}

//			Vector3 localInterceptDodge = MathsUtils.local(car, getDodgeTarget(this.intercept));
			if(!this.radians.isPresent()){
				this.radians = OptionalDouble
						.of(Vector2.Y.correctionAngle(MathsUtils.local(car, this.intercept.position).flatten()));
			}
			return new Controls().withJump(dodgeNow).withPitch(-Math.cos(this.radians.getAsDouble()))
					.withYaw(-Math.sin(this.radians.getAsDouble()) * 2);
		}

		// Aim.
		Vector3 target = this.intercept.intersectPosition;
		if(this.wall){
			pencil.stackRenderString("Wall", Color.GREEN);
			if(this.intercept.plane.differentNormal(carPlane)){
				target = ((SeamIntercept)this.intercept).seamPosition.setDistanceFrom(car.position, 800);
				pencil.stackRenderString("Seam", Color.GREEN);
			}else{
				Vector3 fallingDisplacement = Vector3.Z
						.scale(0.5 * packet.gravity * Math.pow(jumpTime + Constants.DT, 2));
				target = target.minus(fallingDisplacement);
				pencil.renderer.drawRectangle3d(Color.GREEN, target, 8, 8, true);
				if(Double.isNaN(jumpTime)){
					System.err.println(this.bundle.bot.printPrefix() + "Couldn't jump " + (int)this.jumpZ
							+ "uu, but I can jump " + (int)maxZ + "uu!");
				}else{
					System.out.println(this.bundle.bot.printPrefix() + (int)fallingDisplacement.magnitude() + "uu");
				}
			}
		}else if(this.curve){
			double distance = car.position.distance(target);
			distance *= MathsUtils.clamp((driveTime - 0.3) * 0.4, 0, 0.5);
			if(distance > 100){
				target = target.plus(this.intercept.getOffset().scaleToMagnitude(distance)).clamp();
				pencil.stackRenderString((int)distance + "uu", Color.MAGENTA);
				pencil.renderer.drawLine3d(Color.MAGENTA, car.position, target.withZ(car.position.z));
			}
		}

		// Drive.
		this.drive.target = target;
		this.drive.withTargetVelocity(targetVelocity);
		Controls controls = (Controls)this.drive.getOutput();
		if(!this.go){
			if(Math.abs(controls.getSteer()) < 0.2)
				controls.withThrottle(10 - car.forwardVelocity);
			controls.withBoost(false);
		}
		if(Math.abs(controls.getSteer()) > 0.3)
			controls.withThrottle(Math.copySign(1, controls.getThrottle()));
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
