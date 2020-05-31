package ibot.bot.step.steps;

import java.awt.Color;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.input.Pencil;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Pair;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class AerialStep extends Step {

	public static final double JUMP_TIME = 0.2, DOUBLE_JUMP_TIME = (JUMP_TIME + JumpStep.DOUBLE_JUMP_DELAY),
			ANGLE_THRESHOLD = 0.3;

	public final Intercept intercept;
	public final AerialType type;
	private Vector3 targetUp = Vector3.Z, offset;
	private boolean startGrounded;

	public AerialStep(Bundle bundle, Intercept intercept, AerialType type){
		super(bundle);
		this.intercept = intercept;
		this.type = type;

		this.offset = intercept.getOffset();
		if(offset.magnitude() > 0){
			this.targetUp = this.offset.normalised();
		}

		this.startGrounded = bundle.packet.car.hasWheelContact;
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Car car = packet.car;

		double timeLeft = (this.intercept.time - packet.time);
		double timeElapsed = (packet.time - this.getStartTime());

		boolean orient = true, jump = (timeElapsed <= JUMP_TIME && this.startGrounded);

		if(timeLeft <= 0){
			this.setFinished(this.type != AerialType.DODGE_STRIKE || timeLeft < -0.6 || !car.hasDoubleJumped);
			return new Controls();
		}else if(timeElapsed >= DOUBLE_JUMP_TIME){
			if(car.hasWheelContact){
				this.setFinished(true);
			}else if(this.type == AerialType.DOUBLE_JUMP && !car.hasDoubleJumped && this.startGrounded){
				// Double-jump.
				orient = false;
				jump = true;
			}
		}

		// Dodge.
		if(this.type == AerialType.DODGE_STRIKE && timeLeft < DriveStrikeStep.DODGE_TIME * 1.5){
			Vector3 local = MathsUtils.local(car, DriveStrikeStep.getDodgeTarget(this.intercept));
			double radians = Vector2.Y.correctionAngle(local.flatten());
			return new Controls().withJump(true).withPitch(-Math.cos(radians)).withYaw(-Math.sin(radians));
		}

		// Calculations.
		Pair<Vector3, Vector3> freefall = calculateFreefall(car, timeLeft, bundle.info.arena.getGravity(), this.type,
				this.startGrounded, timeElapsed);
		Vector3 carPosition = freefall.getOne(), carVelocity = freefall.getTwo();
		drawCrosshair(pencil, Color.GREEN, carPosition, car.position);
		Vector3 deltaPosition = this.intercept.intersectPosition.minus(carPosition);
		Vector3 direction = deltaPosition.normalised();

		Vector3 turnTarget;
		boolean boost;
		double throttle;

		Vector3 deltaVelocity = deltaPosition.scale(1 / timeLeft);
		double angle = Math.acos(car.orientation.forward.dot(direction));
		double minVelocity = (Constants.BOOST_AIR_ACCELERATION * 3 * Constants.DT);
		if(car.orientation.forward.dot(deltaVelocity) < minVelocity){
			turnTarget = this.intercept.intersectPosition.minus(car.position);
			boost = false;
			throttle = 0;
		}else{
			turnTarget = direction;
			if(!this.bundle.info.lastControls.holdBoost())
				minVelocity = (Constants.BOOST_AIR_ACCELERATION * 0.1);
			boost = (car.orientation.forward.dot(deltaVelocity) > minVelocity && angle < ANGLE_THRESHOLD);
			throttle = 0;
		}
		pencil.stackRenderString((int)deltaVelocity.magnitude() + "uu/s", Color.WHITE);

		double B = ((2 * deltaPosition.magnitude()) / (Constants.BOOST_AIR_ACCELERATION * timeLeft));
		pencil.stackRenderString("Boost time: " + MathsUtils.round(B, 3) + "s", Color.WHITE);
		B = B * car.orientation.forward.dot(deltaPosition.normalised());
		pencil.stackRenderString("Boost time direction: " + MathsUtils.round(B, 3) + "s", Color.WHITE);

		// Crosshair.
		drawCrosshair(pencil, pencil.colour, this.intercept.intersectPosition, car.position);

		// Rendering.
		pencil.renderer.drawLine3d(Color.RED, car.position, car.position.plus(direction.scale(500)));
		pencil.renderer.drawLine3d(Color.GREEN, car.position, car.position.plus(car.orientation.forward.scale(500)));

		// Controls.
		Controls controls = new Controls().withJump(jump).withBoost(boost).withThrottle(throttle);
		if(orient){
			// Vector3 targetForward = (correction.magnitude() < 35 ?
			// this.intercept.ballPosition.minus(car.position) : correction);
			// Vector3 targetForward = correction;
			controls.withOrient(AirControl.getRollPitchYaw(car, turnTarget, this.targetUp));
			// controls.withOrient(AirControl.getRollPitchYaw(car, turnTarget));
		}
		return controls;
	}

	public static Pair<Vector3, Vector3> calculateFreefall(Car car, double timeLeft, Vector3 gravity, AerialType type,
			boolean startGrounded, double timeElapsed){
		Vector3 carPosition = car.position
				.plus(car.velocity.scale(timeLeft).plus(gravity.scale(0.5 * Math.pow(timeLeft, 2))));
		Vector3 carVelocity = car.velocity.plus(gravity.scale(timeLeft));
		if(startGrounded){
			if(timeElapsed <= JUMP_TIME){
				double tau = (JUMP_TIME - timeElapsed);
				if(timeElapsed == 0){
					carVelocity = carVelocity.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE));
					carPosition = carPosition.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE * timeLeft));
				}
				carVelocity = carVelocity.plus(car.orientation.up.scale(Constants.JUMP_ACCELERATION * tau));
				carPosition = carPosition
						.plus(car.orientation.up.scale(0.5 * Constants.JUMP_ACCELERATION * Math.pow(tau, 2)
								+ Constants.JUMP_ACCELERATION * tau * (timeLeft - JUMP_TIME)));
			}
			if(timeElapsed <= DOUBLE_JUMP_TIME && type == AerialType.DOUBLE_JUMP){
				carVelocity = carVelocity.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE));
				carPosition = carPosition
						.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE * (timeLeft - DOUBLE_JUMP_TIME)));
			}
		}
		return new Pair<Vector3, Vector3>(carPosition, carVelocity);
	}

	public static Pair<Vector3, Vector3> calculateFreefall(Car car, double timeLeft, Vector3 gravity, AerialType type){
		return calculateFreefall(car, timeLeft, gravity, type, car.hasWheelContact, 0);
	}

	private static void drawCrosshair(Pencil pencil, Color colour, Vector3 centre, Vector3 facing){
		final double size = 75;
		Vector3 line1 = centre.minus(facing).withZ(0);
		Vector3 line2 = line1.flatten().rotate(Math.PI / 2).withZ(0).scaleToMagnitude(size);
		pencil.renderer.drawLine3d(colour, centre.plus(line2), centre.minus(line2));
		line1 = line1.cross(line2).scaleToMagnitude(size);
		pencil.renderer.drawLine3d(colour, centre.plus(line1), centre.minus(line1));
	}

	@Override
	public int getPriority(){
		return Priority.AIRSTRIKE;
	}

}
