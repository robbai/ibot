package ibot.bot.bots;

import java.util.ArrayList;

import ibot.bot.abort.BallTouchedAbort;
import ibot.bot.abort.SliceOffPredictionAbort;
import ibot.bot.input.Info;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.path.Path;
import ibot.bot.path.curves.Biarc;
import ibot.bot.step.Step;
import ibot.bot.step.steps.AerialStep;
import ibot.bot.step.steps.DefenseStep;
import ibot.bot.step.steps.DriveStrikeStep;
import ibot.bot.step.steps.FollowPathStep;
import ibot.bot.step.steps.FunStep;
import ibot.bot.step.steps.OffenseStep;
import ibot.bot.step.steps.SaveStep;
import ibot.bot.step.steps.kickoff.KickoffStep;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.prediction.BallPrediction;
import ibot.prediction.BallSlice;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class IBot extends ABot {

	public IBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Step fallbackStep(){
		DataPacket packet = this.bundle.packet;
//		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if(info.isKickoff){
			if(info.arena.getMode() == Mode.SOCCAR){
				return new KickoffStep(this.bundle);
			}
		}else if(FunStep.canHaveFun(this.bundle)){
			return new FunStep(this.bundle);
		}

		if(car.onFlatGround && this.iteration < 10 && info.possession > 0){
			Vector2 carPosition = this.bundle.packet.car.position.flatten();
			Vector2 enemyGoalCentre = new Vector2(0, Constants.PITCH_LENGTH_SOCCAR * car.sign);

			ArrayList<BallSlice> bounces = new ArrayList<BallSlice>();
			for(int i = 1; i < BallPrediction.SLICE_COUNT; i++){
				Slice slice = BallPrediction.get(i);
				if(slice.time > info.earliestEnemyIntercept.time - 0.05
						|| Math.abs(slice.position.y) > Constants.PITCH_LENGTH_SOCCAR)
					break;
				if(slice.position.z < 130){
					bounces.add(BallPrediction.get(i));
				}
			}

			final double BORDER = 50;
//			final double MIN_RADIUS = DrivePhysics.getTurnRadius(650);

			for(int i = 0; i < bounces.size(); i++){
				BallSlice slice = bounces.get(i);
				double time = slice.time - car.time;
				Vector2 ballPosition = slice.position.flatten();

//				if(ballPosition.distance(enemyGoalCentre) > 6000)
//					continue;

				Vector2 goalDirection = enemyGoalCentre.minus(ballPosition).normalised();
//				if(goalDirection.dot(ballPosition.minus(carPosition).normalised()) < 0.1)
//					continue;
				Vector2 incorrectVelocity = goalDirection.cross();
				incorrectVelocity = incorrectVelocity.scale(incorrectVelocity.dot(slice.velocity.flatten()));
				Vector2 enemyGoal = enemyGoalCentre.minus(incorrectVelocity.withY(0).scale(0.5));

				ballPosition = ballPosition
						.plus(ballPosition.minus(enemyGoal).scaleToMagnitude(Constants.BALL_RADIUS + 100));
				if(ballPosition.distance(carPosition) < 400)
					continue;

				Biarc curve = new Biarc(carPosition, car.orientation.forward.flatten(), ballPosition,
						enemyGoal.minus(ballPosition));
//				if(curve.getRadius(2) < MIN_RADIUS)
//					continue;
				Vector2[] points = curve.discretise(Path.ANALYSE_POINTS);
				boolean outside = false;
				for(int j = 0; j < points.length; j += 5){
					if(Math.abs(points[j].x) > Constants.PITCH_WIDTH_SOCCAR - BORDER
							|| Math.abs(points[j].y) > Constants.PITCH_LENGTH_SOCCAR - BORDER){
						outside = true;
						break;
					}
				}
				if(outside)
					continue;

				Path path = new Path(car.forwardVelocityAbs, car.boost, points, time);
				if(path.getSpeed(1) < Math.max(1000, car.forwardVelocityAbs * 0.8))
					continue;
				if(path.getTime() < time){
					FollowPathStep follow = new FollowPathStep(this.bundle, path, time + car.time);
					follow.dodge = true;
					follow.linearTarget = true;
					return follow.withAbortCondition(new SliceOffPredictionAbort(this.bundle, slice));
				}
			}
		}

		if(this.iteration < 5 && info.commit
				&& (car.correctSide(info.groundIntercept.position) || info.teamPossessionCorrectSide < 0)){
			boolean doubleJump;
			if(info.isKickoff || !car.hasWheelContact){
				// We use the double-jump intercept for starting mid-air too.
				doubleJump = true;
			}else if(info.aerialDouble != null){
				Vector3 localDouble = MathsUtils.local(car, info.aerialDouble.position);
				doubleJump = (localDouble.z > 550
						&& localDouble.normalised().z > MathsUtils.lerp(0.6, 0.3,
								Math.pow(car.velocity.magnitude() / Constants.MAX_CAR_VELOCITY, 2))
						|| (info.aerialDouble.position.z > Constants.GOAL_HEIGHT - 50
								&& Math.abs(info.aerialDouble.position.x) < Constants.GOAL_WIDTH + 200
								&& Math.abs(info.aerialDouble.position.y) < -Constants.PITCH_LENGTH_SOCCAR + 1100));
			}else{
				doubleJump = (info.aerialDodge == null);
			}
			AerialType type = (doubleJump ? AerialType.DOUBLE_JUMP : AerialType.DODGE_STRIKE);
			Intercept aerialIntercept = (doubleJump ? info.aerialDouble : info.aerialDodge);

			if(aerialIntercept != null){
				DriveStrikeStep driveStrike = (DriveStrikeStep)this.findStep(DriveStrikeStep.class.getName());
				boolean slower = false;
				if(driveStrike != null){
					if(!car.hasWheelContact || driveStrike.intercept.time < aerialIntercept.time){
						slower = true;
					}
				}

				Vector3 localIntercept = MathsUtils.local(car, aerialIntercept.position);
				double radians = Vector2.Y.correctionAngle(localIntercept.flatten());
				boolean theirSide = (aerialIntercept.position.y * car.sign >= 0);
				boolean contested = (Math.abs(info.possession) < 0.15);
				if(!slower
						&& Math.abs(radians) < Math.toRadians(doubleJump ? 35 : 45)
								* (info.goingInHomeGoal || contested ? 1.5 : 1)
						&& (info.groundIntercept == null
								|| localIntercept.z > (localIntercept.magnitude() < 700 ? 90 : (theirSide ? 180 : 230))
								|| (car.position.z > Math.max(500, aerialIntercept.position.z)
										&& info.arena.getMode() == Mode.DROPSHOT))){
					return new AerialStep(this.bundle, aerialIntercept, type).withAbortCondition(
							new BallTouchedAbort(this.bundle, packet.ball.latestTouch, this.index),
							new SliceOffPredictionAbort(this.bundle, aerialIntercept));
				}
			}
		}

		if(SaveStep.mustSave(this.bundle)){
			return new SaveStep(this.bundle);
		}

		if(info.commit && (car.correctSide(info.groundIntercept.position) || info.furthestBack)){
			return new OffenseStep(this.bundle);
		}

		return new DefenseStep(this.bundle);
	}

}
