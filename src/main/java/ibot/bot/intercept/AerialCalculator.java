package ibot.bot.intercept;

import ibot.bot.step.steps.AerialStep;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Pair;
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

		Pair<Vector3, Vector3> freefall = AerialStep.calculateFreefall(car, time, gravity, type);
		Vector3 carPosition = freefall.getOne(), carVelocity = freefall.getTwo();

		Vector3 deltaPosition = target.minus(carPosition);
		Vector3 direction = deltaPosition.normalised();

		double totalTurnTime = estimateTurnTime(car.orientation, direction);
		double phi = Math.acos(car.orientation.forward.dot(direction));

		double tau1 = totalTurnTime * MathsUtils.clamp(1 - AerialStep.ANGLE_THRESHOLD / phi, 0, 1);

		double requiredAcceleration = 2 * deltaPosition.magnitude() / Math.pow(time - tau1, 2);

		double ratio = (requiredAcceleration / Constants.BOOST_AIR_ACCELERATION);

		double tau2 = time - (time - tau1) * Math.sqrt(1 - MathsUtils.clamp(ratio, 0, 1));

		Vector3 velocityEstimate = carVelocity.plus(direction.scale(Constants.BOOST_AIR_ACCELERATION * (tau2 - tau1)));

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
//		return Math.pow((dot - 1) / 0.5, 2);
		double angle = Math.acos(dot);
		return angle * 0.85;
	}

}
