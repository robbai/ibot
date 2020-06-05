package ibot.bot.step.steps.kickoff;

import ibot.boost.BoostManager;
import ibot.bot.input.Bundle;
import ibot.bot.stack.PopStack;
import ibot.bot.stack.PushStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.step.steps.GrabBoostStep;
import ibot.bot.utils.rl.KickoffSpawn;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Output;
import ibot.vectors.Vector3;

public class KickoffStep extends Step {

	public KickoffStep(Bundle bundle){
		super(bundle);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		// Pencil pencil = this.bundle.pencil;
		// Info info = this.bundle.info;
		Car car = packet.car;

		if(!packet.isKickoffPause){
			return new PopStack();
		}

		KickoffSpawn spawn = getSpawn(car.position);
		if(spawn == KickoffSpawn.CORNER){
			boolean sameSpawn = this.hasTeammateSameSpawn(spawn);
			Car offcentre = this.getTeammateAtSpawn(KickoffSpawn.OFFCENTRE);
			if(!sameSpawn || this.hasTeammateSameSide() || (offcentre == null && car.position.x * car.position.y > 0)){
				return new PushStack(new GoForKickoffStep(this.bundle));
			}else{
				return new PushStack(GrabBoostStep.findBoost(this.bundle));
			}
		}else if(spawn == KickoffSpawn.OFFCENTRE){
			Car corner = this.getTeammateAtSpawn(KickoffSpawn.CORNER);
			if(corner != null){
				if(this.hasTeammateSameSide()){
					return new PushStack(GrabBoostStep.findBoost(this.bundle));
				}else{
					return new PushStack(new CheatKickoffStep(this.bundle));
				}
			}else{
				if(car.position.x * car.position.y < 0){
					return new PushStack(new CheatKickoffStep(this.bundle));
				}else{
					return new PushStack(new GoForKickoffStep(this.bundle));
				}
			}
		}else{
			Car offcentre = this.getTeammateAtSpawn(KickoffSpawn.OFFCENTRE);
			if(offcentre == null){
				return new PushStack(GrabBoostStep.findBoost(this.bundle));
			}else{
				int index;
				if(offcentre.position.x < 0){
					if(car.team == 0){
						index = 3;
					}else{
						index = 29;
					}
				}else{
					if(car.team == 0){
						index = 4;
					}else{
						index = 30;
					}
				}
				return new PushStack(new GrabBoostStep(this.bundle, BoostManager.getAllBoosts().get(index)));
			}
		}
	}

	@Override
	public int getPriority(){
		return Priority.KICKOFF;
	}

	private static boolean sameSide(Vector3 position1, Vector3 position2){
		return position1.x * position2.x > 0;
	}

	private boolean hasTeammateSameSide(){
		for(Car car : this.bundle.packet.teammates){
			if(Math.abs(car.position.x) < 50)
				continue;
			if(sameSide(car.position, this.bundle.packet.car.position)){
				return true;
			}
		}
		return false;
	}

	private boolean hasTeammateSameSpawn(KickoffSpawn spawn){
		for(Car car : this.bundle.packet.teammates){
			if(getSpawn(car.position) == spawn){
				return true;
			}
		}
		return false;
	}

	private static KickoffSpawn getSpawn(Vector3 position){
		double x = Math.abs(position.x);
		if(x > 1000){
			return KickoffSpawn.CORNER;
		}else if(x < 50){
			return KickoffSpawn.STRAIGHT;
		}
		return KickoffSpawn.OFFCENTRE;
	}

	private Car getTeammateAtSpawn(KickoffSpawn spawn){
		for(Car car : this.bundle.packet.teammates){
			if(getSpawn(car.position) == spawn){
				return car;
			}
		}
		return null;
	}

}
