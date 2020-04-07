package ibot.bot.step.steps;

import ibot.bot.input.Bundle;
import ibot.bot.step.Priority;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class KickoffStep extends DriveStep {

	public KickoffStep(Bundle bundle){
		super(bundle, new Vector2());
	}

	@Override
	public Output getOutput(){
		Output output = super.getOutput();
		this.setFinished(!this.bundle.packet.isKickoffPause, true);
		return output;
	}

	@Override
	public int getPriority(){
		return Priority.KICKOFF;
	}

}
