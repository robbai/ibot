package ibot.bot.controls;

import ibot.bot.utils.MathsUtils;

public class Marvin {

	private static double THROTTLE_ACCEL = 1600, BREAK_ACCEL = 3500, THROTTLE_MAX_SPEED = 1400, DT = 1 / 60;

	/**
	 * Time until throttle full stop.
	 * @param throttleSpeed
	 * @return
	 */
	public static double tfs(double throttleSpeed){
		return Math.abs(throttleSpeed) / BREAK_ACCEL + DT;
	}

	/**
	 * Time until pitch full stop.
	 * @param pitchSpeed
	 * @return
	 */
	public static double pfs(double pitchSpeed){
		return Math.abs(pitchSpeed) / 66 + DT;
	}

	/**
	 * Time until yaw full stop.
	 * @param yawSpeed
	 * @return
	 */
	public static double yfs(double yawSpeed){
		return Math.abs(yawSpeed) / 50 + DT;
	}

	/**
	 * Time until roll full stop.
	 * @param rollSpeed
	 * @return
	 */
	public static double rfs(double rollSpeed){
		return Math.abs(rollSpeed) / 99 + DT;
	}

	public static double curve1(double x){
//		if(x > 0.5){
//			x = 1 - MathsUtils.clamp(x, -1, 1);
//		}else if(x < -0.5){
//			x = -1 - MathsUtils.clamp(x, -1, 1);
//		}
		double s = 500000 * Math.pow(x, 3);
		return MathsUtils.clamp(s, -1, 1);
	}

	/**
	 * PD steer to point.
	 * @param ang
	 * @param angVel
	 * @return
	 */
	public static double steerPoint(double ang, double angVel){
		return curve1(range180(ang - angVel * DT));
	}

	/**
	 * PD throttle to point
	 * @param loc
	 * @param vel
	 * @param brakes
	 * @return
	 */
	public static double throttlePoint(double loc, double vel, double brakes){
		return Math.signum(loc - brakes * tfs(vel) * vel) * MathsUtils.clamp((Math.abs(loc) + Math.abs(vel)) * DT, -1, 1);
	}

	/**
	 * PD throttle to point
	 * @param loc
	 * @param vel
	 * @return
	 */
	public static double throttlePoint(double loc, double vel){
		return throttlePoint(loc, vel, 1);
	}

	/**
	 * PD throttle to velocity.
	 * @param vel
	 * @param deltaSpeed
	 * @param lastThrottle
	 * @return
	 */
	public static double throttleVelocity(double vel, double deltaSpeed, double lastThrottle){
		vel = vel + throttleAcc(lastThrottle, vel) * DT;
		double deltaAcc = (deltaSpeed - vel) / DT * Math.signum(deltaSpeed);
		if(deltaAcc > 0){
			return MathsUtils.clamp(deltaAcc / (throttleAcc(1, vel) + 1e-9), -1, 1) * Math.signum(deltaSpeed);
		}else if(-3600 < deltaAcc && deltaAcc <= 0){
			return 0;
		}else{
			return -1;
		}
	}

	public static double throttleAcc(double throttle, double vel){
		if(throttle * vel < 0){
			return -3600 * Math.signum(vel);
		}else if(throttle == 0){
			return -525 * Math.signum(vel);
		}else{
			return (-THROTTLE_ACCEL / THROTTLE_MAX_SPEED * Math.min(Math.abs(vel), THROTTLE_MAX_SPEED) + THROTTLE_ACCEL) * throttle;
		}
	}

	//	/**
	//	 * PD yaw to point.
	//	 * @param ang
	//	 * @param angVel
	//	 * @return
	//	 */
	//	public static double yawPoint(double ang, double angVel){
	//		return Math.signum(range180(ang - angVel * yfs(angVel), 1)) * MathsUtils.clamp(Math.abs(ang) * 5 + Math.abs(angVel), -1, 1);
	//	}
	//
	//	/**
	//	 * PD pitch to point.
	//	 * @param ang
	//	 * @param angVel
	//	 * @return
	//	 */
	//	public static double pitchPoint(double ang, double angVel){
	//		return Math.signum(range180(-ang - angVel * pfs(angVel), 1)) * MathsUtils.clamp(Math.abs(ang) * 5 + Math.abs(angVel), -1, 1);
	//	}
	//
	//	/**
	//	 * PD roll to point.
	//	 * @param ang
	//	 * @param angVel
	//	 * @return
	//	 */
	//	public static double rollPoint(double ang, double angVel){
	//		return Math.signum(range180(-ang + angVel * rfs(angVel), 1)) * MathsUtils.clamp(Math.abs(ang) * 4 + Math.abs(angVel), -1, 1);
	//	}

	/**
	 * P velocity boost control.
	 * @param vel
	 * @param deltaVel
	 * @param lastBoost
	 * @return
	 */
	public static boolean boostVelocity(double vel, double deltaVel, boolean lastBoost){
		double relVel = deltaVel - vel - (lastBoost ? 5 : 0);
		double threshold;
		if(vel < THROTTLE_MAX_SPEED){
			if(deltaVel < 0){
				threshold = 800;
			}else{
				threshold = 250;
			}
		}else{
			threshold = 50;
		}
		return relVel > threshold;
	}

	/**
	 * P velocity boost control.
	 * @param vel
	 * @param deltaVel
	 * @return
	 */
	public static boolean boostVelocity(double vel, double deltaVel){
		return boostVelocity(vel, deltaVel, false);
	}

	/**
	 * Limits any angle a to [-pi, pi] range, example: Range180(270, 180) = -90.
	 * @param a
	 * @param pi
	 * @return
	 */
	public static double range180(double a, double pi){
		if(Math.abs(a) >= 2 * pi){
			a -= Math.floor(Math.abs(a) / (2 * pi) * 2 * pi * Math.signum(a));
		}
		if(Math.abs(a) > pi){
			a -= 2 * pi * Math.signum(a);
		}
		return a;
	}

	/**
	 * Limits any angle a to [-Math.PI, Math.PI] range, example: Range180(270, 180) = -90.
	 * @param a
	 * @return
	 */
	public static double range180(double a){
		return range180(a, Math.PI);
	}

	/**
	 * Limits any angle to [0, 2 * pi] range.
	 * @param a
	 * @param pi
	 * @return
	 */
	public static double range360(double a, double pi){
		return a - Math.floor((a / (2 * pi)) * 2 * pi);
	}

	/**
	 * Limits any angle to [0, 2 * Math.PI] range.
	 * @param a
	 * @return
	 */
	public static double range360(double a){
		return range360(a, Math.PI);
	}

}
