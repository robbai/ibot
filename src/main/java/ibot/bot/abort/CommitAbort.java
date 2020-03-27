package ibot.bot.abort;

import ibot.bot.input.Bundle;

public class CommitAbort extends AbortCondition {

	private final double minTime;

	public CommitAbort(Bundle bundle, double minTime){
		super(bundle);
		this.minTime = minTime;
	}

	@Override
	public boolean shouldAbort(){
		if(this.bundle.packet.time - this.getStartTime() < this.minTime){
			return false;
		}
		return this.bundle.info.commit;
	}

}
