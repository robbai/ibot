package ibot.bot.bots;

import java.util.Random;

import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.BallState;
import rlbot.gamestate.DesiredVector3;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import ibot.bot.actions.arcs.CompositeArc;
import ibot.bot.actions.arcs.FollowArcs;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.intercept.AerialType;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;

@SuppressWarnings ("unused")
public class TestBot extends ABot {

	private static final AerialType AERIAL_TYPE = AerialType.DODGE_STRIKE;

	private double timeSet = 0;
	private float ballY, playerY;
	private Random random;

	public TestBot(int index, int team){
		super(index, team);
		this.random = new Random();
	}

	@Override
	protected ControlsOutput processInput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if(this.action != null){
			if(!this.action.isFinished()){
				pencil.stackRenderString("Action: " + this.action.getClass().getSimpleName(), pencil.altColour);
				return action.getOutput();
			}
			this.action = null;
		}

		// if(packet.robbie != null){
		// CompositeArc compositeArc = CompositeArc.create(this.car,
		// packet.robbie.position.flatten(), new Vector2(), 200, 0);

		CompositeArc compositeArc = CompositeArc.create(car, info.groundIntercept.position.flatten(),
				info.enemyGoal.flatten(), 200, 300);
		if(packet.ball.position.z < 120 && packet.ball.velocity.z <= 0){
			GameState gameState = new GameState().withBallState(new BallState().withPhysics(new PhysicsState()
					.withVelocity(new DesiredVector3(0F, 0F, 0F)).withAngularVelocity(new DesiredVector3(0F, 0F, 0F))));
			RLBotDll.setGameState(gameState.buildPacket());
		}
		this.action = new FollowArcs(this.bundle, compositeArc);
		return action.getOutput();
		// }

		// if((packet.ball.position.y - ballY) * this.sign > 2000 || (car.position.y -
		// playerY) * this.sign > 5000
		// || (packet.time - timeSet) > 5){
		// // if((packet.time - timeSet) > 10 || (info.car.position.y -
		// // packet.ball.position.y) * this.sign > 0 || info.ballVelocity.y * this.sign
		// <
		// // -1000){
		// float x = (float)Math.signum(MathsUtils.random(-1, 1));
		// ballY = (float)(random(2000, 3500) * this.sign);
		// playerY = (float)(-random(4000, Constants.PITCH_LENGTH_SOCCAR - 200) *
		// this.sign);
		// GameState gameState = new GameState()
		// .withCarState(this.index,
		// new CarState().withBoostAmount((float)random(30, 100))
		// .withPhysics(new PhysicsState().withLocation(new DesiredVector3(0F, playerY,
		// 0F))
		// .withVelocity(new DesiredVector3(0F,
		// (float)(Constants.MAX_CAR_VELOCITY * 0.85 * this.sign), 0F))
		// .withRotation(new DesiredRotation(0F,
		// (float)(Math.PI * (this.team == 0 ? 0.5 : 1.5)), 0F))))
		// .withBallState(new BallState().withPhysics(new PhysicsState()
		// .withLocation(new DesiredVector3(1500F * x, ballY,
		// (float)Constants.BALL_RADIUS))
		// .withVelocity(new DesiredVector3((float)random(300, 450) * -x,
		// (float)(random(0, 800) * -this.sign), (float)random(1100, 1300)
		// / (AERIAL_TYPE == AerialType.DOUBLE_JUMP ? 1.2F : 0.8F)))));
		// if(packet.robbie != null){
		// gameState
		// .withCarState(packet.robbie.index,
		// new CarState()
		// .withBoostAmount(
		// 100F)
		// .withPhysics(new PhysicsState()
		// .withLocation(new DesiredVector3(0F,
		// (float)(this.sign * Constants.PITCH_LENGTH_SOCCAR), 0F))
		// .withVelocity(new DesiredVector3(0F, 0F, 0F))
		// .withRotation(new DesiredRotation(0F,
		// (float)(-Math.PI * (this.team == 0 ? 0.5 : 1.5)), 0F))));
		// }
		// RLBotDll.setGameState(gameState.buildPacket());
		// this.action = null;
		// this.timeSet = packet.time;
		// }
		//
		// if(this.action != null){
		// if(!this.action.isFinished()){
		// pencil.stackRenderString("Action: " + this.action.getClass().getSimpleName(),
		// pencil.altColour);
		// return action.getOutput();
		// }else{
		// this.action = null;
		// }
		// }
		//
		// // Intercept aerialIntercept = (AERIAL_TYPE == AerialType.DODGE_STRIKE ?
		// // info.aerialDodge : info.aerialDouble);
		// // if(aerialIntercept != null && (packet.time - 0.5) > timeSet){
		// // this.action = new Aerial(this, aerialIntercept, AERIAL_TYPE)
		// // .withAbortCondition(new BallTouchedAbort(this, packet.ball.latestTouch,
		// // this.index));
		// // return this.action.getOutput(packet);
		// // }else{
		// // this.action = null;
		// // }
		// // Vector3 target = info.groundIntercept.intersectPosition;
		// // if(info.bounce != null){
		// // target = target.lerp(info.bounce.intersectPosition, 0.5);
		// // }
		// // return (ControlsOutput)Handling.driveTime(this, target, false, false,
		// // info.time + 1.5);
		//
		// if(info.doubleJumpIntercept == null)
		// return new ControlsOutput();
		// if(Math.min(packet.time - timeSet, info.getTimeOnGround()) < 0.2 ||
		// !car.hasWheelContact){
		// return (ControlsOutput)Handling.driveTime(this.bundle,
		// info.bounce != null ?
		// info.doubleJumpIntercept.position.lerp(info.bounce.position, 0.5)
		// : info.doubleJumpIntercept.position,
		// false, false, info.time + 0.75);
		// }
		// this.action = new DriveStrike(this.bundle, info.doubleJumpIntercept,
		// info.enemyGoal, true)
		// .withAbortCondition(new BallTouchedAbort(this.bundle,
		// packet.ball.latestTouch, this.index));
		// return this.action.getOutput();
	}

	private double random(double min, double max){
		return this.random.nextDouble() * (max - min) + min;
	}

	@SuppressWarnings ("unused")
	private double random(double min){
		return random(-min, min);
	}

}
