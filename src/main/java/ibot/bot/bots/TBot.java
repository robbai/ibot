package ibot.bot.bots;

import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.BallState;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import ibot.bot.input.Info;
import ibot.bot.intercept.AerialType;
import ibot.bot.step.Priority;
import ibot.bot.step.steps.AerialStep;
import ibot.bot.step.steps.AtbaStep;
import ibot.bot.step.steps.DemoStep;
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

		boolean doubleJump = false;

		if((packet.time - this.setTime) > 8 || !packet.car.correctSide(packet.ball.position)
				|| packet.car.velocity.magnitude() < 1
				|| (packet.car.hasWheelContact && packet.car.position.y * this.sign > 0)){
			double distance = 3000;
			double carVelocity = 1500;
			double ballX = MathsUtils.random(-4000, 4000);
			double y = 0 * this.sign;
			double angle = MathsUtils.random(Math.PI * 0.45, Math.PI / 2) * (ballX < 0 ? 2 : 1);
			double velocityZ = MathsUtils.random(600, 1100) + (doubleJump ? 800 : 0);
			GameState gameState = new GameState();
			gameState.withBallState(new BallState().withPhysics(
					new PhysicsState().withLocation(new Vector3(ballX, y, Constants.BALL_RADIUS).toDesired())
							.withVelocity(new Vector3(-ballX / (doubleJump ? 1.5 : 2), 0, velocityZ).toDesired())
							.withAngularVelocity(new Vector3().toDesired())));
			gameState.withCarState(this.index, new CarState().withBoostAmount(100F).withPhysics(new PhysicsState()
					.withLocation(new Vector3(Math.sin(angle) * distance, Math.cos(angle) * distance * this.sign + y,
							Constants.CAR_HEIGHT).clamp(info.arena.getWidth() - 500, info.arena.getLength() - 500)
									.toDesired())
					.withVelocity(
							new Vector3(-Math.sin(angle) * carVelocity, -Math.cos(angle) * carVelocity * this.sign, 0)
									.toDesired())
					.withRotation(new Rotator(0, 0, -Math.PI / 2 - angle).toDesired())
					.withAngularVelocity(new Vector3().toDesired())));
			RLBotDll.setGameState(gameState.buildPacket());
			this.setTime = packet.time;
		}

		boolean idle = (this.getActiveStep() == null || this.getActiveStep().getPriority() == Priority.IDLE);

		if(packet.time - this.setTime < 0.075){
			if(idle)
				return null;
			this.clearSteps();
			AtbaStep atba = new AtbaStep(this.bundle);
			atba.dontBoost = true;
			atba.dodge = false;
			return atba;
		}

		if((doubleJump ? info.aerialDouble : info.aerialDodge) != null && idle){
			return new AerialStep(this.bundle, (doubleJump ? info.aerialDouble : info.aerialDodge),
					(doubleJump ? AerialType.DOUBLE_JUMP : AerialType.DODGE_STRIKE));
		}

		return new IdleStep(this.bundle);
	}

}
