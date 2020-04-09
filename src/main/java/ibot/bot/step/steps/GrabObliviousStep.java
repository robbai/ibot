package ibot.bot.step.steps;

import java.util.OptionalDouble;

import rlbot.flat.QuickChatSelection;
import ibot.bot.abort.BoostYoinkedAbort;
import ibot.bot.controls.Handling;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.CompositeArc;
import ibot.bot.utils.Constants;
import ibot.bot.utils.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class GrabObliviousStep extends Step {

	public GrabObliviousStep(Bundle bundle){
		super(bundle);
		bundle.bot.sendQuickChat(QuickChatSelection.Information_NeedBoost, QuickChatSelection.Reactions_Okay);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
//		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if(info.nearestBoost == null){
			return new PopStack();
		}

//		boolean dontBoost = false;
		OptionalDouble targetTime = OptionalDouble.empty();
		boolean wall = !car.onFlatGround;

		Vector3 target = info.nearestBoost.getLocation().withZ(Constants.CAR_HEIGHT);

		if(info.nearestBoost.isFullBoost() || info.mode == Mode.HOOPS){
			if(info.isKickoff){
				Vector2 endTarget = target.flatten().withY(0);

				CompositeArc compositeArc = CompositeArc.create(car, target.flatten(), endTarget, 1000, 200, 300);

				return new FollowArcsStep(this.bundle, compositeArc)
						.withAbortCondition(new BoostYoinkedAbort(this.bundle, info.nearestBoost));
			}

			if(info.nearestBoost.isFullBoost()){
				target = target.scale(1 - (35 / target.magnitude()));
			}
//			else{
//				dontBoost = true;
//			}
			Vector2 offset = target.minus(car.position).flatten().rotate(Math.PI / 2);
			if(offset.dot(target.flatten()) < 0)
				offset = offset.scale(-1);
			target = target.plus(offset.scale(info.nearestBoost.isFullBoost() && !info.isKickoff ? 0.11 : 0.09));
		}

		Output output = Handling.driveTime(this.bundle, target, (!info.isKickoff || info.mode == Mode.SOCCAR && !wall),
				info.mode == Mode.DROPSHOT /* || dontBoost */, targetTime);
//		if(output instanceof Controls){
//			Controls controls = (Controls)output;
//			controls.withBoost(controls.holdBoost() && !dontBoost);
//			return controls;
//		}
		return output;
	}

	@Override
	public int getPriority(){
		return Priority.GRAB_BOOST;
	}

}
