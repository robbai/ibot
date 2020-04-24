package ibot.bot.step.steps;

import java.awt.Color;

import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.abort.CommitAbort;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.stack.PushStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.CompositeArc;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
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
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		this.drive.gentleSteer = !info.lastMan;

		if(!car.onFlatGround && car.hasWheelContact){
			return new JumpStep(this.bundle, Constants.JUMP_MAX_HOLD);
		}

		this.drive.dontBoost = true;

		double goalDistance = info.earliestEnemyIntercept.position.distance(info.homeGoal);

		BoostPad nearestBoost = info.nearestBoost;
		if(nearestBoost != null && nearestBoost.isFullBoost() && this.bundle.bot.iteration < 5){
			double distance = nearestBoost.getLocation().distance(car.position.flatten());
			boolean boostCorrectSide = (info.groundIntercept.position.y - nearestBoost.getLocation().y) * car.sign > 0;
			if(boostCorrectSide && car.boost < 40 && goalDistance > 4500
					&& DrivePhysics.minTravelTime(car, distance) < info.earliestEnemyIntercept.time - packet.time){
				this.drive.dontBoost = false;
				return new PushStack(new GrabObliviousStep(this.bundle, nearestBoost)
//						.withAbortCondition(new CommitAbort(this.bundle, 0))
				);
			}
		}

		// this.drive.dontBoost = !info.lastMan;

		Vector3 target;

		final double MIN_DEPTH = 0.15;
		final double MAX_DEPTH = 0.55;
		double depthLerp = car.boost / 100;
		if(info.furthestBack && info.teamPossession > 0)
			depthLerp = Math.max(depthLerp - 0.1, 0);
		pencil.stackRenderString("Depth: " + MathsUtils.round(depthLerp), pencil.colour);

		Vector2 direction;
		if(info.furthestBack){
			direction = info.earliestEnemyIntercept.getOffset().flatten().scaleToMagnitude(-1);
		}else{
			direction = info.homeGoal.minus(info.earliestEnemyIntercept.position).flatten().normalised();
		}

		target = info.earliestEnemyIntercept.position.plus(
				direction.scaleToMagnitude(goalDistance * (1 - MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH, depthLerp))));
		if(Math.abs(target.x) > Constants.PITCH_WIDTH_SOCCAR - 400){
			target = target.withX(Math.copySign(Constants.PITCH_WIDTH_SOCCAR - 400, target.x));
		}
		if(Math.abs(target.y) > Constants.PITCH_LENGTH_SOCCAR - 300){
			target = target.withY(Math.copySign(Constants.PITCH_LENGTH_SOCCAR - 400, target.y));
		}

		double nose = (car.orientation.forward.y * car.sign);
		if(packet.ball.position.distance(info.homeGoal) < (info.furthestBack ? 4000 : 3000)){
			double distance = info.homeGoal.distance(car.position);
			final double closingDistance = 1000;

			nose = Math.max(0, nose);

			double x = MathsUtils.clamp((closingDistance - distance) / closingDistance, -3.5, 1);
			double y = (Constants.PITCH_LENGTH_SOCCAR - 400 - nose * 1000) / Math.abs(car.position.y);
			target = info.homeGoal.multiply(new Vector3(x, y, 1));
		}else{
			if(info.teamPossession * info.earliestEnemyIntercept.position.y * car.sign > 0 || info.furthestBack){
				target = target.withX(target.x * 0.6);
			}

//			if(info.furthestBack){
//				target = target.withX(target.x * 0.6);
//			}

			if(car.boost < 60 && (target.y * car.sign < 0 || !car.correctSide(target))){
				BoostPad boost = Info.findNearestBoost(target.plus(car.velocity.scale(0.5)).flatten(),
						BoostManager.getAllBoosts());
				Vector2 boostPosition = boost.getLocation();

				if(boostPosition.distance(car.position.flatten()) < 2000){
					double angle1 = boostPosition.minus(car.position.flatten())
							.angle(target.minus(car.position).flatten());
					double angle2 = boostPosition.minus(target.flatten()).angle(car.position.minus(target).flatten());
					if(Math.max(angle1, angle2) < Math.toRadians(35)){
						// target = boostPosition.withZ(Constants.CAR_HEIGHT);
						return new PushStack(new GrabBoostStep(this.bundle, boost));
					}
				}
			}

			target = target.clamp();
		}

		boolean arc = false;

		pencil.renderer.drawLine3d(Color.BLACK, car.position, target);
		pencil.renderer.drawLine3d(pencil.altColour, info.earliestEnemyIntercept.position, target);

		if(arc && info.carForwardComponent > 0.975){
			Vector2 endTarget = info.earliestEnemyIntercept.position.flatten();
			CompositeArc compositeArc = CompositeArc.create(car, target.flatten(), endTarget, 1300, 200, 300);
			return new FollowArcsStep(this.bundle, compositeArc).withBoost(!this.drive.dontBoost)
					.withAbortCondition(new CommitAbort(this.bundle, 0.1));
		}

		// Drive.
		this.drive.target = target;
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
