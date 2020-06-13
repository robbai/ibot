package ibot.bot.intercept;

import ibot.bot.step.steps.AerialStep;
import ibot.bot.utils.Pair;
import ibot.bot.utils.rl.Constants;
import ibot.input.Car;
import ibot.input.CarOrientation;
import ibot.vectors.Vector3;

public class AerialCalculator {

	// https://samuelpmish.github.io/notes/RocketLeague/aerial_hit/

	public final double finalSpeed, acceleration;
	public final boolean viable;

	public AerialCalculator(double finalVelocity, double acceleration, boolean viable){
		super();
		this.finalSpeed = finalVelocity;
		this.acceleration = acceleration;
		this.viable = viable;
	}

	public static AerialCalculator isViable(Car car, Vector3 target, double globalTime, Vector3 gravity,
			AerialType type){
		double timeLeft = (globalTime - car.time);

		Pair<Vector3, Vector3> freefall = AerialStep.calculateFreefall(car, timeLeft, gravity, type);
		Vector3 carPosition = freefall.getOne(), carVelocity = freefall.getTwo();

		Vector3 deltaPosition = target.minus(carPosition);
		Vector3 direction = deltaPosition.normalised();

		double turnTime = estimateTurnTime(car.orientation, direction);
//		if(type == AerialType.DODGE_STRIKE)
//			turnTime *= 2;
		timeLeft -= turnTime;

		Vector3 acceleration = deltaPosition.scale(2 / Math.pow(timeLeft, 2));
		Vector3 finalVelocity = carVelocity.plus(acceleration.scale(timeLeft));
		double boostUsed = (acceleration.magnitude() / Constants.BOOST_AIR_ACCELERATION) * timeLeft
				* Constants.BOOST_USAGE;

		// Is it viable?
		final double easy = 1;
		boolean viable = (finalVelocity.magnitude() < easy * Constants.MAX_CAR_VELOCITY)
				&& (boostUsed < easy * car.boost) && acceleration.magnitude() < Constants.BOOST_AIR_ACCELERATION * easy;
		return new AerialCalculator(finalVelocity.magnitude(), acceleration.magnitude(), viable);
	}

	public static double estimateTurnTime(CarOrientation orientation, Vector3 desiredForward){
		double dot = orientation.forward.dot(desiredForward.normalised());
		return Math.pow(dot - 1, 2) * 0.29;
	}

	public static double calculateBoostTime(Vector3 deltaPosition, double dot, double timeLeft){
		double displace = deltaPosition.magnitude() * dot, boostAcc = Constants.BOOST_AIR_ACCELERATION;
		double boostTime = timeLeft
				- (Math.sqrt(boostAcc * (boostAcc * Math.pow(timeLeft, 2) - 2 * displace))) / boostAcc;
//		if(Double.isNaN(boostTime)){
//			double acceleration = displace * (2 / Math.pow(timeLeft, 2));
//			boostTime = (acceleration * timeLeft) / Constants.BOOST_AIR_ACCELERATION;
//		}
		if(Double.isNaN(boostTime))
			return timeLeft;
		return boostTime;
	}

	public static double calculateBoostTime(Vector3 deltaPosition, CarOrientation orientation, double timeLeft){
		double dot = deltaPosition.normalised().dot(orientation.forward);
		return calculateBoostTime(deltaPosition, dot, timeLeft);
	}

}
