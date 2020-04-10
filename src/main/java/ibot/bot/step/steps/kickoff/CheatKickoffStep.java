package ibot.bot.step.steps.kickoff;

import ibot.bot.input.Bundle;
import ibot.bot.step.steps.DriveStep;
import ibot.output.Output;

public class CheatKickoffStep extends DriveStep {

	public CheatKickoffStep(Bundle bundle){
		super(bundle, bundle.packet.car.position.withX(0).scale(0.2));
		this.dontBoost = true;
	}

	@Override
	public Output getOutput(){
		this.withTargetVelocity(this.bundle.packet.time + 1.1);
		Output output = super.getOutput();
		this.setFinished(!this.bundle.packet.isKickoffPause, true);
		return output;
	}

}
