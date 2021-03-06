package ibot.bot.step.steps;

import java.awt.Color;

import ibot.boost.BoostPad;
import ibot.bot.abort.CommitAbort;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.physics.Car1D;
import ibot.bot.stack.PushStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.step.steps.jump.JumpStep;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class DefenseStep extends Step {

	private final DriveStep drive;

	public DefenseStep(Bundle bundle){
		super(bundle);
		this.drive = new DriveStep(bundle);
		this.drive.reverse = false;
//		this.drive.routing = false;
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

//		this.drive.gentleSteer = !info.lastMan;
		this.drive.dontBoost = false;

		if(!car.onFlatGround && car.hasWheelContact && car.velocity.z < 0){
			return new JumpStep(this.bundle, Constants.MAX_JUMP_HOLD_TIME);
		}

		double goalDistance = packet.ball.position.distance(info.homeGoal);

		BoostPad nearestBoost = info.nearestBoost;
		if(nearestBoost != null && nearestBoost.isFullBoost() && this.bundle.bot.iteration < 5){
			double distance = nearestBoost.getPosition().distance(car.position.flatten());
			boolean boostCorrectSide = (packet.ball.position.y - nearestBoost.getPosition().y) * car.sign > 0;
			if(boostCorrectSide && car.boost < 60
					&& new Car1D(car)
							.stepDisplacement(1, true,
									distance + nearestBoost.getPosition().distance(info.homeGoal.flatten()))
							.getTime() < info.earliestEnemyIntercept.time + goalDistance / 4000){
				this.drive.dontBoost = nearestBoost.isFullBoost();
				return new PushStack(new GrabObliviousStep(this.bundle, nearestBoost)
						.withAbortCondition(new CommitAbort(this.bundle, 0)));
			}
		}

//		if(packet.ball.position.distance(info.homeGoal) > 4200 && info.teamPossessionCorrectSide > 0.85
//				&& (info.teamPossessionCorrectSide > info.possession || !car.correctSide(info.groundIntercept.position))
//				&& car.boost > (Constants.MAX_CAR_VELOCITY - car.forwardVelocity) / Constants.BOOST_GROUND_ACCELERATION
//						* Constants.BOOST_USAGE
//				&& info.getTimeOnGround() > 0.1 && !info.furthestBack && car.position.y * car.sign > -2000){
//			DemoStep demo = new DemoStep(this.bundle);
//			if(demo.isValid())
//				return demo;
//		}

		this.drive.dontBoost = true;

		Vector3 target;

		final double MIN_DEPTH = 0.35;
		final double MAX_DEPTH = 0.8;
		double depthLerp = car.boost / 100;
//		double depthLerp = MathsUtils.clamp((info.teamPossessionCorrectSide + 0.5) * 0.8, 0, 1);
		if(info.slowestTeammate)
			depthLerp = Math.max(depthLerp - 0.3, 0);
		pencil.stackRenderString("Depth: " + MathsUtils.round(depthLerp), pencil.colour);

		Vector2 direction;
		if(info.slowestTeammate){
			direction = info.homeGoal.minus(info.earliestEnemyIntercept.position).flatten().normalised();
		}else{
			direction = info.earliestEnemyIntercept.position.minus(info.earliestEnemyIntercept.car.position).flatten()
					.normalised();
		}

//		target = MathsUtils.traceToWall(info.earliestEnemyIntercept.position.flatten(), direction,
//				Math.copySign(info.arena.getWidth(), direction.x), Math.copySign(info.arena.getLength(), direction.y))
//				.lerp(info.earliestEnemyIntercept.position.flatten(), MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH, depthLerp))
//				.withZ(Constants.CAR_HEIGHT);

		target = info.earliestEnemyIntercept.position.plus(
				direction.scaleToMagnitude(goalDistance * (1 - MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH, depthLerp))));
		if(Math.abs(target.x) > Constants.PITCH_WIDTH_SOCCAR - 400){
			target = target.withX(Math.copySign(Constants.PITCH_WIDTH_SOCCAR - 400, target.x));
		}
		if(Math.abs(target.y) > Constants.PITCH_LENGTH_SOCCAR - 300){
			target = target.withY(Math.copySign(Constants.PITCH_LENGTH_SOCCAR - 400, target.y));
		}

		double nose = (car.orientation.forward.y * car.sign);
		if(packet.ball.position.distance(info.homeGoal) < (info.slowestTeammate ? 4400 : 3200) || info.lastMan){
			double goalCarDistance = info.homeGoal.distance(car.position);
			final double closingDistance = 1000;

			nose = Math.max(0, nose);

			double x = MathsUtils.clamp((closingDistance - goalCarDistance) / closingDistance, -3, 1) * info.homeGoal.x;
			double facing = Math.min(1, (2 * closingDistance) / goalCarDistance);
			target = new Vector3(x, (Constants.PITCH_LENGTH_SOCCAR - (Math.abs(x) < 800 ? 0 : 240)) * -car.sign,
					Constants.CAR_HEIGHT)
							.plus(packet.ball.position.minus(car.position).flatten().scaleToMagnitude(facing * 800));
		}else{
			if(info.teamPossession < 0.1 && info.earliestEnemyIntercept.position.y * car.sign < 0 || info.furthestBack){
				target = target.withX(target.x * 0.7);
			}else if(packet.ball.position.y * car.sign > 0){
				target = target.withX(target.x * 1.25);
			}

//			if(target.y * car.sign > -1000 && !info.slowestTeammate) target = target.withX(target.x * 1.2);

//			if(car.boost < 80){
//				BoostPad boost = Info.findNearestBoost(target.plus(car.velocity.scale(0.5)).flatten(),
//						BoostManager.getAllBoosts());
//				Vector2 carPositionFlat = car.position.flatten();
//				if(boost.getPosition().distance(carPositionFlat) < 1400
//						&& (packet.ball.position.y - boost.getPosition().y) * car.sign > 2500){
//					if(boost.getPosition().minus(carPositionFlat).dot(target.minus(carPositionFlat).flatten()) > 0
//							&& boost.getPosition().distance(carPositionFlat) < target.flatten()
//									.distance(carPositionFlat)){
//						return new PushStack(new GrabBoostStep(this.bundle, boost));
//					}
//				}
//			}

			target = target.clamp();

//			double targetDistance = target.distance(car.position);
//			target = target.plus(target.minus(packet.ball.position).flatten()
//					.scaleToMagnitude(car.sign * Math.min(1000, targetDistance * 0.5)));

			double dirX = (packet.ball.position.y * car.sign < 2500 ? info.earliestEnemyIntercept.getOffset().x
					: info.earliestEnemyIntercept.car.position.minus(info.earliestEnemyIntercept.position).x);
			target = target.withX(Math.copySign(target.x,
					(target.distance(packet.ball.position) > 4500 && !info.lastMan ? 1 : -1) * dirX));
		}

		final double separate = 1600;
		if(!car.correctSide(packet.ball.position) && !info.furthestBack
				&& Math.abs(target.x - packet.ball.position.x) < separate)
			target = target.plus(new Vector2(Math.copySign(separate, target.x - packet.ball.position.x), 0));

		// TODO
		final double heavy = 0.6;
		target = target
				.withY(Math.min(packet.ball.position.y * car.sign * heavy + Constants.PITCH_LENGTH_SOCCAR * (heavy - 1),
						target.y * car.sign) * car.sign);

//		this.drive.routing = goalDistance > 2500;
//		Vector2 direction;
//		if(info.slowestTeammate){
//			Vector2 homeGoal = new Vector2(0, info.arena.getLength() * -car.sign);
//			direction = packet.ball.position.flatten().minus(homeGoal).normalised();
//			target = homeGoal.lerp(packet.ball.position.flatten(), 0.2).withZ(Constants.CAR_HEIGHT);
//		}else{
//			direction = info.earliestEnemyIntercept.car.position.minus(info.earliestEnemyIntercept.position).flatten()
//					.normalised();
//			Vector2 trace = MathsUtils.traceToWall(info.earliestEnemyIntercept.position.flatten(), direction.scale(-1),
//					info.arena.getWidth(), info.arena.getLength());
//			target = trace.lerp(info.earliestEnemyIntercept.position.flatten(), 0.2).withZ(Constants.CAR_HEIGHT);
//		}
//		target = target.minus(direction.scale(target.distance(car.position) * 0.3));

		pencil.renderer.drawLine3d(Color.BLACK, car.position, target);
		pencil.renderer.drawLine3d(pencil.altColour, info.earliestEnemyIntercept.position, target);

		// Drive.
//		this.drive.withTargetTime(car.time + 0.2);
		this.drive.target = target;
		this.drive.dodge = (Math.abs(target.y) < info.arena.getLength()
				&& (!info.furthestBack || info.teamPossessionCorrectSide > 0.3));
		return this.drive.getOutput();
	}

	@Override
	public int getPriority(){
		return Priority.DEFENSE;
	}

	public void manipulateControls(Controls controls){
		controls.withBoost(controls.holdBoost() && !this.drive.dontBoost);
	}

}
