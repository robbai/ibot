package ibot.bot.step.steps;

import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.input.DataPacket;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class SaveStep extends OffenseStep {

	public SaveStep(Bundle bundle){
		super(bundle);
		this.canPop = false; // Offense can't pop.
		this.addOffset = false;
	}

	@Override
	public Output getOutput(){
		if(!mustSave(this.bundle)){
			return new PopStack();
		}
		return super.getOutput();
	}

	@Override
	public int getPriority(){
		return Priority.SAVE;
	}

	public static boolean mustSave(Bundle bundle){
		Info info = bundle.info;
		DataPacket packet = bundle.packet;

		Vector2 displacement = info.homeGoal.minus(packet.ball.position).flatten();

		if(info.possession < info.teamPossessionCorrectSide && displacement.magnitude() < 1400){
			return false;
		}

		Vector2 initialVelocity = packet.ball.velocity.flatten();
		double time = 2;
		Vector2 delta = displacement.scale(1 / time).minus(initialVelocity);
		return delta.magnitude() * Math.signum(delta.dot(displacement)) < 1700;
	}

}
