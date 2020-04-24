package ibot.bot.bots;

import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.BallState;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import ibot.bot.input.Info;
import ibot.bot.intercept.SeamIntercept;
import ibot.bot.step.Step;
import ibot.bot.step.steps.AtbaStep;
import ibot.bot.step.steps.DriveStrikeStep;
import ibot.bot.step.steps.IdleStep;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.input.DataPacket;
import ibot.input.Rotator;
import ibot.vectors.Vector3;

public class TestBot extends ABot {

	private double setTime;
	private boolean wall = true;

	public TestBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Step fallbackStep(){
		DataPacket packet = this.bundle.packet;
		Info info = this.bundle.info;

		boolean idle = (this.getActiveStep() instanceof IdleStep);
		if(!idle || (packet.time - this.setTime) % 10 > 5){
			if(idle || !MathsUtils.between(packet.ball.position.y * this.sign, -2000, 1000)
					|| packet.ball.position.x > 1000){
//				this.wall = !this.wall;
				GameState gameState = new GameState();
				gameState.withBallState(new BallState().withPhysics(
						new PhysicsState().withLocation(new Vector3(1000, 0, Constants.BALL_RADIUS).toDesired())
								.withVelocity(new Vector3(MathsUtils.random(1500, 2500) / (this.wall ? 1 : 100),
										-MathsUtils.random(100, 500) * this.sign,
										MathsUtils.random(100, 350) * (this.wall ? 1 : 4)).toDesired())));
				gameState
						.withCarState(this.index,
								new CarState().withBoostAmount(100F)
										.withPhysics(new PhysicsState()
												.withLocation(new Vector3(MathsUtils.random(2000, 3500),
														-MathsUtils.random(2500, 4500) * this.sign
																* (this.wall ? 1 : -0.1),
														Constants.CAR_HEIGHT).toDesired())
												.withVelocity(new Vector3().toDesired())
												.withRotation(new Rotator(0, 0, Math.PI * (this.wall ? 1 : 3) / 4)
														.toDesired())));
				RLBotDll.setGameState(gameState.buildPacket());
				this.setTime = packet.time;
			}
		}

		if(packet.time - this.setTime < 0.1){
			if(idle){
				return null;
			}
			this.clearSteps();
			return new IdleStep(this.bundle);
		}

		if((info.groundIntercept instanceof SeamIntercept || !this.wall) && packet.car.hasWheelContact){
			return new DriveStrikeStep(this.bundle, info.groundIntercept, false);
		}

		return new AtbaStep(this.bundle);
	}

}
