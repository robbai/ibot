package ibot.bot.bots;

import java.util.Random;

import ibot.bot.abort.BallTouchedAbort;
import ibot.bot.actions.Action;
import ibot.bot.actions.Aerial;
import ibot.bot.actions.arcs.CompositeArc;
import ibot.bot.actions.arcs.FollowArcs;
import ibot.bot.controls.Handling;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.vectors.Vector3;
import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.BallState;
import rlbot.gamestate.CarState;
import rlbot.gamestate.DesiredRotation;
import rlbot.gamestate.DesiredVector3;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;

@SuppressWarnings ("unused")
public class TestBot extends DataBot {

	private static final AerialType AERIAL_TYPE = AerialType.DODGE_STRIKE;

	private Action action;
	private double timeSet = 0;
	private float ballY, playerY;
	private Random random;

	public TestBot(int playerIndex){
		super(playerIndex);
		this.random = new Random();
	}

	@Override
	protected ControlsOutput processInput(DataPacket packet){
		if(this.action != null){
			if(!this.action.isFinished(packet)){
				this.stackRenderString("Action: " + this.action.getClass().getSimpleName(), this.altColour);
				return action.getOutput(packet);
			}else{
				this.action = null;
			}
		}

		if(packet.robbie != null){
//			CompositeArc compositeArc = CompositeArc.create(this.car, packet.robbie.position.flatten(), new Vector2(), 200, 0);

			CompositeArc compositeArc = CompositeArc.create(this.car, this.groundIntercept.position.flatten(), packet.robbie.position.flatten(), 200, 300);
			if(this.ballPosition.z < 120 && this.ballVelocity.z <= 0){
				GameState gameState = new GameState().withBallState(new BallState().withPhysics(new PhysicsState().withVelocity(new DesiredVector3(0F, 0F, 0F)).withAngularVelocity(new DesiredVector3(0F, 0F, 0F))));
				RLBotDll.setGameState(gameState.buildPacket());
			}

			this.action = new FollowArcs(this, compositeArc);
			return action.getOutput(packet);
		}

//		    	if((packet.ball.position.y - ballY) * this.sign > 2000 || (this.car.position.y - playerY) * this.sign > 5000 || (packet.secondsElapsed - timeSet) > 5){
//		if((packet.time - timeSet) > 10 || (this.car.position.y - packet.ball.position.y) * this.sign > 0 || this.ballVelocity.y * this.sign < -1000){
//			float x = (float)Math.signum(this.random(-1, 1));
//			ballY = (float)(random(2000, 3500) * this.sign);
//			playerY = (float)(-random(1000, 2000) * this.sign);
//			GameState gameState = new GameState()
//					.withCarState(this.playerIndex, new CarState().withBoostAmount((float)random(30, 100))
//							.withPhysics(new PhysicsState()
//									.withLocation(new DesiredVector3(0F, playerY, 0F))
//									.withVelocity(new DesiredVector3(0F, (float)(Constants.MAX_CAR_VELOCITY * 0.85 * this.sign), 0F))
//									.withRotation(new DesiredRotation(0F, (float)(Math.PI * (this.team == 0 ? 0.5 : 1.5)), 0F))))
//					.withBallState(new BallState()
//							.withPhysics(new PhysicsState()
//									.withLocation(new DesiredVector3(1500F * x, ballY, (float)Constants.BALL_RADIUS))
//									.withVelocity(new DesiredVector3(
//											(float)random(300, 450) * -x,
//											(float)(random(0, 800) * -this.sign),
//											(float)random(1100, 1300) / (AERIAL_TYPE == AerialType.DOUBLE_JUMP ? 1.2F : 1.4F)
//											)
//										)
//									));
//			if(packet.robbie != null){
//				gameState.withCarState(packet.robbie.index, new CarState().withBoostAmount(100F)
//						.withPhysics(new PhysicsState()
//								.withLocation(new DesiredVector3(0F, (float)(this.sign * Constants.PITCH_LENGTH_SOCCAR), 0F))
//								.withVelocity(new DesiredVector3(0F, 0F, 0F))
//								.withRotation(new DesiredRotation(0F, (float)(-Math.PI * (this.team == 0 ? 0.5 : 1.5)), 0F))));
//			}
//			RLBotDll.setGameState(gameState.buildPacket());
//			this.action = null;
//			this.timeSet = packet.time;
//		}

		if(this.action != null){
			if(!this.action.isFinished(packet)){
				this.stackRenderString("Action: " + this.action.getClass().getSimpleName(), this.altColour);
				return action.getOutput(packet);
			}else{
				this.action = null;
			}
		}

		Intercept aerialIntercept = (AERIAL_TYPE == AerialType.DODGE_STRIKE ? this.aerialDodge : this.aerialDouble);
		if(aerialIntercept != null && (packet.time - 0.5) > timeSet){
			this.action = new Aerial(this, aerialIntercept, AERIAL_TYPE).withAbortCondition(new BallTouchedAbort(this, packet.ball.latestTouch, this.playerIndex));
			return this.action.getOutput(packet);
		}else{
			this.action = null;
		}
		Vector3 target = this.groundIntercept.intersectPosition;
		if(this.bounce != null){
			target = target.lerp(this.bounce.intersectPosition, 0.5);
		}
		return (ControlsOutput)Handling.driveTime(this, target, false, false, this.time + 1.5);

//		if(this.groundIntercept == null) return new ControlsOutput();
//		if((packet.secondsElapsed - timeSet) < 0.05 || !this.car.hasWheelContact){
//			return (ControlsOutput)Handling.driveTime(this, this.bounce != null ? this.groundIntercept.position.lerp(this.bounce.position, 0.5) : this.groundIntercept.position, false, false, this.secondsElapsed + 0.75);
//		}
//		this.action = new DriveStrike(this, this.groundIntercept).withAbortCondition(new BallTouchedAbort(this, packet.ball.latestTouch, this.playerIndex));
//		return this.action.getOutput(packet);
	}

	private double random(double min, double max){
		return this.random.nextDouble() * (max - min) + min;
	}

	@SuppressWarnings ("unused")
	private double random(double min){
		return random(-min, min);
	}

}
