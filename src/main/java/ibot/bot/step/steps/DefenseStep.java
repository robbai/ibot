package ibot.bot.step.steps;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.boost.BoostManager;
import ibot.bot.abort.CommitAbort;
import ibot.bot.controls.Handling;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.stack.PushStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.CompositeArc;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class DefenseStep extends Step {

	public DefenseStep(Bundle bundle){
		super(bundle);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if((car.boost < 40 || info.isKickoff) && info.mode != Mode.DROPSHOT && info.nearestBoost != null){
			return new PushStack(new GrabObliviousStep(this.bundle));
		}

		boolean dontBoost = !info.lastMan;
		Vector3 target;
		OptionalDouble targetTime = OptionalDouble.empty();
		boolean wall = !car.onFlatGround;

		final double MIN_DEPTH = 0.3;
		final double MAX_DEPTH = 0.8;
		// double depthLerp = MathsUtils.clamp((info.teamPossession * 0.6) + 0.35, 0,
		// 1);
		double depthLerp = car.boost / 100;
		pencil.stackRenderString("Depth: " + MathsUtils.round(depthLerp), pencil.colour);

		// target = info.homeGoal.lerp(info.earliestEnemyIntercept.position,
		// MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH,
		// depthLerp)).withZ(Constants.CAR_HEIGHT);

		double goalDistance = info.earliestEnemyIntercept.position.distance(info.homeGoal);
		Vector2 direction = info.earliestEnemyIntercept.getOffset().flatten().scaleToMagnitude(-1);
		target = info.earliestEnemyIntercept.position.plus(
				direction.scaleToMagnitude(goalDistance * (1 - MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH, depthLerp))));
		if(Math.abs(target.x) > Constants.PITCH_WIDTH_SOCCAR - 400){
			target = target.withX(Math.copySign(Constants.PITCH_WIDTH_SOCCAR - 400, target.x));
		}
		if(Math.abs(target.y) > Constants.PITCH_LENGTH_SOCCAR - 300){
			target = target.withY(Math.copySign(Constants.PITCH_LENGTH_SOCCAR - 400, target.y));
		}

		double nose = (car.orientation.forward.y * car.sign);

		if((!info.furthestBack || nose > 0.2) || info.teamPossession > 0.7){
			if(info.teamPossession * info.earliestEnemyIntercept.position.y * car.sign > 0 || info.furthestBack){
				target = target.withX(target.x * 0.6);
			}

//			if(car.boost < 45 && target.y * car.sign < -1000 && goalDistance > 3500){
//				target = Info.findNearestBoost(target.plus(car.velocity.scale(0.5)).flatten(),
//						BoostManager.getSmallBoosts()).getLocation().withZ(Constants.CAR_HEIGHT);
//			}

			if(car.boost < 70){
				Vector2 boostPosition = Info
						.findNearestBoost(target.plus(car.velocity.scale(0.5)).flatten(), BoostManager.getSmallBoosts())
						.getLocation();
				if(boostPosition.minus(car.position.flatten()).normalised()
						.dot(target.minus(car.position).flatten().normalised()) > 0.3){
					target = boostPosition.withZ(Constants.CAR_HEIGHT);
				}
			}
		}else{
			double distance = info.homeGoal.distance(car.position);
			final double closingDistance = 1000;

			nose = Math.max(0, nose);

			double x = MathsUtils.clamp((closingDistance - distance) / closingDistance, -3.5, 1);
			double y = (Constants.PITCH_LENGTH_SOCCAR - 300 - nose * 1200) / Math.abs(car.position.y);
			target = info.homeGoal.multiply(new Vector3(x, y, 1));
		}

		boolean arc = false;

		pencil.renderer.drawLine3d(Color.BLACK, car.position, target);
		pencil.renderer.drawLine3d(pencil.altColour, info.earliestEnemyIntercept.position, target);

		if(arc && info.carForwardComponent > 0.975){
			Vector2 endTarget = info.earliestEnemyIntercept.position.flatten();
			CompositeArc compositeArc = CompositeArc.create(car, target.flatten(), endTarget, 1300, 200, 300);
			return new FollowArcsStep(this.bundle, compositeArc).withBoost(dontBoost)
					.withAbortCondition(new CommitAbort(this.bundle, 0.1));
		}

		Output output = Handling.driveTime(this.bundle, target, (!info.isKickoff || info.mode == Mode.SOCCAR && !wall),
				info.mode == Mode.DROPSHOT || dontBoost, targetTime);
		if(output instanceof Controls){
			Controls controls = (Controls)output;
			controls.withBoost(controls.holdBoost() && !dontBoost);
			return controls;
		}
		return output;
	}

	@Override
	public int getPriority(){
		return Priority.DEFENSE;
	}

}
