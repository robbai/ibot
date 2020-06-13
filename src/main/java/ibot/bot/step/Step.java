package ibot.bot.step;

import java.util.ArrayList;

import ibot.bot.abort.AbortCondition;
import ibot.bot.input.Bundle;
import ibot.output.Controls;
import ibot.output.Output;

public abstract class Step extends Output {

	protected Bundle bundle;
	private boolean finished;
	private double startTime;
	private ArrayList<AbortCondition> abortConditions;

	public Step(Bundle bundle){
		this.bundle = bundle;
		this.finished = false;
		this.startTime = bundle.packet.time;
		this.abortConditions = new ArrayList<AbortCondition>();
	}

	public abstract Output getOutput();

	public abstract int getPriority();

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

	protected void setFinished(boolean finished, boolean override){
		if(this.finished && !override)
			return;
		this.finished = finished;
	}

	protected void setFinished(boolean finished){
		this.setFinished(finished, false);
	}

	public double getStartTime(){
		return this.startTime;
	}

	public Step withAbortCondition(AbortCondition... abortConditions){
		for(AbortCondition abort : abortConditions){
			this.abortConditions.add(abort);
		}
		return this;
	}

	public void manipulateControls(Controls controls){
	}

	@Override
	public String toString(){
		return this.getClass().getSimpleName();
	}

}
