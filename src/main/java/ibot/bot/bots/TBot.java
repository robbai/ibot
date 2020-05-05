package ibot.bot.bots;

import java.util.ArrayList;

import ibot.bot.abort.SliceOffPredictionAbort;
import ibot.bot.path.Curve;
import ibot.bot.path.Path;
import ibot.bot.path.curves.Biarc;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.step.steps.AtbaStep;
import ibot.bot.step.steps.FollowPathStep;
import ibot.bot.step.steps.JumpStep;
import ibot.bot.utils.Constants;
import ibot.input.Car;
import ibot.prediction.BallPrediction;
import ibot.prediction.BallSlice;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;

public class TBot extends ABot {

	public TBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Step fallbackStep(){
		Car car = this.bundle.packet.car;

		if(this.stepsPriority() > Priority.IDLE)
			return null;

		if(car.onFlatGround){
			Vector2 carPosition = this.bundle.packet.car.position.flatten();
			Vector2 enemyGoal = new Vector2(0, Constants.PITCH_LENGTH_SOCCAR * car.sign);

			ArrayList<BallSlice> bounces = new ArrayList<BallSlice>();
			for(int i = 1; i < BallPrediction.SLICE_COUNT; i++){
				if(BallPrediction.get(i).position.z < 130){
					bounces.add(BallPrediction.get(i));
				}
			}

			for(int i = 0; i < bounces.size(); i++){
				Slice slice = bounces.get(i);
				double time = slice.time - car.time;
				Vector2 ballPosition = slice.position.flatten();
				ballPosition = ballPosition
						.plus(ballPosition.minus(enemyGoal).scaleToMagnitude(Constants.BALL_RADIUS + 100));
				Curve curve = new Biarc(carPosition, car.orientation.forward.flatten(), ballPosition,
						enemyGoal.minus(ballPosition));
				Path path = new Path(car.forwardVelocityAbs, car.boost, curve, time);
				if(path.getTime() < time){
					FollowPathStep follow = new FollowPathStep(this.bundle, path, time + car.time);
					follow.dodge = true;
					follow.linearTarget = true;
					return follow.withAbortCondition(new SliceOffPredictionAbort(this.bundle, slice));
				}
			}
		}else if(car.hasWheelContact){
			return new JumpStep(this.bundle, Constants.JUMP_MAX_HOLD);
		}

		return new AtbaStep(this.bundle);
	}

}
