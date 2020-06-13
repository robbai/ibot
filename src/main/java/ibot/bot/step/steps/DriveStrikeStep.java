package ibot.bot.step.steps;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.input.Pencil;
import ibot.bot.intercept.Intercept;
import ibot.bot.intercept.SeamIntercept;
import ibot.bot.physics.Car1D;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.physics.JumpPhysics;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.step.steps.jump.DoubleJumpStep;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.maths.Plane;
import ibot.bot.utils.rl.Constants;
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
	private static final boolean CANCEL_FLIP = true;

	private OptionalDouble jumpStart = OptionalDouble.empty(), radians = OptionalDouble.empty();
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
			this.holdTime = Constants.MAX_JUMP_HOLD_TIME;
		}else{
			double minZ = JumpPhysics.maxZ(packet.gravity * this.gravityScale, 0, doubleJump);
			double maxZ = JumpPhysics.maxZ(packet.gravity * this.gravityScale, Constants.MAX_JUMP_HOLD_TIME,
					doubleJump);
			this.holdTime = MathsUtils.clamp(
					MathsUtils.lerp(0, Constants.MAX_JUMP_HOLD_TIME, Math.pow((this.jumpZ - minZ) / (maxZ - minZ), 2)),
					MIN_HOLD_TIME, Constants.MAX_JUMP_HOLD_TIME) * 0.61;
		}

		this.drive = new DriveStep(bundle);
		this.drive.reverse = false;
		this.drive.dodge = false;
		this.drive.routing = false;
	}

	@SuppressWarnings ("unused")
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
		boolean samePlane = true;
		double fullDistance;
		if(this.wall && carPlane.differentNormal(this.intercept.plane)){
			Vector3 seamPosition = ((SeamIntercept)this.intercept).seamPosition;
			fullDistance = (MathsUtils.local(car, seamPosition).flatten().magnitude()
					+ seamPosition.distance(this.intercept.position) - this.jumpZ);
			samePlane = false;
		}else{
			fullDistance = localIntercept.flatten().magnitude();
		}
		double maxZ = JumpPhysics.maxZ(packet.gravity * this.gravityScale, this.holdTime, this.doubleJump);
		double jumpTime = JumpPhysics.timeZ(Math.min(this.jumpZ, maxZ - 1), packet.gravity * this.gravityScale,
				this.holdTime, this.doubleJump);
		if(Double.isNaN(jumpTime))
			jumpTime = 0;
		double driveTime = Math.max(0, timeLeft - jumpTime);
		double initialVelocity = car.velocity.dot(this.intercept.intersectPosition.minus(car.position).normalised());
		double finalVelocity = (2 * fullDistance - driveTime * initialVelocity) / (driveTime + 2 * jumpTime);
		double driveDistance = (fullDistance - finalVelocity * jumpTime);
		double targetAcceleration = ((finalVelocity - initialVelocity) / driveTime);
		double targetVelocity = fullDistance / Math.max(timeLeft, MathsUtils.EPSILON);

		pencil.stackRenderString("Left: " + MathsUtils.round(timeLeft, 3) + "s",
				timeLeft < 0 ? Color.RED : Color.WHITE);
		pencil.stackRenderString("Hold: " + MathsUtils.round(this.holdTime, 3) + "s", Color.GREEN);

		this.setFinished(timeLeft <= (car.hasDoubleJumped && !this.doubleJump && !CANCEL_FLIP ? -0.6 : 0));

		if(!this.jumpStart.isPresent() && !this.intercept.plane.differentNormal(carPlane)){
			pencil.stackRenderString("Jump: " + MathsUtils.round(jumpTime, 3) + "s", Color.WHITE);
			pencil.stackRenderString((int)targetVelocity + "uu/s", Color.WHITE);

			Vector3 localFreefall = MathsUtils.local(car, freefall);
			Vector3 floor = this.intercept.intersectPosition.shadowOntoPlane(this.intercept.plane);
			pencil.renderer.drawLine3d(pencil.colour, car.position, freefall);
			pencil.renderer.drawLine3d(pencil.colour, this.intercept.intersectPosition, floor);
			pencil.renderer.drawLine3d(pencil.altColour, freefall, floor);

			// Simulate.
			Vector3 prophecy = null;
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
				if(renderTime > jumpTime && renderTime - renderDeltaTime < jumpTime){
					prophecy = position;
					pencil.renderCrosshair(Color.GREEN, prophecy, car.position);
				}
				pencil.renderer.drawLine3d(Color.WHITE, previousPosition, position);
				previousPosition = position;
				renderTime += renderDeltaTime;
			}

			boolean nowJump;
