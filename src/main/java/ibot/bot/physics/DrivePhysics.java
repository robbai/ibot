package ibot.bot.physics;

import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.StaticClass;
import ibot.input.Car;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class DrivePhysics extends StaticClass {

	/**
	 * Piecewise-linear
	 * https://samuelpmish.github.io/notes/RocketLeague/ground_control/#turning
	 */
	private static double[][] speedCurvature = new double[][] {{0.0, 0.00690}, {500.0, 0.00398}, {1000.0, 0.00235}, {1500.0, 0.00138}, {1750.0, 0.00110}, {2300.0, 0.00088}};
	/**
	 * Piecewise-linear
	 * https://samuelpmish.github.io/notes/RocketLeague/ground_control/#throttle
	 */
	private static double[][] throttleAcceleration = new double[][] {{0, 1600}, {1400, 160}, {1410, 0}, {2300, 0}};

	/*
	 *  https://github.com/samuelpmish/RLUtilities/blob/master/src/mechanics/drive.cc#L34-L53
	 */
	private static double curvature(double v){
		v = MathsUtils.clamp(Math.abs(v), 0, Constants.MAX_CAR_VELOCITY);

		for(int i = 0; i < 5; i++){
			if(speedCurvature[i][0] <= v && (v < speedCurvature[i + 1][0] || (i == 4 && v == speedCurvature[5][0]))){
				double u = (v - speedCurvature[i][0]) / (speedCurvature[i + 1][0] - speedCurvature[i][0]);
				return MathsUtils.lerp(speedCurvature[i][1], speedCurvature[i + 1][1], u);
			}
		}

		return 0;
	}

	/*
	 *  https://samuelpmish.github.io/notes/RocketLeague/ground_control/#acceleration
	 */
	public static double determineAcceleration(double velocityForward, double throttle, boolean boost){
		if(boost) throttle = 1;

		double boostAcceleration = (boost ? Constants.BOOST_GROUND_ACCELERATION : 0);

		// Coasting and braking.
		boolean coast = (Math.abs(throttle) < 0.01);
		boolean brake = (!coast && velocityForward * throttle < 0);
		if(coast){
			return -Math.signum(velocityForward) * Constants.COAST_ACCELERATION;
		}else if(brake){
			return -Math.signum(velocityForward) * (Constants.BRAKE_ACCELERATION + boostAcceleration);
		}

		// Throttle.
		velocityForward = MathsUtils.clamp(Math.abs(velocityForward), 0, 2300);
		for(int i = 0; i < 3; i++){
			if(throttleAcceleration[i][0] <= velocityForward && velocityForward < throttleAcceleration[i + 1][0]){
				double u = (velocityForward - throttleAcceleration[i][0]) / (throttleAcceleration[i + 1][0] - throttleAcceleration[i][0]);
				return MathsUtils.lerp(throttleAcceleration[i][1], throttleAcceleration[i + 1][1], u) + boostAcceleration;
			}
		}

		return 0;
	}

	/*
	 *  https://github.com/samuelpmish/RLUtilities/blob/master/src/mechanics/drive.cc#L55-L74
	 */
	public static double getSpeedFromRadius(double r){
		double k = (1D / r);
		k = MathsUtils.clamp(k, speedCurvature[speedCurvature.length - 1][1], speedCurvature[0][1]);

		for(int i = 5; i > 0; i--){
			if(speedCurvature[i][1] <= k && (k < speedCurvature[i - 1][1] || (i == 1 && k == speedCurvature[0][1]))){
				double u = (k - speedCurvature[i][1]) / (speedCurvature[i - 1][1] - speedCurvature[i][1]);
				return MathsUtils.lerp(speedCurvature[i][0], speedCurvature[i - 1][0], u);
			}
		}

		return 0;
	}

	public static double getTurnRadius(double v){
		if(v == 0) return 0;
		return 1.0 / curvature(v);
	}

	public static double maxVelForTurn(Car car, Vector3 target){
		Vector2 local = MathsUtils.local(car, target).flatten();

		double low = 100;
//		double high = Constants.SUPERSONIC_VELOCITY;
		double high = Constants.MAX_CAR_VELOCITY;

		final int steps = 20;
		for(int i = 0; i < steps; i++){
			double vel = (low + high) / 2;

			double turningRadius = getTurnRadius(vel);

			Vector2 left = Vector2.X.scale(turningRadius);
			Vector2 right = left.scale(-1);

			double distance = Math.min(local.distance(left), local.distance(right));
			if(distance < turningRadius){
				high = vel;
			}else{
				low = vel;
			}
		}

		return high;
	}

	public static double maxVelocity(double velocityForward, double boost, double maxTime){
		double velocity = velocityForward, time = 0;
		while(time < maxTime){
			double acceleration = determineAcceleration(velocity, 1, boost >= 1);
			if(Math.abs(acceleration) < 0.1) break;

			velocity += acceleration * Constants.DT;

			if(Math.abs(velocity) > Constants.MAX_CAR_VELOCITY) return Constants.MAX_CAR_VELOCITY * Math.signum(velocity);

			boost -= Constants.BOOST_USAGE * Constants.DT;

			time += Constants.DT;
		}

		return velocity;
	}

	public static double maxVelocity(double velocityForward, double boost){
		return maxVelocity(velocityForward, boost, 10);
	}

	public static double maxVelocityDist(double velocityForward, double boost, double maxDistance){
		double velocity = velocityForward, time = 0, distance = 0;
		while(time < 20 && distance < maxDistance){
			double acceleration = determineAcceleration(velocity, 1, boost >= 1);
			if(Math.abs(acceleration) < 0.1) break;

			velocity += acceleration * Constants.DT;

			if(Math.abs(velocity) > Constants.MAX_CAR_VELOCITY) return Constants.MAX_CAR_VELOCITY * Math.signum(velocity);

			distance += velocity * Constants.DT;

			boost -= Constants.BOOST_USAGE * Constants.DT;

			time += Constants.DT;
		}

		return velocity;
	}

	public static double maxDistance(double maxTime, double velocity, double boost){
		boolean reverse = boost < -0.0001;
		if(reverse) boost = 0;
		double sign = (reverse ? -1 : 1);

		double time = 0, displace = 0;

		while(time < maxTime){
			double acceleration = determineAcceleration(velocity, sign, boost >= 1);
			if(Math.abs(acceleration) < 1 && Math.abs(velocity) < 1) break;

			velocity += acceleration * Constants.DT;
			velocity = MathsUtils.clamp(velocity, -Constants.MAX_CAR_VELOCITY, Constants.MAX_CAR_VELOCITY);
			boost -= Constants.BOOST_USAGE * Constants.DT;

			displace += velocity * Constants.DT;

			time += Constants.DT;
		}

		return displace;
	}

	public static double maxDistanceReverse(double maxTime, double velocity){
		return maxDistance(maxTime, velocity, -1);
	}

	public static double timeToReachVel(double velocity, double boost, double targetVelocity){
		boolean brake = (targetVelocity < velocity);

		double time = 0;

		while(Math.abs(velocity - targetVelocity) > 50 && time < 10){
			velocity += determineAcceleration(velocity, brake ? -1 : 1, boost >= 1 && !brake) * Constants.DT;
			if(!brake) boost -= Constants.BOOST_USAGE * Constants.DT;
			time += Constants.DT;
		}

		return time;
	}

	public static double minTravelTime(double forwardVelocity, double startBoost, double targetDistance, double maxVel){
		boolean reverse = startBoost < -0.0001;
		if(reverse) startBoost = 0;
		double sign = (reverse ? -1 : 1);

		double time = 0, distance = 0, boost = startBoost, velocity = forwardVelocity;

		while(distance < targetDistance){
			velocity += determineAcceleration(velocity, sign, boost >= 1) * Constants.DT;
			velocity = MathsUtils.clamp(velocity, -maxVel, maxVel);
			if(velocity != maxVel) boost -= Constants.BOOST_USAGE * Constants.DT;
			distance += velocity * sign * Constants.DT;
			time += Constants.DT;
		}

		return time;
	}

	public static double minTravelTimeReverse(double forwardVelocity, double targetDistance, double maxVel){
		return minTravelTime(forwardVelocity, -1, targetDistance, maxVel);
	}

	public static double minTravelTime(double forwardVelocity, double startBoost, double targetDistance){
		return minTravelTime(forwardVelocity, startBoost, targetDistance, Constants.MAX_CAR_VELOCITY);
	}

	public static double minTravelTime(Car car, double targetDistance){
		return minTravelTime(car.forwardVelocity, car.boost, targetDistance);
	}

	public static double estimateDodgeDistance(Car car){
		return (car.forwardVelocityAbs + Constants.DODGE_IMPULSE) * 1.45;
	}

	public static double produceAcceleration(double acceleration, double forwardVelocity){
		// TODO Auto-generated method stub
		return 0;
	}

}
