package ibot.bot.step.steps;

import java.awt.Color;

import rlbot.flat.QuickChatSelection;
import ibot.boost.BoostPad;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.Constants;
import ibot.bot.utils.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class GrabObliviousStep extends Step {

	private final DriveStep drive;
	private final BoostPad boost;

	public GrabObliviousStep(Bundle bundle, BoostPad boost){
		super(bundle);
		bundle.bot.sendQuickChat(QuickChatSelection.Information_NeedBoost, QuickChatSelection.Reactions_Okay);
		this.drive = new DriveStep(bundle);
		this.boost = boost;
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if(this.boost == null || !Info.isBoostValid(this.boost, car) || car.boost > 99){
			return new PopStack();
		}

		Vector3 target = this.boost.getLocation().withZ(Constants.CAR_HEIGHT);

		pencil.renderer.drawLine3d(Color.YELLOW, car.position, target);
//		pencil.renderer
//				.drawString3d(
//						MathsUtils.round(this.boost.getTimer(), 2) + "\n"
//								+ MathsUtils.round(this.boost.getTimeLeft(), 2) + "\n" + this.boost.isActive(),
//						Color.YELLOW, target, 2, 2);

		if(this.boost.isFullBoost() || info.arena.getMode() == Mode.HOOPS){
			if(this.boost.isFullBoost()){
				target = target.scale(1 - (35 / target.magnitude()));
				this.drive.dontBoost = false;
			}else{
				this.drive.dontBoost = true;
			}
			Vector2 offset = target.minus(car.position).flatten().rotate(Math.PI / 2);
			if(offset.dot(target.flatten()) < 0)
				offset = offset.scale(-1);
			target = target.plus(offset.scale(this.boost.isFullBoost() && !info.isKickoff ? 0.11 : 0.09));
		}

		// Controls.
		this.drive.target = target;
		this.drive.dodge = (car.boost < 1 || !this.boost.isFullBoost());
		Output output = this.drive.getOutput();
		if(this.boost.isFullBoost() && car.hasWheelContact && output instanceof Controls){
			Controls controls = (Controls)output;
			controls.withBoost(!car.isSupersonic && !controls.holdHandbrake());
			return controls;
		}
		return output;
	}

	@Override
	public int getPriority(){
		return Priority.GRAB_BOOST;
	}

}
