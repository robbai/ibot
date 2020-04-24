package ibot.bot.bots;

import ibot.bot.abort.BallTouchedAbort;
import ibot.bot.abort.SliceOffPredictionAbort;
import ibot.bot.input.Info;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.step.Step;
import ibot.bot.step.steps.AerialStep;
import ibot.bot.step.steps.DefenseStep;
import ibot.bot.step.steps.FunStep;
import ibot.bot.step.steps.OffenseStep;
import ibot.bot.step.steps.SaveStep;
import ibot.bot.step.steps.kickoff.KickoffStep;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
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
				Vector3 localIntercept = MathsUtils.local(car, aerialIntercept.position);
				double radians = Vector2.Y.correctionAngle(localIntercept.flatten());
				boolean theirSide = (aerialIntercept.position.y * car.sign >= 0);
				boolean contested = (Math.abs(info.possession) < 0.15);
				if(Math.abs(radians) < Math.toRadians(doubleJump ? 35 : 45)
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
