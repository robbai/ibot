package ibot.bot.intercept;

import java.util.Arrays;

import ibot.bot.input.Info;
import ibot.bot.input.arena.Arena;
import ibot.bot.physics.Car1D;
import ibot.bot.physics.JumpPhysics;
import ibot.bot.physics.Routing;
import ibot.bot.step.steps.DriveStrikeStep;
import ibot.bot.utils.StaticClass;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.maths.Plane;
import ibot.bot.utils.rl.Constants;
import ibot.bot.utils.rl.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.prediction.BallPrediction;
import ibot.prediction.BallSlice;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class InterceptCalculator extends StaticClass {

	public static final Vector2 X_SKEW = new Vector2(Math.sqrt(0.2) * 2, Math.sqrt(0.2));

	public static Intercept aerialCalculate(Info info, Car car, AerialType type, Mode mode, boolean kickoff,
			boolean chooseStrongest){
		if(BallPrediction.isEmpty())
			return null;

		Arena arena = info.arena;
		Vector3 carPosition = car.position;
		Vector3 interceptFrom = carPosition;

		final double RADIUS = (Constants.BALL_RADIUS - 45);

		AerialCalculator strongest = null;
		Slice strongestSlice = null;
		Vector3 strongestInterceptPosition = null;

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i += 2){
			BallSlice slice = BallPrediction.get(i);

			if(type == AerialType.DODGE_STRIKE
					&& slice.time - car.time > (Constants.MAX_DODGE_DELAY + DriveStrikeStep.DODGE_TIME - 0.05)){
				break;
			}

			if(quickPrune(slice.position.distance(car.position) - RADIUS,
					car.velocity.dot(slice.position.minus(car.position).normalised()), slice.time - car.time, true))
				continue;

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
				Vector2 direction = slice.position.minus(car.position).flatten().normalised();
				Vector2 xTrace = MathsUtils.traceToX(car.position.flatten(), direction, arena.getWidth());
				if(xTrace == null || xTrace.y * car.sign > -2000){
					Vector2 goal = chooseGoal(arena, car, slice.position);
					Vector2 offset = getOffset(car, slice, goal);
					if(goal.distance(slice.position.flatten()) < 3000){
						offset = offset.multiply(X_SKEW);
					}
					interceptPosition = slice.position.plus(offset.withZ(0).scale(RADIUS));
				}else{
					Vector2 corner = xTrace.withY(Math.max(xTrace.y * car.sign, -arena.getLength()) * car.sign);
					Vector2 offset = getOffset(car, slice, corner);
					interceptPosition = slice.position.plus(offset.withZ(0).scale(RADIUS + 5));
				}
			}

			AerialCalculator calculation = AerialCalculator.isViable(car, interceptPosition, slice.time,
					arena.getGravity(), type);
			if(calculation.viable){
				if(strongest == null || strongest.finalVelocity < calculation.finalVelocity - i){
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
		return new Intercept(strongestSlice.position, car, strongestInterceptPosition, null, strongestSlice.time);
	}

	public static Intercept groundCalculate(DataPacket packet, Info info, Car car, boolean doubleJump){
		if(BallPrediction.isEmpty())
			return null;

		final double RADIUS = (Constants.BALL_RADIUS + (doubleJump ? -40 : 25));

		// Freefall collision.
		if(!car.hasWheelContact){
			Intercept freefallIntercept = freefallCalculate(car, info, RADIUS + 15);
			if(freefallIntercept != null)
				return freefallIntercept;
		}

		Arena arena = info.arena;
		Mode mode = arena.getMode();

		// Enemies for calculating furthest point.
		Car[] enemyCars = (car.team == packet.car.team ? packet.enemies : packet.teammates);
		Vector2[] enemies = Arrays.asList(enemyCars).stream().map(c -> c.position.flatten()).toArray(Vector2[]::new);
		Vector2 side = new Vector2(arena.getWidth() + 250, arena.getLength() * car.sign);

		// Car.
		Plane carPlane = Plane.asCar(car);
		boolean ourSide = (car.position.y * car.sign < 0);

		// Jumping.
		final double MIN_Z = (doubleJump ? JumpPhysics.maxZ(arena.getGravityAcc(), 0, true) - 75 : Double.MIN_VALUE);
		final double MAX_Z = JumpPhysics.maxZ(arena.getGravityAcc(), Constants.MAX_JUMP_HOLD_TIME, doubleJump)
				+ (doubleJump ? 100 : 55) + (!ourSide && doubleJump ? -50 : 0);

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i += 1){
			BallSlice slice = BallPrediction.get(i);
			boolean finalSlice = (i == BallPrediction.SLICE_COUNT - 1);

			// Slice plane.
			Plane slicePlane;
			if(Math.abs(slice.position.y) > info.arena.getLength() - 400
					&& (slice.position.y * car.sign > 0 || (Math.abs(slice.position.x) < Constants.GOAL_WIDTH)
							&& slice.position.z < Constants.GOAL_HEIGHT)){
				slicePlane = arena.getFloor();
			}else{
				double localZ = MathsUtils.local(car, slice.position).z;
				if(localZ > MAX_Z && !doubleJump){
					slicePlane = arena.getClosestPlane(slice.position);
					if(!finalSlice && Math.abs(car.position.y) > info.arena.getLength())
						continue;
				}else{
					slicePlane = carPlane;
				}
			}
			if(slicePlane.normal.dot(carPlane.normal) < -0.9){
				if(!finalSlice)
					continue;
				slicePlane = carPlane;
			}
			boolean differentPlane = slicePlane.differentNormal(carPlane);

			double z = (slicePlane.getNormalDistance(slice.position) - Constants.CAR_HEIGHT);
			if(!finalSlice && (z < MIN_Z || z > (differentPlane ? MAX_Z - 10 : MAX_Z))){
				continue;
			}

			Vector3 offset;
			if(differentPlane){
				if(slicePlane.differentNormal(Vector3.Y)){
					offset = Vector3.Y
							.scaleToMagnitude((car.correctSide(slice.position) ? RADIUS + 15 : RADIUS + 5) * -car.sign);
				}else{
					offset = Vector3.X.scaleToMagnitude((RADIUS + 10) * -Math.signum(slice.position.x));
				}
			}else{
				Vector2 direction = slice.position.minus(car.position).flatten().normalised();
				Vector2 xTrace = MathsUtils.traceToX(car.position.flatten(), direction,
						arena.getWidth() * Math.signum(direction.x));
				if(slice.position.y * car.sign < 1000 && (xTrace == null || xTrace.y * car.sign > 0)){
					Vector2 furthest = MathsUtils.furthestPointOnLineSegment(
							side.withX(Math.copySign(side.x, slice.position.x)), side.withX(0), enemies);
					offset = slice.position.minus(furthest.withZ(slice.position.z)).scaleToMagnitude(RADIUS);
				}else if(mode == Mode.DROPSHOT){
					offset = car.position.minus(slice.position).scaleToMagnitude(RADIUS);
				}else{
					if(xTrace == null || xTrace.y * car.sign > -3000){
						Vector2 goal = chooseGoal(arena, car, slice.position);
						offset = getOffset(car, slice, goal).withZ(0).scale(RADIUS);
					}else{
						Vector2 corner = xTrace
								.withY(Math.max(xTrace.y * car.sign + 900, -arena.getLength()) * car.sign);
						Vector2 yTrace = MathsUtils.traceToY(car.position.flatten(), direction,
								info.arena.getLength() * -car.sign);
						if(yTrace != null)
							corner = corner.withX(Math.copySign(corner.x, yTrace.x));
						offset = getOffset(car, slice, corner).withZ(0).scale(RADIUS + 5);
					}
				}
			}

			Vector3 interceptPosition = slice.position.plus(offset);
			if(finalSlice){
				return new Intercept(slice.position, car, interceptPosition, slicePlane, slice.time);
			}else if(!differentPlane){
				double distance = MathsUtils.local(car, interceptPosition).flatten().magnitude();
				double initialVelocity = car.velocity.dot(interceptPosition.minus(car.position).normalised());

				if(quickPrune(distance, initialVelocity, slice.time - car.time, false))
					continue;

				if(new Car1D(car).withVelocity(initialVelocity).stepDisplacement(1, true, distance)
						.getTime() < slice.time - Routing.estimateTurnTime(car, interceptPosition, true)){
					return new Intercept(slice.position, car, interceptPosition, slicePlane, slice.time);
				}
			}else{
				/**
				 * https://github.com/tarehart/ReliefBot/blob/9a0646d40e3ea16b431dd351177ff30dbe12f302/src/main/java/tarehart/rlbot/planning/SteerUtil.kt#L158-L165
				 */
				Vector3 toPositionOnTargetPlane = interceptPosition.minus(car.position)
						.projectToPlane(slicePlane.normal);
				Vector3 carShadowOnTargetPlane = car.position.shadowOntoPlane(slicePlane);
				double distanceFromTargetPlane = slicePlane.getNormalDistance(car.position);
				double targetDistanceFromCarPlane = carPlane.getNormalDistance(interceptPosition);
				double carPlaneWeight = (distanceFromTargetPlane
						/ (distanceFromTargetPlane + targetDistanceFromCarPlane));
				Vector3 toPositionAlongSeam = toPositionOnTargetPlane.projectToPlane(carPlane.normal);
				Vector3 seamPosition = carShadowOnTargetPlane.plus(toPositionAlongSeam.scale(carPlaneWeight));

				double angle = MathsUtils.local(car.orientation, slicePlane.normal).flatten()
						.angle(MathsUtils.local(car, seamPosition).flatten());
				double initialVelocity = car.velocity.dot(seamPosition.minus(car.position).normalised());

				if(quickPrune(distanceFromTargetPlane + targetDistanceFromCarPlane, initialVelocity,
						slice.time - car.time, false))
					continue;

				Car1D sim = new Car1D(car).withVelocity(initialVelocity).stepDisplacement(1, true,
						distanceFromTargetPlane);
				initialVelocity = sim.getVelocity();
				double factor = MathsUtils.lerp(1, (Math.abs(slicePlane.normal.dot(Vector3.Y)) > 0.9 ? 0.5 : 0.25),
						(Math.cos(angle) + 1) / 2);
				initialVelocity *= factor;
				sim.withVelocity(initialVelocity).stepDisplacement(1, true,
						distanceFromTargetPlane + targetDistanceFromCarPlane);
				if(sim.getTime() < slice.time - Routing.estimateTurnTime(car, seamPosition, true) + 0.1){
					return new SeamIntercept(slice.position, car, interceptPosition, slicePlane, seamPosition,
							slice.time);
				}
			}
		}

		return null; // Uh oh.
	}

	private static boolean quickPrune(double distance, double initialVelocity, double deltaTime, boolean aerial){
		double velocity = (distance - 300) / deltaTime;
		if(Math.abs(velocity) > Constants.MAX_CAR_VELOCITY)
			return true;
		double acceleration = (2 * (distance - deltaTime * initialVelocity)) / Math.pow(deltaTime, 2);
		return Math.abs(acceleration) > 100
				+ (aerial ? Constants.BOOST_AIR_ACCELERATION : Constants.BRAKE_ACCELERATION);
	}

//	private static Slice guessSlice(Car car){
//		Vector3 ballPosition = BallPrediction.get(0).position;
//		double initialSpeed = car.velocity.dot(ballPosition.minus(car.position).normalised());
//		double deltaTime = ballPosition.distance(car.position) / initialSpeed;
//		int index = (int)MathsUtils.clamp(Math.abs(deltaTime / BallPrediction.DT), 0, BallPrediction.SLICE_COUNT - 1);
//		return BallPrediction.get(index);
//	}

	private static Intercept freefallCalculate(Car car, Info info, double radius){
		Vector3 carPosition = car.position, carVelocity = car.velocity, gravity = info.arena.getGravity();

		double deltaTime = (BallPrediction.SLICE_COUNT - 1) * BallPrediction.DT;
		Vector3 futureCarPosition = carPosition.plus(carVelocity.scale(deltaTime))
				.plus(gravity.scale(0.5 * Math.pow(deltaTime, 2)));
		if(futureCarPosition.minus(BallPrediction.get(BallPrediction.SLICE_COUNT - 1).position)
				.dot(carPosition.minus(BallPrediction.get(0).position)) > 0)
			return null;

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i += (0.1 / BallPrediction.DT)){
			Slice slice = BallPrediction.get(i);
			Vector3 slicePosition = slice.position;
			deltaTime = slice.time - car.time;
			futureCarPosition = carPosition.plus(carVelocity.scale(deltaTime))
					.plus(gravity.scale(0.5 * Math.pow(deltaTime, 2)));
			if(futureCarPosition.distance(slicePosition) < radius)
				return new Intercept(slicePosition, car, futureCarPosition, info.arena.getClosestPlane(carPosition),
						slice.time);
		}
		return null;
	}

	private static Vector2 getOffset(Car car, BallSlice slice, Vector2 goal){
		final double maxAngle = (Math.PI * 0.425);
		Vector2 offset = slice.position.flatten().minus(goal);
//		Vector2 offset = optimalIntercept(slice, goal.withZ(MathsUtils.clamp(slice.position.z, Constants.BALL_RADIUS,
//				Constants.GOAL_HEIGHT - Constants.BALL_RADIUS))).flatten().scale(-1);
		Vector2 carSide = car.position.minus(slice.position).flatten();
		return carSide.rotate(MathsUtils.clamp(carSide.correctionAngle(offset), -maxAngle, maxAngle)).normalised();
	}

