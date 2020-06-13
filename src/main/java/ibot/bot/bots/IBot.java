package ibot.bot.bots;

import java.util.function.Function;

import ibot.boost.BoostPad;
import ibot.bot.abort.CommitAbort;
import ibot.bot.abort.NotMyPredictionAbort;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.step.steps.AerialStep;
import ibot.bot.step.steps.DefenseStep;
import ibot.bot.step.steps.DemoStep;
import ibot.bot.step.steps.DriveStep;
import ibot.bot.step.steps.FunStep;
import ibot.bot.step.steps.GrabObliviousStep;
import ibot.bot.step.steps.OffenseStep;
import ibot.bot.step.steps.SaveStep;
import ibot.bot.step.steps.kickoff.KickoffStep;
import ibot.bot.step.steps.meta.ChainStep;
import ibot.bot.step.steps.meta.ShouldStep;
import ibot.bot.utils.rl.Constants;
import ibot.bot.utils.rl.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class IBot extends ABot {

	public IBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Output fallback(){
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

//		if(car.onFlatGround && this.iteration < 2 && info.commit && info.possession > 0.8
//				&& this.stepsPriority() < Priority.STRIKE && car.forwardVelocity > 1600){
//			Vector2 carPosition = this.bundle.packet.car.position.flatten();
//			Vector2 enemyGoalCentre = new Vector2(0, Constants.PITCH_LENGTH_SOCCAR * car.sign);
//
//			ArrayList<BallSlice> bounces = new ArrayList<BallSlice>();
//			for(int i = 1; i < BallPrediction.SLICE_COUNT; i++){
//				Slice slice = BallPrediction.get(i);
//				if(slice.time > info.earliestEnemyIntercept.time - 0.8)
//					break;
//				if(slice.position.z < 120 && slice.time > info.groundIntercept.time){
//					bounces.add(BallPrediction.get(i));
//				}
//			}
//
//			final double BORDER = 60;
//
//			for(int i = 0; i < bounces.size(); i++){
//				BallSlice slice = bounces.get(i);
//				double time = slice.time - car.time;
//				Vector2 ballPosition = slice.position.flatten();
//
//				Vector2 goalDirection = enemyGoalCentre.minus(ballPosition).normalised();
//				Vector2 incorrectVelocity = goalDirection.cross();
//				incorrectVelocity = incorrectVelocity.scale(incorrectVelocity.dot(slice.velocity.flatten()));
//				Vector2 enemyGoal = enemyGoalCentre.minus(incorrectVelocity.withY(0).scale(0.5));
//
//				ballPosition = ballPosition
//						.plus(ballPosition.minus(enemyGoal).scaleToMagnitude(Constants.BALL_RADIUS + 70));
//				if(ballPosition.distance(carPosition) < 400)
//					continue;
//
//				if(enemyGoal.minus(ballPosition).normalised().dot(ballPosition.minus(carPosition).normalised()) < 0.3)
//					continue;
//
//				Biarc curve = new Biarc(carPosition, car.orientation.forward.flatten(), ballPosition,
//						enemyGoal.minus(ballPosition));
//				Vector2[] points = curve.discretise(Path.ANALYSE_POINTS);
//				boolean outside = false;
//				for(int j = 0; j < points.length; j += 5){
//					if(Math.abs(points[j].x) > Constants.PITCH_WIDTH_SOCCAR - BORDER
//							|| Math.abs(points[j].y) > Constants.PITCH_LENGTH_SOCCAR - BORDER){
//						outside = true;
//						break;
//					}
//				}
//				if(outside)
//					continue;
//
//				Path path = new Path(car.forwardVelocityAbs, car.boost, points, time);
////				if(path.getSpeed(1) < Math.max(Constants.MAX_CAR_THROTTLE_VELOCITY - 200, car.forwardVelocityAbs * 0.7))
////					continue;
//				if(path.getTime() < time){
//					FollowPathStep follow = new FollowPathStep(this.bundle, path, time + car.time);
//					follow.dodge = (ballPosition.distance(enemyGoalCentre) < 5000);
////					follow.dodge = true;
//					follow.linearTarget = true;
//					return follow.withAbortCondition(new SliceOffPredictionAbort(this.bundle, slice));
//				}
//			}
//		}

		if(this.iteration < 5 && car.boost > 5 && info.groundIntercept != null && info.commit){
			for(int i = 0; i < 2; i++){
				boolean doubleJump = (i == 1);
				Intercept intercept = (doubleJump ? info.aerialDouble : info.aerialDodge);
				if(intercept == null)
					continue;
				if(!doubleJump && !car.hasWheelContact)
					continue;
				if(intercept.time > info.earliestEnemyIntercept.time)
					continue;
				if(intercept.time > info.groundIntercept.time - Constants.DT)
					continue;
				if(info.lastMan && intercept.position.distance(info.homeGoal.withZ(0)) > 5000)
					continue;

				Vector2 offset = intercept.getOffset().flatten();
				double factor = offset.normalised().dot(info.groundIntercept.getOffset().flatten().normalised());
				if(factor > 0.4){
					AerialType type = (doubleJump ? AerialType.DOUBLE_JUMP : AerialType.DODGE_STRIKE);
					return new AerialStep(this.bundle, intercept, type)
							.withAbortCondition(new NotMyPredictionAbort(this.bundle, intercept));
				}
			}
		}

		if(info.lastMan && SaveStep.mustSave(this.bundle)){
			return new SaveStep(this.bundle);
		}

//		Vector2 trace = MathsUtils.traceToY(car.position.flatten(),
//				info.groundIntercept.position.minus(car.position).flatten(), info.arena.getLength() * car.sign);
//		boolean canShoot = (trace != null && Math.abs(trace.x) < Constants.GOAL_WIDTH - Constants.BALL_RADIUS);
//		trace = MathsUtils.traceToY(info.groundIntercept.car.position.flatten(),
//				info.groundIntercept.position.minus(info.groundIntercept.car.position).flatten(),
//				info.arena.getLength() * -car.sign);
//		boolean opponentShoot = (trace != null && Math.abs(trace.x) < Constants.GOAL_WIDTH - Constants.BALL_RADIUS);
//
//		boolean offense = ((info.commit || (canShoot && info.possession > -0.2))
//				&& ((info.teamPossessionCorrectSide < -0.3 && opponentShoot)
//						|| car.correctSide(info.groundIntercept.position))
//				&& (info.possession > -0.1 || car.boost > 30
//						|| info.groundIntercept.position.y * car.sign < info.arena.getLength() - 1400));
//		if(offense){
//			OffenseStep offenseStep = new OffenseStep(this.bundle);
//			if(!info.commit){
//				offenseStep.canPop = false;
//				offenseStep.withAbortCondition(new BallTouchedAbort(this.bundle));
//			}
//			return offenseStep;
//		}
//
//		return new DefenseStep(this.bundle);

		if(this.iteration < 7){
			BoostPad pad = info.nearestBoost;
			GrabObliviousStep oblivious = ((packet.ball.position.distance(info.homeGoal) > 4000 || !info.furthestBack)
					&& car.boost < (pad.isFullBoost() ? 60 : 20) ? new GrabObliviousStep(this.bundle, pad) : null);

			if(pad != null && (info.earliestEnemyIntercept.time - packet.time) > 0.7
					&& !car.correctSide(info.groundIntercept.position) && (pad.isFullBoost() || car.boost > 50)
					&& car.onFlatGround){
				DemoStep demo = new DemoStep(this.bundle);
				if(demo.isValid())
					return new ChainStep(this.bundle, oblivious, demo);
			}

			Vector2 target;
//			if(info.furthestBack){
			target = info.homeGoal.flatten();
////			if(info.slowestTeammate)
////				target = target.multiply(new Vector2(1.1, 1).clamp());
//			}else{
//				double targetDistance = (info.slowestTeammate ? 8000 : 6500);
//				Vector2 goal = info.homeGoal.flatten(), ball = packet.ball.position.flatten();
//				target = goal.setDistanceFrom(ball, Math.min(targetDistance, goal.distance(ball)));
//			}
//			Vector2 target = MathsUtils.traceToWall(info.earliestEnemyIntercept.car.position.flatten(),
//					info.earliestEnemyIntercept.position.minus(info.earliestEnemyIntercept.car.position).flatten(),
//					info.arena.getWidth(), info.arena.getLength());
//			target = target.withY(Math.min(target.y * car.sign, 0) * car.sign);
			DriveStep drive = new DriveStep(this.bundle, target);
//			drive.conserveBoost = true;
//			drive.dontBoost = (info.teamPossessionCorrectSide < 0.5);
			drive.dontBoost = true;
			drive.canPop = true;
			drive.popDistance = (info.slowestTeammate ? 1400 : 1100);
//			drive.withAbortCondition(new CommitAbort(this.bundle, 0));
//			drive.withAbortCondition(new PossessionAbort(this.bundle, 0.6, 0.85));

			Function<Bundle, Boolean> driveCondition = (b -> !(b.info.possession > 0.5
//					&& b.info.groundIntercept.position.y * b.packet.car.sign > 0
					&& b.info.groundIntercept.getAlignment() > 0.85));
			ShouldStep shouldDrive = new ShouldStep(this.bundle, drive, driveCondition);

			DefenseStep defense = new DefenseStep(this.bundle);
			defense.withAbortCondition(new CommitAbort(this.bundle, 0));
//			PossessionAbort possession = new PossessionAbort(this.bundle, -0.1);
//			possession.inverted = true;
//			defense.withAbortCondition(possession);

			OffenseStep offense = new OffenseStep(this.bundle);
			offense.minimumTime = info.groundIntercept.time - packet.time + (car.boost / 100) * 0.45;

			return new ChainStep(this.bundle, oblivious, shouldDrive, defense, offense);
		}
		return new DefenseStep(this.bundle);
	}

}
