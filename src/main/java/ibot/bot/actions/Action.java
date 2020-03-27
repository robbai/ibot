package ibot.bot.actions;

import java.util.ArrayList;

import ibot.bot.abort.AbortCondition;
import ibot.bot.input.Bundle;
import ibot.output.ControlsOutput;
import ibot.output.Output;

public abstract class Action extends Output {

	protected Bundle bundle;
	private boolean finished;
	private double startTime;
	private ArrayList<AbortCondition> abortConditions;

	public Action(Bundle bundle){
		this.bundle = bundle;
		this.finished = false;
		this.startTime = bundle.packet.time;
		this.abortConditions = new ArrayList<AbortCondition>();
	}

	public abstract ControlsOutput getOutput();

	public boolean isFinished(){
		if(!this.finished){
			for(AbortCondition abort : this.abortConditions){
				if(abort.shouldAbort()){
					this.finished = true;
					return true;
				}
			}
		}
		return this.finished;
	}

	protected void setFinished(boolean finished){
		if(this.finished)
			return;
		this.finished = finished;
	}

	public double getStartTime(){
		return this.startTime;
	}

	@Override
	public boolean isControls(){
		return false;
	}

	@Override
	public boolean isAction(){
		return true;
	}

	public Action withAbortCondition(AbortCondition... abortConditions){
		for(AbortCondition abort : abortConditions){
			this.abortConditions.add(abort);
		}
		return this;
	}

}
