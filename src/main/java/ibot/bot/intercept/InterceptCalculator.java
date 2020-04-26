package ibot.bot.intercept;

import ibot.bot.input.Info;
import ibot.bot.input.arena.Arena;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.physics.JumpPhysics;
import ibot.bot.step.steps.DriveStrikeStep;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.bot.utils.Plane;
import ibot.bot.utils.StaticClass;
import ibot.input.Car;
import ibot.prediction.BallPrediction;
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

		final double RADIUS = (Constants.BALL_RADIUS - 30);

		AerialCalculator strongest = null;
		Slice strongestSlice = null;
		Vector3 strongestInterceptPosition = null;

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			Slice slice = BallPrediction.get(i);

			if(type == AerialType.DODGE_STRIKE
					&& slice.time - car.time > (Constants.MAX_DODGE_DELAY + DriveStrikeStep.DODGE_TIME - 0.05)){
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
				Vector2 direction = slice.position.minus(car.position).flatten().normalised();
				Vector2 xTrace = MathsUtils.traceToX(car.position.flatten(), direction, arena.getWidth());
				if(xTrace == null || xTrace.y * car.sign > -2000){
					Vector2 goal = (car.team == info.bot.team ? info.enemyGoal.flatten() : info.homeGoal.flatten());
					Vector2 offset = getOffset(car, slice.position, goal);
					if(goal.distance(slice.position.flatten()) < 3000){
						offset = offset.multiply(X_SKEW);
					}
					interceptPosition = slice.position.plus(offset.withZ(0).scale(RADIUS));
				}else{
					Vector2 corner = xTrace.withY(Math.max(xTrace.y * car.sign, -arena.getLength()) * car.sign);
					Vector2 offset = getOffset(car, slice.position, corner);
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

	public static Intercept groundCalculate(Info info, Car car, Vector2 goal, boolean doubleJump){
		if(BallPrediction.isEmpty())
			return null;

		final double RADIUS = (Constants.BALL_RADIUS + (doubleJump ? -35 : 10));

		Arena arena = info.arena;
		Mode mode = arena.getMode();

		// Car.
		Plane carPlane = arena.getClosestPlane(car.position);
		boolean ourSide = (car.position.y * car.sign < 0);

		// Jumping.
		final double MIN_Z = (doubleJump ? JumpPhysics.maxZ(arena.getGravity(), 0, true) - 75 : Double.MIN_VALUE);
		final double MAX_Z = JumpPhysics.maxZ(arena.getGravity(), Constants.JUMP_MAX_HOLD, doubleJump)
				+ (doubleJump ? 100 : 55) + (!ourSide && doubleJump ? -50 : 0);

		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			Slice slice = BallPrediction.get(i);
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
			boolean differentPlane = slicePlane.differentNormal(car);

			double z = (slicePlane.getNormalDistance(slice.position) - Constants.CAR_HEIGHT);
			if(!finalSlice && (z < MIN_Z || z > (differentPlane ? MAX_Z - 10 : MAX_Z))){
				continue;
			}

			Vector3 offset;
			if(differentPlane){
				if(slicePlane.differentNormal(Vector3.Y)){
					offset = Vector3.Y.scaleToMagnitude((RADIUS + 10) * -car.sign);
				}else{
					offset = Vector3.X.scaleToMagnitude((RADIUS + 10) * -Math.signum(slice.position.x));
				}
			}else if(mode == Mode.DROPSHOT){
				offset = car.position.minus(slice.position).scaleToMagnitude(RADIUS);
			}else{
				offset = getOffset(car, slice.position, goal).withZ(0).scale(RADIUS);
			}

			Vector3 interceptPosition = slice.position.plus(offset);
			if(finalSlice){
				return new Intercept(slice.position, car, interceptPosition, slicePlane, slice.time);
			}else if(!differentPlane){
				double distance = MathsUtils.local(car, interceptPosition).flatten().magnitude();
				double initialVelocity = car.velocity.dot(interceptPosition.minus(car.position).normalised());
				if(DrivePhysics.maxDistance(slice.time - car.time - turnTime(car, interceptPosition), initialVelocity,
						car.boost) > distance){
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
				double time = DrivePhysics.minTravelTime(initialVelocity, car.boost, distanceFromTargetPlane);
				initialVelocity = DrivePhysics.maxVelocityDist(initialVelocity, car.boost, distanceFromTargetPlane);
				initialVelocity -= initialVelocity * (Math.abs(slicePlane.normal.dot(Vector3.Y)) > 0.9 ? 0.45 : 0.3)
						* (1 - Math.sin(angle));
				time += DrivePhysics.minTravelTime(initialVelocity, car.boost, targetDistanceFromCarPlane);
				time += turnTime(car, seamPosition);
				if(time < slice.time - car.time){
					return new SeamIntercept(slice.position, car, interceptPosition, slicePlane, seamPosition,
							slice.time);
				}
			}
		}

		return null; // Uh oh.
	}

	private static double turnTime(Car car, Vector3 vector){
		double radians = Vector2.Y.angle(MathsUtils.local(car, vector).flatten());
		if(car.position.distance(vector) < 2000){
			radians = Math.abs(MathsUtils.shorterAngle(radians));
		}
		return radians * 0.3;
	}

	private static Vector2 getOffset(Car car, Vector3 slicePosition, Vector2 goal){
		final double maxAngle = (Math.PI * 0.475);
		Vector2 offset = slicePosition.flatten().minus(goal);
		Vector2 carSide = car.position.minus(slicePosition).flatten();
		return carSide.rotate(MathsUtils.clamp(carSide.correctionAngle(offset), -maxAngle, maxAngle)).normalised();
	}

}