//	/**
//	 * https://github.com/LHolten/DisasterBot/blob/74350bda635062d689334a1d4d7bebf021677c08/util/linear_algebra.py#L37-L43
//	 * Provides vector for correcting an object's velocity vector towards the target
//	 * vector
//	 */
//	private static Vector3 optimalIntercept(BallSlice slice, Vector3 goal){
//		Vector3 targetDirection = goal.minus(slice.position).normalised();
//		Vector3 correctVelocity = targetDirection.scale(slice.velocity.dot(targetDirection));
//		Vector3 incorrectVelocity = slice.velocity.minus(correctVelocity);
//		double extraVelocity = Math.sqrt(Math.pow(6000, 2) - Math.pow(incorrectVelocity.magnitude(), 2));
//		return targetDirection.scale(extraVelocity).minus(incorrectVelocity);
//	}

	public static Vector2 chooseGoal(Arena arena, Car car, Vector3 interceptPosition){
		double y = arena.getLength() * car.sign;
		Vector2 trace = MathsUtils.traceToY(car.position.flatten(),
				interceptPosition.minus(car.position).flatten().normalised(), y);
		if(trace == null)
			return new Vector2(0, y);
		double maxX = Constants.GOAL_WIDTH - 200;
		double yDelta = (y - interceptPosition.y);
		return new Vector2(MathsUtils.clampMagnitude(trace.x, maxX)
				* (Math.abs(interceptPosition.x) > Constants.GOAL_WIDTH ? -1 : 1) * Math.max(1 - yDelta / 4000, 0), y);
	}

}
