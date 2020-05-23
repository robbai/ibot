package ibot.bot.intercept;

import ibot.bot.step.steps.AerialStep;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Spherical;
import ibot.input.Car;
import ibot.input.CarOrientation;
import ibot.prediction.Slice;
import ibot.vectors.Vector3;

public class AerialCalculator {

	// https://samuelpmish.github.io/notes/RocketLeague/aerial_hit/

	public final double finalVelocity, acceleration;
	public final boolean viable;

	public AerialCalculator(double finalVelocity, double acceleration, boolean viable){
		super();
		this.finalVelocity = finalVelocity;
		this.acceleration = acceleration;
		this.viable = viable;
	}

	/*
	 * https://raw.githubusercontent.com/samuelpmish/RLUtilities/master/src/
	 * mechanics/aerial.cc
	 */
	public static AerialCalculator isViable(Car car, Vector3 target, double globalTime, Vector3 gravity,
			AerialType type){
		double time = (globalTime - car.time);

		Vector3 carPosition = car.position.plus(car.velocity.scale(time).plus(gravity.scale(0.5 * Math.pow(time, 2))));
		Vector3 carVelocity = car.velocity.plus(gravity.scale(time));

		if(car.hasWheelContact){
			carPosition = carPosition.plus(car.orientation.up.scale(Constants.JUMP_IMPULSE
					* ((type == AerialType.DOUBLE_JUMP ? 2 : 1) * time - AerialStep.JUMP_TIME)
					+ Constants.JUMP_ACCELERATION
							* (time * AerialStep.JUMP_TIME - 0.5 * AerialStep.JUMP_TIME * AerialStep.JUMP_TIME)));
			carVelocity = carVelocity
					.plus(car.orientation.up.scale((type == AerialType.DOUBLE_JUMP ? 2 : 1) * Constants.JUMP_IMPULSE
							+ Constants.JUMP_ACCELERATION * AerialStep.JUMP_TIME));
		}

		Vector3 deltaPosition = target.minus(carPosition);

		Vector3 forward = deltaPosition.normalised();

		double totalTurnTime = estimateTurnTime(car.orientation, forward);
		Spherical sphericalLocal = new Spherical(MathsUtils.local(car, forward));
		double phi = Math.abs(sphericalLocal.getElevation()) + Math.abs(sphericalLocal.getPerpendicular());

		double tau1 = totalTurnTime * MathsUtils.clamp(1 - AerialStep.ANGLE_THRESHOLD / phi, 0, 1);

		double requiredAcceleration = 2 * deltaPosition.magnitude() / ((time - tau1) * (time - tau1));

		double ratio = (requiredAcceleration / Constants.BOOST_AIR_ACCELERATION);

		double tau2 = time - (time - tau1) * Math.sqrt(1 - MathsUtils.clamp(ratio, 0, 1));

		Vector3 velocityEstimate = carVelocity.plus(forward.scale(Constants.BOOST_AIR_ACCELERATION * (tau2 - tau1)));

		double boostEstimate = (tau2 - tau1) * Constants.BOOST_USAGE;

//		final double easy = MathsUtils.lerp(0.75, 0.9, car.boost / 100);
		final double easy = 0.9;

		double finalVelocity = velocityEstimate.magnitude();
		boolean viable = (finalVelocity < easy * Constants.MAX_CAR_VELOCITY)
				&& (boostEstimate < MathsUtils.lerp(easy, 1, 0.25) * car.boost) && (Math.abs(ratio) < easy);
		return new AerialCalculator(finalVelocity, requiredAcceleration, viable);
	}

	public static AerialCalculator isViable(Car car, Slice slice, Vector3 gravity, AerialType type){
		return isViable(car, slice.position, slice.time, gravity, type);
	}

//	public static double estimateTurnTime(CarOrientation orientation, Vector3 desiredForward){
//		Vector3 local = MathsUtils.local(orientation, desiredForward);
//		Spherical spherical = new Spherical(local);
//		double phi = Math.abs(spherical.getElevation()) + Math.abs(spherical.getPerpendicular());
//		double time = 1.8 * Math.sqrt(phi / 9);
//		return time;
//	}

	public static double estimateTurnTime(CarOrientation orientation, Vector3 desiredForward){
		double dot = orientation.forward.dot(desiredForward.normalised());
		return Math.pow((dot - 1) / 0.5, 2);
	}

}
