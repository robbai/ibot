package ibot.bot.step.steps.kickoff;

import java.util.OptionalDouble;

import ibot.bot.input.Bundle;
import ibot.bot.step.steps.DriveStep;
import ibot.output.Output;

public class CheatKickoffStep extends DriveStep {

	public CheatKickoffStep(Bundle bundle){
		super(bundle, bundle.packet.car.position.withX(0).scale(0.2));
	}

	@Override
	public Output getOutput(){
		this.targetTime = OptionalDouble.of(this.bundle.packet.time + 1.3);
		Output output = super.getOutput();
		this.setFinished(!this.bundle.packet.isKickoffPause, true);
		return output;
	}

}
