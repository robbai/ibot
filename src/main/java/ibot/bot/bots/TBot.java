package ibot.bot.bots;

import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.BallState;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import ibot.bot.input.Info;
import ibot.bot.step.steps.AtbaStep;
import ibot.bot.step.steps.DemoStep;
import ibot.bot.step.steps.DriveStrikeStep;
import ibot.bot.step.steps.IdleStep;
import ibot.bot.step.steps.OffenseStep;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;
import ibot.input.DataPacket;
import ibot.input.Rotator;
import ibot.output.Output;
import ibot.vectors.Vector3;

public class TBot extends ABot {

	private double setTime;

	public TBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Output fallback(){
		DataPacket packet = this.bundle.packet;
		Info info = this.bundle.info;

		if(packet.robbie != null){
			if(packet.robbie.isDemolished)
				return new OffenseStep(this.bundle);
			return new DemoStep(this.bundle);
		}

		if((packet.time - this.setTime) > 8 || !packet.car.correctSide(packet.ball.position)){
			GameState gameState = new GameState();
			gameState.withBallState(new BallState()
					.withPhysics(new PhysicsState().withLocation(new Vector3(0, 0, Constants.BALL_RADIUS).toDesired())
							.withVelocity(new Vector3(0, 0, MathsUtils.random(800, 2000)).toDesired())
							.withAngularVelocity(new Vector3().toDesired())));
			gameState.withCarState(this.index, new CarState().withBoostAmount(100F).withPhysics(new PhysicsState()
					.withLocation(new Vector3(0, MathsUtils.random(2000, 4000) * -this.sign, Constants.CAR_HEIGHT)
							.toDesired())
					.withVelocity(new Vector3().toDesired()).withRotation(new Rotator(0, 0, Math.PI / 2).toDesired())));
			RLBotDll.setGameState(gameState.buildPacket());
			this.setTime = packet.time;
		}

		if(packet.time - this.setTime < 0.01){
			if(this.getActiveStep() instanceof IdleStep)
				return null;
			this.clearSteps();
			AtbaStep atba = new AtbaStep(this.bundle);
			return atba;
		}

		if(packet.car.hasWheelContact && info.doubleJumpIntercept != null){
			return new DriveStrikeStep(this.bundle, info.doubleJumpIntercept, true);
		}

		return new AtbaStep(this.bundle);
	}

}
