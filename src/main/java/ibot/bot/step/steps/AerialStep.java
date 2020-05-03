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
import ibot.bot.utils.Spherical;
import ibot.input.Car;
import ibot.input.CarOrientation;
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
	private Vector3 targetUp = Vector3.Z, offset, gravity;
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

		this.gravity = Vector3.Z.scale(bundle.packet.gravity);
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
		if(this.type == AerialType.DODGE_STRIKE && timeLeft < DriveStrikeStep.DODGE_TIME){
			Vector3 local = MathsUtils.local(car, DriveStrikeStep.getDodgeTarget(this.intercept));
			double radians = Vector2.Y.correctionAngle(local.flatten());
			return new Controls().withJump(true).withPitch(-Math.cos(radians)).withYaw(-Math.sin(radians));
		}

		// Render freefall.
		renderRoot(car, timeLeft, timeElapsed);

		pencil.stackRenderString(MathsUtils.round(timeElapsed, 3) + "s", Color.WHITE);
		Vector3 carPosition = car.position
				.plus(car.velocity.scale(timeLeft).plus(gravity.scale(0.5 * Math.pow(timeLeft, 2))));
		Vector3 carVelocity = car.velocity.plus(gravity.scale(timeLeft));
		if(this.startGrounded){
			if(timeElapsed <= JUMP_TIME){
				double tau = (JUMP_TIME - timeElapsed);
				if(timeElapsed == 0){
					carVelocity = carVelocity.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE));
					carPosition = carPosition.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE * timeLeft));
				}
				carVelocity = carVelocity.plus(car.orientation.up.scale(Constants.JUMP_ACCELERATION * tau));
				carPosition = carPosition
						.plus(car.orientation.up.scale(Constants.JUMP_ACCELERATION * tau * (timeLeft - 0.5 * tau)));
			}
			if(timeElapsed <= DOUBLE_JUMP_TIME && this.type == AerialType.DOUBLE_JUMP){
				carVelocity = carVelocity.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE));
				carPosition = carPosition
						.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE * (timeLeft - DOUBLE_JUMP_TIME)));
			}
		}

		Vector3 deltaPosition = this.intercept.intersectPosition.minus(carPosition);

		Vector3 direction = deltaPosition.normalised();

		Vector3 turnTarget;

		boolean boost;
		double throttle;

		if(deltaPosition.magnitude() > 50){
			turnTarget = direction;
		}else{
			turnTarget = this.intercept.position.minus(car.position);
		}
		Spherical spherical = new Spherical(MathsUtils.local(car.orientation, direction));
		double angle = Math.abs(spherical.getElevation()) + Math.abs(spherical.getPerpendicular());
		if(angle < ANGLE_THRESHOLD){
			if(deltaPosition.magnitude() > 50){
				boost = true;
				throttle = 0;
			}else{
				boost = false;
				throttle = MathsUtils.clamp(0.5 * Constants.THROTTLE_AIR_ACCELERATION * Math.pow(timeLeft, 2), 0, 1);
			}
		}else{
			boost = false;
			throttle = 0;
		}
		pencil.stackRenderString((int)deltaPosition.magnitude() + "uu", Color.WHITE);

		// Vector3 deltaVelocity = deltaPosition.scale(1 / timeLeft);
		// Spherical spherical = new Spherical(MathsUtils.local(car.orientation,
		// direction));
		// double angle = Math.abs(spherical.getElevation()) +
		// Math.abs(spherical.getPerpendicular());
		// double minVelocity = (Constants.BOOST_AIR_ACCELERATION * 3 * Constants.DT);
		// if(car.orientation.forward.dot(deltaVelocity) < minVelocity){
		// turnTarget = this.intercept.interceptPosition.minus(car.position);
		// boost = false;
		// throttle = 0;
		// }else{
		// turnTarget = direction;
		// if(!bot.lastControls.holdBoost()) minVelocity =
		// (Constants.BOOST_AIR_ACCELERATION * 0.1);
		// boost = (car.orientation.forward.dot(deltaVelocity) > minVelocity && angle <
		// ANGLE_THRESHOLD);
		// throttle = 0;
		// }
		// bot.stackRenderString((int)deltaVelocity.magnitude() + "uu/s", Color.WHITE);

		// if(true){
		// double B = ((2 * deltaPosition.magnitude()) /
		// (Constants.BOOST_AIR_ACCELERATION * timeLeft));
		// bot.stackRenderString("Boost time: " + MathsUtils.round(B, 3) + "s",
		// Color.WHITE);
		// B = B * car.orientation.forward.dot(deltaPosition.normalised());
		// bot.stackRenderString("Boost time direction: " + MathsUtils.round(B, 3) +
		// "s", Color.WHITE);
		// }

		// Crosshair.
		final double size = 75;
		Vector3 line1 = this.intercept.position.minus(car.position).withZ(0);
		Vector3 line2 = line1.flatten().rotate(Math.PI / 2).withZ(0).scaleToMagnitude(size);
		pencil.renderer.drawLine3d(pencil.colour, this.intercept.position.plus(line2),
				this.intercept.position.minus(line2));
		line1 = line1.cross(line2).scaleToMagnitude(size);
		pencil.renderer.drawLine3d(pencil.colour, this.intercept.position.plus(line1),
				this.intercept.position.minus(line1));

		// Rendering.
		// bot.stackRenderString((int)acceleration + "uu/s^2", Color.WHITE,
		// car.position, 1, 1);
		// bot.renderer.drawLine3d(Color.WHITE, this.intercept.interceptPosition,
		// carPosition);
		// bot.renderer.drawLine3d(Color.BLACK, result, result.plus(prophecy));
		// bot.renderer.drawLine3d(Color.RED, this.intercept.interceptPosition,
		// this.intercept.interceptPosition.plus(car.orientation.forward.minus(correction.normalised()).scale(500)));
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

	private void render(Vector3 carPosition, Vector3 carVelocity, CarOrientation carOrientation,
			boolean hasDoubleJumped, double timeLeft, double timeElapsed){
		Vector3 lastCarPosition = new Vector3(carPosition);

		// Jumping.
		if(this.startGrounded){
			if(timeElapsed == 0
					|| (timeElapsed >= DOUBLE_JUMP_TIME && !hasDoubleJumped && this.type == AerialType.DOUBLE_JUMP)){
				// JumpStep or double-jump.
				carVelocity = carVelocity.plus(carOrientation.up.scale(Constants.JUMP_IMPULSE));
				hasDoubleJumped |= (timeElapsed >= DOUBLE_JUMP_TIME);
			}
			if(timeElapsed <= JUMP_TIME){
				// Acceleration due to jumping.
				carVelocity = carVelocity.plus(carOrientation.up.scale(Constants.JUMP_ACCELERATION * Constants.DT));
			}
		}

		// Gravity.
		carVelocity = carVelocity.plus(gravity.scale(Constants.DT));

		// if(bot.lastControls.holdBoost() && timeElapsed < Constants.DT * 3){
		// carVelocity =
		// carVelocity.plus(carOrientation.forward.scale(Constants.BOOST_AIR_ACCELERATION
		// * Constants.DT));
		// }

		carVelocity = carVelocity.scale(Math.min(1, Constants.MAX_CAR_VELOCITY / carVelocity.magnitude()));

		carPosition = carPosition.plus(carVelocity.scale(Constants.DT));

		timeElapsed += Constants.DT;
		timeLeft -= Constants.DT;

		this.bundle.pencil.renderer.drawLine3d(this.bundle.pencil.altColour, lastCarPosition, carPosition);

		if(timeLeft <= 0)
			return;
		render(carPosition, carVelocity, carOrientation, hasDoubleJumped, timeLeft, timeElapsed);
	}

	private void renderRoot(Car car, double timeLeft, double timeElapsed){
		render(car.position, car.velocity, car.orientation, car.hasDoubleJumped, timeLeft, timeElapsed);
	}

	@Override
	public int getPriority(){
		return Priority.AIRSTRIKE;
	}

}
