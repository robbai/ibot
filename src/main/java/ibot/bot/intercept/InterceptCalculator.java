package ibot.bot.intercept;

import java.util.OptionalDouble;

import ibot.bot.actions.DriveStrike;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.physics.JumpPhysics;
import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.bot.utils.StaticClass;
import ibot.input.Car;
import ibot.prediction.BallPrediction;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class InterceptCalculator extends StaticClass {

	public static final Vector2 xSkew = new Vector2(Math.sqrt(0.2) * 2, Math.sqrt(0.2));

	public static Intercept aerialCalculate(DataBot bot, Car car, double gravity, OptionalDouble boost, AerialType type,
			Mode mode, boolean kickoff, boolean chooseStrongest){
		if(BallPrediction.isEmpty())
			return null;

		Vector3 carPosition = car.position;
		Vector3 interceptFrom = carPosition;

		final double RADIUS = (Constants.BALL_RADIUS - 20);

		AerialCalculator strongest = null;
		Slice strongestSlice = null;
		Vector3 strongestInterceptPosition = null;

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
//		for(int i = (type == AerialType.DODGE_STRIKE ? (int)((Constants.MAX_DODGE_DELAY + Aerial.DODGE_TIME - 0.1) * 60) : ballPrediction.slicesLength() - 1); i >= 0; i--){
			Slice slice = BallPrediction.get(i);

			if(type == AerialType.DODGE_STRIKE
					&& slice.time - car.time > (Constants.MAX_DODGE_DELAY + DriveStrike.DODGE_TIME - 0.05)){
				break;
			}

			Vector3 interceptPosition;
			if(mode == Mode.DROPSHOT){
				if(slice.position.y * car.sign <= 0){
					Vector3 offset = interceptFrom.minus(slice.position).normalised();
					interceptPosition = slice.position.plus(offset
							.withZ(MathsUtils.clamp(offset.z, -0.5 / Math.abs(offset.z), 0)).scaleToMagnitude(RADIUS));
				}else{
					Vector3 offset = interceptFrom.minus(slice.position).normalised();
					interceptPosition = slice.position.plus(
							offset.withZ(MathsUtils.clamp(offset.z, 0.65 / Math.abs(offset.z), 1 / Math.abs(offset.z)))
									.scaleToMagnitude(RADIUS));
				}
			}else{
//				Vector2 goal = new Vector2(0, (mode == Mode.HOOPS ? Constants.PITCH_LENGTH_HOOPS : Constants.PITCH_WIDTH_SOCCAR) * car.sign);
				Vector2 goal = (car.team == bot.team ? bot.enemyGoal.flatten() : bot.homeGoal.flatten());
				Vector2 offset = getOffset(car, slice.position, goal);
				if(goal.distance(slice.position.flatten()) < 3000){
//				if(mode == Mode.HOOPS || Math.abs(slice.position.x) > Constants.GOAL_WIDTH - 200)
					offset = offset.multiply(xSkew);
				}
//				else{
//					offset = offset.multiply(xSkew.reciprocal());
//				}
				interceptPosition = slice.position.plus(offset.withZ(0).scale(RADIUS));
			}

			AerialCalculator calculation = AerialCalculator.isViable(car, interceptPosition, slice.time, gravity, type);
			if(calculation.viable){
				if(strongest == null || strongest.finalVelocity < calculation.finalVelocity){
					strongest = calculation;
					strongestSlice = slice;
					strongestInterceptPosition = interceptPosition;
				}
				if(!chooseStrongest){
					break;
				}
			}
		}

		if(strongest == null)
			return null; // Uh oh.
		return new Intercept(strongestSlice.position, car, strongestInterceptPosition, strongestSlice.time);
	}

	public static Intercept aerialCalculate(DataBot bot, Car car, double gravity, AerialType type, Mode mode,
			boolean kickoff, boolean chooseStrongest){
		return aerialCalculate(bot, car, gravity, OptionalDouble.empty(), type, mode, kickoff, chooseStrongest);
	}

	public static Intercept aerialCalculate(DataBot bot, Car car, double gravity, double boost, AerialType type,
			Mode mode, boolean kickoff, boolean chooseStrongest){
		return aerialCalculate(bot, car, gravity, OptionalDouble.of(boost), type, mode, kickoff, chooseStrongest);
	}

	public static Intercept groundCalculate(DataBot bot, Car car, Mode mode){
		if(BallPrediction.isEmpty())
			return null;

		final double RADIUS = (Constants.BALL_RADIUS + 15);

		final double MAX_Z = JumpPhysics.maxZ(bot, Constants.JUMP_MAX_HOLD, true) + Constants.BALL_RADIUS * 0.25;

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			Slice slice = BallPrediction.get(i);
			boolean finalSlice = (i == BallPrediction.SLICE_COUNT - 1);

			double z = slice.position.minus(car.position).dot(car.orientation.up);
			if(!finalSlice && z > MAX_Z){
				continue;
			}

			Vector3 offset;
			if(mode == Mode.DROPSHOT){
				offset = car.position.minus(slice.position).scaleToMagnitude(RADIUS);
			}else{
				Vector2 goal = (car.team == bot.team ? bot.enemyGoal.flatten() : bot.homeGoal.flatten());
				offset = getOffset(car, slice.position, goal).withZ(0).scale(RADIUS);
			}

			double distance = (slice.position.distance(car.position) - RADIUS);
			double initialVelocity = car.velocity.dot(slice.position.minus(car.position).normalised());

			if(finalSlice || DrivePhysics.maxDistance(slice.time - car.time, initialVelocity, car.boost) > distance){
				Vector3 interceptPosition = slice.position.plus(offset);
				return new Intercept(slice.position, car, interceptPosition, slice.time);
			}
		}

		return null; // Uh oh.
	}

	/**
	 * This is intended for finding a intercept on the wall to aim for when the car
	 * is still on the ground, it is imprecise.
	 */
	public static Intercept wallCalculate(DataBot bot, Car car, double maxTime){
		if(BallPrediction.isEmpty() || bot.mode != Mode.SOCCAR || !car.onFlatGround)
			return null;

		final double RADIUS = (Constants.BALL_RADIUS + 30);
		final double WALL_SIZE = 260;
		final double VIOLENCE = 2.25;

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			Slice slice = BallPrediction.get(i);
			boolean finalSlice = (i == BallPrediction.SLICE_COUNT - 1);

			if(slice.time > maxTime)
				break;

			boolean xSide = (Math.abs(slice.position.x) > Constants.PITCH_WIDTH_SOCCAR - WALL_SIZE);
			boolean ySide = (Math.abs(slice.position.y) > Constants.PITCH_LENGTH_SOCCAR - WALL_SIZE);
//			if(!(xSide || ySide)) continue;
//			if(!(xSide || (ySide && slice.position.y * car.sign < 0))) continue;
			if(!(xSide || ySide) || slice.position.y * car.sign > Constants.PITCH_LENGTH_SOCCAR - 600)
				continue;

			if(Math.abs(slice.position.x) < Constants.GOAL_WIDTH && slice.position.z < Constants.GOAL_HEIGHT)
				continue;

			Vector3 local = MathsUtils.local(car, slice.position);
//			if(local.z < 250) continue;
			if(slice.position.z < 275)
				continue;

//			Vector2 goal = (car.team == bot.team ? bot.enemyGoal.flatten() : bot.homeGoal.flatten());
//			Vector3 offset = slice.position.minus(goal).withZ(0).scaledToMagnitude(RADIUS);

			double distance = (local.flatten().magnitude() + local.z - RADIUS) * 1.1;
			double initialVelocity = car.velocity.dot(slice.position.minus(car.position).normalised());

			if(finalSlice || DrivePhysics.maxDistance(slice.time - car.time, initialVelocity, car.boost) > distance){
				Vector3 interceptPosition = slice.position.minus(car.position)
						.multiply(new Vector3(
								xSide || Math.abs(car.position.x) < Constants.GOAL_WIDTH + 200 ? VIOLENCE : 0,
								ySide ? VIOLENCE : 0, 0))
						.plus(car.position);
				return new Intercept(slice.position, car, interceptPosition, slice.time);
			}
		}

		return null; // Uh oh.
	}

	private static Vector2 getOffset(Car car, Vector3 slicePosition, Vector2 goal){
		final double maxAngle = (Math.PI * 0.475);
		Vector2 offset = slicePosition.flatten().minus(goal);
		Vector2 carSide = car.position.minus(slicePosition).flatten();
		return carSide.rotate(MathsUtils.clamp(carSide.correctionAngle(offset), -maxAngle, maxAngle)).normalised();
	}

	public static Intercept groundCalculate(CarSlice[] slices, Car car){
		if(slices == null)
			return null;

		final double RADIUS = 60;

		for(int i = 0; i < slices.length; i++){
			Slice slice = slices[i];

			double distance = slice.position.distance(car.position) - RADIUS;
			double initialVelocity = car.velocity.dot(slice.position.minus(car.position).normalised());

			if(DrivePhysics.maxDistance(slice.time - car.time, initialVelocity, car.boost) > distance){
				Vector3 interceptPosition = slice.position
						.plus(car.position.minus(slice.position).scaleToMagnitude(RADIUS));
				return new Intercept(slice.position, car, interceptPosition, slice.time);
			}
		}

		return null; // Uh oh.
	}

}
