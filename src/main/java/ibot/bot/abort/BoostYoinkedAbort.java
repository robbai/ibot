package ibot.bot.abort;

import ibot.boost.BoostPad;
import ibot.bot.input.Bundle;

public class BoostYoinkedAbort extends AbortCondition {

	private final BoostPad boostPad;

	public BoostYoinkedAbort(Bundle bundle, BoostPad boostPad){
		super(bundle);
		this.boostPad = boostPad;
	}

	@Override
	public boolean shouldAbort(){
		return !this.boostPad.isActive();
	}

}
