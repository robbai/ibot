package ibot.output;

import ibot.bot.actions.Action;

public abstract class Output {

	public abstract boolean isControls();

	public abstract boolean isAction();

	public ControlsOutput getOutput(){
		if(this.isAction())
			return ((Action)this).getOutput();
		return (ControlsOutput)this;
	}

}
