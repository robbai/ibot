package ibot.bot.bots;

import ibot.bot.abort.BallTouchedAbort;
import ibot.bot.path.Curve;
import ibot.bot.path.Path;
import ibot.bot.path.curves.CompositeArc;
import ibot.bot.step.Step;
import ibot.bot.step.steps.AtbaStep;
import ibot.bot.step.steps.FollowPathStep;
import ibot.bot.step.steps.JumpStep;
import ibot.bot.utils.Constants;
import ibot.input.Car;
import ibot.prediction.BallPrediction;
import ibot.vectors.Vector2;

public class TBot extends ABot {

	public TBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Step fallbackStep(){
		Car car = this.bundle.packet.car;

		if(car.onFlatGround){
			Vector2 carPosition = this.bundle.packet.car.position.flatten();
			Vector2 enemyGoal = new Vector2(0, Constants.PITCH_LENGTH_SOCCAR * car.sign);

			Path path = null;
			double time = 0;
			int low = 0;
			int high = BallPrediction.SLICE_COUNT - 1;
			while(low < high){
				int mid = Math.floorDiv(low + high, 2);
				time = BallPrediction.get(mid).time - car.time;
				Vector2 ballPosition = BallPrediction.get(mid).position.flatten();
				ballPosition = ballPosition.plus(ballPosition.minus(enemyGoal).scaleToMagnitude(150));
//				Curve curve = new Biarc(carPosition, car.orientation.forward.flatten(), ballPosition,
//						enemyGoal.minus(ballPosition));
				Curve curve = CompositeArc.create(car, ballPosition, enemyGoal, 100, 100);
				if(curve == null /*
									 * || 1000 < (2 * (curve.getLength() - time * car.forwardVelocity)) /
									 * Math.pow(time, 2)
									 */){
					low = mid + 1;
					continue;
				}
				path = new Path(car.forwardVelocityAbs, car.boost, curve, time);
				if(path.getTime() > time){
					low = mid + 1;
				}else{
					high = mid;
				}
			}

			if(path != null){
				FollowPathStep follow = new FollowPathStep(this.bundle, path, time + car.time);
				follow.dodge = true;
				if(car.team == 0)
					follow.linearTarget = true;
				return follow
						.withAbortCondition(new BallTouchedAbort(this.bundle, this.bundle.packet.ball.latestTouch));
			}
		}else if(car.hasWheelContact){
			return new JumpStep(this.bundle, Constants.JUMP_MAX_HOLD);
		}

		return new AtbaStep(this.bundle);
	}

}
