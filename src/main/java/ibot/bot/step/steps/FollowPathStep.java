package ibot.bot.step.steps;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.bot.input.Bundle;
import ibot.bot.input.Pencil;
import ibot.bot.path.Path;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class FollowPathStep extends Step {

	public final static double STEER_LOOKAHEAD = 0.285, SPEED_LOOKAHEAD = 0.05;
	private final static boolean VERBOSE_RENDER = true;

	private Path path;
	private OptionalDouble targetTime;
	public boolean renderPredictionToTargetTime = false;
	public boolean dodge = false, linearTarget = false;
	private final DriveStep drive;

	public FollowPathStep(Bundle bundle, Path path, boolean dodge, OptionalDouble targetTime){
		super(bundle);
		this.path = path;
		this.dodge = dodge;
		this.targetTime = (!targetTime.isPresent()/** || targetTime.getAsDouble() < path.getTime() */
				? OptionalDouble.empty()
				: targetTime);
		this.drive = new DriveStep(bundle);
		this.drive.reverse = false;
		this.drive.dodge = false;
//		this.drive.ignoreRadius = true;
	}

	public FollowPathStep(Bundle bundle, Path path, OptionalDouble targetTime){
		this(bundle, path, false, targetTime);
	}

	public FollowPathStep(Bundle bundle, Path path){
		this(bundle, path, false, OptionalDouble.empty());
	}

	public FollowPathStep(Bundle bundle, Path path, boolean dodge){
		this(bundle, path, dodge, OptionalDouble.empty());
	}

	public FollowPathStep(Bundle bundle, Path path, double targetTime){
		this(bundle, path, false, OptionalDouble.of(targetTime));
	}

	public FollowPathStep(Bundle bundle, Path path, boolean dodge, double targetTime){
		this(bundle, path, dodge, OptionalDouble.of(targetTime));
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;

		if(!this.targetTime.isPresent() && this.expire())
			return new PopStack();

		// Target and acceleration.
		double carS = this.path.findClosestS(packet.car.position.flatten(), false);
		double initialVelocity = packet.car.forwardVelocityAbs;
		double timeElapsed = packet.time - this.getStartTime();
		double guessedTimeLeft = (this.path.getTime() - timeElapsed);
		double updatedTimeLeft = (this.path.getTime() * (1 - carS / this.path.getDistance()));
		Vector2 target = getTarget(carS, initialVelocity);
		double targetVelocity = this.path
				.getSpeed(MathsUtils.clamp((carS + initialVelocity * SPEED_LOOKAHEAD) / this.path.getDistance(), 0, 1));

//		targetVelocity = Math.max(400, targetVelocity);

		if(updatedTimeLeft > guessedTimeLeft + 0.4)
			return new PopStack();

		double targetAcceleration = (targetVelocity - initialVelocity) / SPEED_LOOKAHEAD;
		if(this.targetTime.isPresent()){
			double targetTimeLeft = (this.targetTime.getAsDouble() - packet.time);

			if(this.linearTarget){
				targetAcceleration = ((this.path.getDistance() - carS) / targetTimeLeft - initialVelocity)
						/ SPEED_LOOKAHEAD; // Enforce!
			}else{
				double arrivalAcceleration = ((2 * (this.path.getDistance() - carS - targetTimeLeft * initialVelocity))
						/ Math.pow(targetTimeLeft, 2));
				targetAcceleration = Math.min(targetAcceleration, arrivalAcceleration);
			}
		}

		// Render.
		pencil.stackRenderString("Distance: " + (int)this.path.getDistance() + "uu", Color.WHITE);
		if(!this.targetTime.isPresent()){
			pencil.stackRenderString("Est Time: " + MathsUtils.round(updatedTimeLeft) + "s ("
					+ (guessedTimeLeft < updatedTimeLeft ? "+" : "")
					+ MathsUtils.round(updatedTimeLeft - guessedTimeLeft) + "s)", Color.WHITE);
		}else{
			pencil.stackRenderString("Est Time: " + MathsUtils.round(updatedTimeLeft) + "s (Want: "
					+ MathsUtils.round(this.targetTime.getAsDouble() - packet.time) + "s)", Color.WHITE);
		}
		if(VERBOSE_RENDER){
			pencil.stackRenderString("Current Vel.: " + (int)initialVelocity + "uu/s", Color.WHITE);
			pencil.stackRenderString("Target Vel.: " + (int)targetVelocity + "uu/s", Color.WHITE);
			pencil.stackRenderString("Target Acc.: " + (int)targetAcceleration + "uu/s^2", Color.WHITE);
		}
		this.path.render(pencil, Color.BLUE);
		pencil.renderer.drawCenteredRectangle3d(Color.CYAN, target.withZ(Constants.CAR_HEIGHT), 10, 10, true);
		pencil.renderer.drawCenteredRectangle3d(Color.RED,
				this.path.S(Math.min(this.path.getDistance(), carS + initialVelocity * SPEED_LOOKAHEAD))
						.withZ(Constants.CAR_HEIGHT),
				5, 5, true);

		// Dodge.
		if(this.dodge && targetAcceleration >= 0){
			// Low time results in a chip shot, high time results in a low shot
			if(updatedTimeLeft < 0.2){
				return new FastDodgeStep(this.bundle, this.path.T(1).minus(packet.car.position.flatten()).withZ(0));
			}
		}

		// Handling.
		this.drive.target = target.withZ(0);
		this.drive.withTargetVelocity(packet.car.forwardVelocity + targetAcceleration * SPEED_LOOKAHEAD);
		return ((Controls)this.drive.getOutput()).withHandbrake(false);
	}

	private Vector2 getTarget(double carS, double initialVelocity){
		return this.path
				.S(Math.min(this.path.getDistance() - 1, carS + Math.max(500, initialVelocity) * STEER_LOOKAHEAD));
	}

	private boolean expire(){
		Car car = this.bundle.packet.car;

//		if(!car.onFlatGround)
//			return true;

		double distanceError = this.path.findClosestS(car.position.flatten(), true);
		if(distanceError > 80)
			return true;

		double carS = this.path.findClosestS(car.position.flatten(), false);

		if(this.dodge){
			return (carS + Math.abs(car.forwardVelocity) * STEER_LOOKAHEAD / 8) / this.path.getDistance() >= 1;
		}

//		return getTarget(carS, car.forwardVelocityAbs) == null;

		return carS > this.path.getDistance() - 100;
	}

	@Override
	public int getPriority(){
		return this.dodge ? Priority.STRIKE : Priority.DRIVE;
	}

}
