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
	private static final double[][] SPEED_CURVATURE = new double[][] { { 0.0, 0.00690 }, { 500.0, 0.00398 },
			{ 1000.0, 0.00235 }, { 1500.0, 0.00138 }, { 1750.0, 0.00110 }, { Constants.MAX_CAR_VELOCITY, 0.00088 } };
	/**
	 * Piecewise-linear
	 * https://samuelpmish.github.io/notes/RocketLeague/ground_control/#throttle
	 */
	private static final double[][] THROTTLE_ACCELERATION = new double[][] { { 0, 1600 }, { 1400, 160 }, { 1410, 0 },
			{ Constants.MAX_CAR_VELOCITY, 0 } };

	/*
	 * https://github.com/samuelpmish/RLUtilities/blob/master/src/mechanics/drive.cc
	 * #L34-L53
	 */
	private static double curvature(double velocity){
		velocity = MathsUtils.clamp(Math.abs(velocity), 0, Constants.MAX_CAR_VELOCITY);

		for(int i = 0; i < SPEED_CURVATURE.length - 1; i++){
			if(SPEED_CURVATURE[i][0] <= velocity
					&& (velocity < SPEED_CURVATURE[i + 1][0] || (i == 4 && velocity == SPEED_CURVATURE[5][0]))){
				double u = (velocity - SPEED_CURVATURE[i][0]) / (SPEED_CURVATURE[i + 1][0] - SPEED_CURVATURE[i][0]);
				return MathsUtils.lerp(SPEED_CURVATURE[i][1], SPEED_CURVATURE[i + 1][1], u);
			}
		}

		return 0;
	}

	/*
	 * https://samuelpmish.github.io/notes/RocketLeague/ground_control/#acceleration
	 */
	public static double determineAcceleration(double velocity, double throttle, boolean boost){
		if(boost)
			throttle = 1;

		double boostAcceleration = (boost ? Constants.BOOST_GROUND_ACCELERATION : 0);

		// Coasting and braking.
		boolean coast = (Math.abs(throttle) < 0.01);
		boolean brake = (!coast && velocity * throttle < 0);
		if(coast){
			return -Math.signum(velocity) * Constants.COAST_ACCELERATION;
		}else if(brake){
			return -Math.signum(velocity) * (Constants.BRAKE_ACCELERATION + boostAcceleration);
		}

		// Throttle.
		velocity = MathsUtils.clamp(Math.abs(velocity), 0, Constants.MAX_CAR_VELOCITY);
		for(int i = 0; i < 3; i++){
			if(THROTTLE_ACCELERATION[i][0] <= velocity && velocity < THROTTLE_ACCELERATION[i + 1][0]){
				double u = (velocity - THROTTLE_ACCELERATION[i][0])
						/ (THROTTLE_ACCELERATION[i + 1][0] - THROTTLE_ACCELERATION[i][0]);
				return MathsUtils.lerp(THROTTLE_ACCELERATION[i][1], THROTTLE_ACCELERATION[i + 1][1], u)
						+ boostAcceleration;
			}
		}

		return 0;
	}

	/*
	 * https://github.com/samuelpmish/RLUtilities/blob/master/src/mechanics/drive.cc
	 * #L55-L74
	 */
	public static double getSpeedFromRadius(double r){
		double k = (1D / r);
		k = MathsUtils.clamp(k, SPEED_CURVATURE[SPEED_CURVATURE.length - 1][1], SPEED_CURVATURE[0][1]);

		for(int i = SPEED_CURVATURE.length - 1; i > 0; i--){
			if(SPEED_CURVATURE[i][1] <= k && (k < SPEED_CURVATURE[i - 1][1] || (i == 1 && k == SPEED_CURVATURE[0][1]))){
				double u = (k - SPEED_CURVATURE[i][1]) / (SPEED_CURVATURE[i - 1][1] - SPEED_CURVATURE[i][1]);
				return MathsUtils.lerp(SPEED_CURVATURE[i][0], SPEED_CURVATURE[i - 1][0], u);
			}
		}

		return 0;
	}

	public static double getTurnRadius(double v){
		if(v == 0)
			return 0;
		return 1.0 / curvature(v);
	}

	public static double maxSpeedForTurn(Car car, Vector3 target){
		Vector2 local = MathsUtils.local(car, target).flatten();

		double low = 100;
		double high = Constants.MAX_CAR_VELOCITY;

		final int STEPS = 20;
		for(int i = 0; i < STEPS; i++){
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

	public static double estimateDodgeDistance(Car car){
		return (car.forwardVelocityAbs + Constants.DODGE_IMPULSE) * 1.45;
	}

}
