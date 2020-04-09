package ibot.bot.step.steps;

import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.step.Priority;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class GrabBoostStep extends DriveStep {

	private boolean startedKickoff;

	public GrabBoostStep(Bundle bundle, Vector2 boostLocation){
		super(bundle, boostLocation);
		this.startedKickoff = bundle.packet.isKickoffPause;
	}

	public static GrabBoostStep findBoost(Bundle bundle){
		BoostPad boost = Info.findNearestBoost(bundle.packet.car.position.flatten(), BoostManager.getFullBoosts());
		if(boost != null){
			return new GrabBoostStep(bundle, boost.getLocation());
		}
		return null;
	}

	@Override
	public Output getOutput(){
		Output output = super.getOutput();
		this.setFinished(this.bundle.packet.car.boost > 99);
		return output;
	}

	@Override
	public int getPriority(){
		return this.startedKickoff ? Priority.KICKOFF : Priority.DRIVE;
	}

}
