package ibot.bot.abort;

import ibot.boost.BoostPad;
import ibot.bot.utils.DataBot;
import ibot.input.DataPacket;

public class BoostYoinkedAbort extends AbortCondition {

	private final BoostPad boostPad;

	public BoostYoinkedAbort(DataBot bot, BoostPad boostPad){
		super(bot);
		this.boostPad = boostPad;
	}

	@Override
	public boolean shouldAbort(DataPacket packet){
		return !this.boostPad.isActive();
	}

}
