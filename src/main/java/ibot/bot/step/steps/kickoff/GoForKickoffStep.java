package ibot.bot.step.steps.kickoff;

import ibot.bot.input.Bundle;
import ibot.bot.step.steps.DriveStep;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class GoForKickoffStep extends DriveStep {

	public GoForKickoffStep(Bundle bundle){
		super(bundle, new Vector2());
	}

	@Override
	public Output getOutput(){
		Output output = super.getOutput();
		this.setFinished(!this.bundle.packet.isKickoffPause, true);
		return output;
	}

}