//			if(this.doubleJump){
//				nowJump = (driveTime <= (this.doubleJump ? 0.05 : Constants.DT));
//			}else{
			nowJump = (prophecy != null && MathsUtils.local(car, prophecy).flatten()
					.distance(localIntercept.flatten()) < (this.doubleJump ? 100 : 55));
//			}
			if(nowJump){
				this.jumpStart = OptionalDouble.of(time);
			}
		}

		// Crosshair.
		pencil.renderCrosshair(pencil.altColour, 50, this.intercept.intersectPosition, car.position);

		if(this.jumpStart.isPresent()){
			pencil.renderCrosshair(Color.MAGENTA, freefall, car.position);

			double timeJumping = (time - this.jumpStart.getAsDouble());
			boolean dodgeSoon = (timeLeft < DODGE_TIME + Constants.DT);
			boolean dodgeNow = (timeLeft < DODGE_TIME);

			pencil.stackRenderString(MathsUtils.round(timeLeft, 3) + "s", dodgeSoon ? Color.YELLOW : Color.WHITE);

			// Double-jump.
			if(this.doubleJump && timeJumping > this.holdTime + DoubleJumpStep.DOUBLE_JUMP_DELAY
					&& !car.hasDoubleJumped){
				return new Controls().withJump(true);
			}

			// Orient.
			if(!dodgeSoon){
				Vector3 desiredForward, desiredRoof;
				if(this.doubleJump){
					desiredForward = this.intercept.intersectPosition.minus(freefall);
					desiredRoof = Vector3.Z;
				}else{
//					double angle = MathsUtils.localAngleBetween(car, this.intercept.intersectPosition,
//							this.intercept.position); // Approach angle.
//					Vector3 corner = Vector3.X.scale(
//							Math.copySign(42.1, MathsUtils.local(car.orientation, this.intercept.getOffset()).x));
//					corner = MathsUtils.global(car.orientation, corner);
//					pencil.renderer.drawLine3d(Color.MAGENTA, this.intercept.intersectPosition,
//							corner.plus(this.intercept.intersectPosition));
//					pencil.renderer.drawRectangle3d(Color.PINK, corner.plus(this.intercept.intersectPosition), 12, 12,
//							true);
					desiredForward = MathsUtils.local(car.orientation, this.intercept.getOffset().scaleToMagnitude(-1));
					double angle = MathsUtils.localAngleBetween(car, freefall,
							this.intercept.position.minus(Vector3.Z.scale(50))) * Math.signum(desiredForward.x);
//					pencil.renderer.drawString3d((int)Math.toDegrees(angle) + "deg", Color.WHITE, car.position, 4, 4);
					desiredForward = desiredForward.flatten().rotate(MathsUtils.clampMagnitude(angle, Math.PI / 2))
							.withZ(0);
					double elevation = MathsUtils.clampMagnitude(
							Math.acos(
									(this.intercept.intersectPosition.z - car.position.z) / localIntercept.magnitude()),
							Math.PI / 4);
					desiredForward = MathsUtils.global(car.orientation, desiredForward).flatten().withAngleZ(elevation);
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
				this.radians = OptionalDouble.of(Vector2.Y.correctionAngle(
						MathsUtils.local(car.orientation, this.intercept.getOffset().scale(-1)).flatten()));
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
		double availableAcceleration = (new Car1D(car).stepTime(1, true, car.time + driveTime).getVelocity()
				- initialVelocity) / driveTime;

//		double driveTimeInPractice = driveTime;
//		double targetInitialVelocity = 0;
//		for(int i = 0; i < 10; i++){
//			targetInitialVelocity = (driveDistance / driveTimeInPractice)
//					- (availableAcceleration * driveTimeInPractice) / 2;
//			driveTimeInPractice = Math.max(MathsUtils.EPSILON,
//					driveTimeInPractice - new Car1D(0, 0, initialVelocity, car.boost)
//							.stepVelocity(1, true, targetInitialVelocity).getTime());
//		}
//		this.drive.withTargetVelocity(
//				Math.abs(this.bundle.info.lastControls.getSteer()) > 0.05 ? targetVelocity : targetInitialVelocity);
//		Controls controls = (Controls)this.drive.getOutput();

		if(!samePlane){
			this.drive.withTargetVelocity(targetVelocity * 1.3);
		}else{
			this.drive.withTargetVelocity(OptionalDouble.empty());
		}

		Controls controls = (Controls)this.drive.getOutput();
		if(Math.abs(controls.getSteer()) < 0.05 && samePlane){
			double throttle = DrivePhysics.produceAcceleration(car, targetAcceleration);
			controls.withThrottle(throttle).withBoost(throttle > 1);
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
