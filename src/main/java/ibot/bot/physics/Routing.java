package ibot.bot.physics;

import java.util.List;
import java.util.stream.Collectors;

import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.utils.StaticClass;
import ibot.bot.utils.maths.MathsUtils;
import ibot.input.Car;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class Routing extends StaticClass {

	public static final double[] QUICK_TURN_VELOCITIES = { 860, 1100, 2250 };

	public static double estimateTurnTime(Car car, Vector3 target, boolean allowBackwards){
		double radians = Vector2.Y.angle(MathsUtils.local(car, target).flatten());
		if(car.position.distance(target) < 2500 && allowBackwards){
			radians = MathsUtils.shorterAngle(radians);
		}
		return estimateTurnTimeRadians(radians);
	}

	public static double estimateTurnTime(Car car, Vector2 target, boolean allowBackwards){
		return estimateTurnTime(car, target.withZ(0), allowBackwards);
	}

	public static double estimateTurnTimeRadians(double radians){
		return Math.abs(radians) * 0.6;
	}

	/**
	 * 2D based routing.
	 */
	public static Vector2 quickRoute(Car car, Slice destination){
		Vector2 carPosition = car.position.flatten();
		Vector2 destinationPosition = destination.position.flatten();

		// Get the pads.
		List<BoostPad> viablePads = BoostManager.getAllBoosts().stream()
				.filter(pad -> isPadViable(car, destination, pad)).collect(Collectors.toList());

//		Vector2 carDirection = car.orientation.forward.flatten();

		// Find the quickest route.
		Vector2 bestBoostPosition = new Vector2(destinationPosition);
		double bestTime = destination.time;
		for(BoostPad pad : viablePads){
			// Drive to the boost.
			double distance = pad.getPosition().distance(carPosition);
			Car1D sim = new Car1D(car, pad.getPosition()).stepDisplacement(1, true, distance);
			double turnTime1 = estimateTurnTime(car, pad.getPosition(), false);
			if(sim.getTime() + turnTime1 > bestTime)
				continue;

			// Drive to the destination!
			sim.withBoost(pad.isFullBoost() ? 100 : Math.min(100, sim.getBoost() + 12)); // Pickup boost.
			distance += pad.getPosition().distance(destinationPosition);
			sim.stepDisplacement(1, true, distance);
			double radians = pad.getPosition().minus(carPosition)
					.angle(destination.position.flatten().minus(pad.getPosition()));
			double turnTime2 = estimateTurnTimeRadians(radians);

			// Best!
			double time = sim.getTime() + turnTime1 + turnTime2;
			if(time < bestTime){
				bestTime = time;
				bestBoostPosition = pad.getPosition();
			}
		}

		return bestBoostPosition;
	}

	private static boolean isPadViable(Car car, Slice destination, BoostPad pad){
		if(pad.getTimeLeft() + car.time > destination.time)
			return false;
		if(!pad.isActive())
			return false;
//		if(pad.getPosition().equals(destination.position.flatten()))
//			return false;
		if(pad.getPosition().distance(destination.position.flatten()) < 80)
			return false;
//		return destination.position.minus(car.position).flatten()
//				.dot(destination.position.flatten().minus(pad.getPosition())) > 0;
		return pad.getPosition().minus(destination.position.flatten())
				.dot(pad.getPosition().minus(car.position.flatten())) < 0;
	}

	public static double findQuickerTurningVelocity(double velocity, boolean allowAccelerating){
		int best = -1;
		double bestValue = 0;
		for(int i = 0; i < QUICK_TURN_VELOCITIES.length; i++){
			if(Math.abs(QUICK_TURN_VELOCITIES[i]) > Math.abs(velocity) && !allowAccelerating)
				continue;
			double value = Math.abs(QUICK_TURN_VELOCITIES[i] - velocity);
			if(best == -1 || value <= bestValue){
				best = i;
				bestValue = value;
			}
		}
		return best == -1 ? velocity : Math.copySign(QUICK_TURN_VELOCITIES[best], velocity);
	}

}
