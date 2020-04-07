package ibot.bot.step.steps;

import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class GrabBoostStep extends DriveStep {

	private GrabBoostStep(Bundle bundle, Vector2 boostLocation){
		super(bundle, boostLocation);
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

}
