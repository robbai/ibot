package ibot.bot.step.steps;

import rlbot.flat.QuickChatSelection;
import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.step.Priority;
import ibot.output.Output;

public class GrabBoostStep extends DriveStep {

	private boolean startedKickoff;
	private BoostPad boost;
	private boolean lastActive;

	public GrabBoostStep(Bundle bundle, BoostPad boost){
		super(bundle, boost.getPosition());
		this.startedKickoff = bundle.packet.isKickoffPause;
		this.boost = boost;
		this.lastActive = boost.isActive();
		this.routing = true;
		this.reverse = false;
		bundle.bot.sendQuickChat(QuickChatSelection.Information_NeedBoost);
	}

	public static GrabBoostStep findBoost(Bundle bundle){
		BoostPad boost = Info.findNearestBoost(bundle.packet.car.position.flatten(), BoostManager.getFullBoosts());
		if(boost != null){
			return new GrabBoostStep(bundle, boost);
		}
		return null;
	}

	@Override
	public Output getOutput(){
		Output output = super.getOutput();
		this.setFinished(!this.boost.isActive() && this.lastActive);
		this.lastActive = this.boost.isActive();
		return output;
	}

	@Override
	public int getPriority(){
		return this.startedKickoff ? Priority.KICKOFF : Priority.GRAB_BOOST;
	}

}
