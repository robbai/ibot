package ibot.bot.controls;

import ibot.bot.utils.MathsUtils;

public class Marvin {

	private static final double THROTTLE_ACCEL = 1600, BREAK_ACCEL = 3500, THROTTLE_MAX_SPEED = 1400, DT = 1 / 60;

	/**
	 * Time until throttle full stop.
	 *
	 * @param throttleSpeed
	 * @return
	 */
	public static double tfs(double throttleSpeed){
		return Math.abs(throttleSpeed) / BREAK_ACCEL + DT;
	}

	/**
	 * Time until pitch full stop.
	 *
	 * @param pitchSpeed
	 * @return
	 */
	public static double pfs(double pitchSpeed){
		return Math.abs(pitchSpeed) / 66 + DT;
	}

	/**
	 * Time until yaw full stop.
	 *
	 * @param yawSpeed
	 * @return
	 */
	public static double yfs(double yawSpeed){
		return Math.abs(yawSpeed) / 50 + DT;
	}

	/**
	 * Time until roll full stop.
	 *
	 * @param rollSpeed
	 * @return
	 */
	public static double rfs(double rollSpeed){
		return Math.abs(rollSpeed) / 99 + DT;
	}

	public static double curve1(double x){
		if(Math.abs(x) > 0.5){
			x = Math.copySign(1 - MathsUtils.clamp(x, -1, 1), x);
		}
		double s = 500000 * Math.pow(x, 3);
		return MathsUtils.clamp(s, -1, 1);
	}

	/**
	 * PD steer to point.
	 *
	 * @param angle
	 * @param angularVelocity
	 * @return
	 */
	public static double steerPoint(double angle, double angularVelocity){
		return curve1(range180(angle - angularVelocity * DT));
	}

	/**
	 * PD throttle to point
	 *
	 * @param location
	 * @param velocity
	 * @param brakes
	 * @return
	 */
	public static double throttlePoint(double location, double velocity, double brakes){
		return Math.signum(location - brakes * tfs(velocity) * velocity)
				* MathsUtils.clamp((Math.abs(location) + Math.abs(velocity)) * DT, -1, 1);
	}

	/**
	 * PD throttle to point
	 *
	 * @param location
	 * @param velocity
	 * @return
	 */
	public static double throttlePoint(double location, double velocity){
		return throttlePoint(location, velocity, 1);
	}

	/**
	 * PD throttle to velocity.
	 *
	 * @param velocity
	 * @param desiredVelocity
	 * @param lastThrottle
	 * @return
	 */
	public static double throttleVelocity(double velocity, double desiredVelocity, double lastThrottle){
		velocity = velocity + throttleAcc(lastThrottle, velocity) * DT;
		double deltaAcc = (desiredVelocity - velocity) / DT * Math.signum(desiredVelocity);
		if(deltaAcc > 0){
			return MathsUtils.clamp(deltaAcc / (throttleAcc(1, velocity) + 1e-9), -1, 1) * Math.signum(desiredVelocity);
		}else if(-3600 < deltaAcc && deltaAcc <= 0){
			return 0;
		}else{
			return -1;
		}
	}

	public static double throttleAcc(double throttle, double velocity){
		if(throttle * velocity < 0){
			return -3600 * Math.signum(velocity);
		}else if(throttle == 0){
			return -525 * Math.signum(velocity);
		}else{
			return (-THROTTLE_ACCEL / THROTTLE_MAX_SPEED * Math.min(Math.abs(velocity), THROTTLE_MAX_SPEED)
					+ THROTTLE_ACCEL) * throttle;
		}
	}

	// /**
	// * PD yaw to point.
	// * @param ang
	// * @param angVel
	// * @return
	// */
	// public static double yawPoint(double ang, double angVel){
	// return Math.signum(range180(ang - angVel * yfs(angVel), 1)) *
	// MathsUtils.clamp(Math.abs(ang) * 5 + Math.abs(angVel), -1, 1);
	// }
	//
	// /**
	// * PD pitch to point.
	// * @param ang
	// * @param angVel
	// * @return
	// */
	// public static double pitchPoint(double ang, double angVel){
	// return Math.signum(range180(-ang - angVel * pfs(angVel), 1)) *
	// MathsUtils.clamp(Math.abs(ang) * 5 + Math.abs(angVel), -1, 1);
	// }
	//
	// /**
	// * PD roll to point.
	// * @param ang
	// * @param angVel
	// * @return
	// */
	// public static double rollPoint(double ang, double angVel){
	// return Math.signum(range180(-ang + angVel * rfs(angVel), 1)) *
	// MathsUtils.clamp(Math.abs(ang) * 4 + Math.abs(angVel), -1, 1);
	// }

	/**
	 * P velocity boost control.
	 *
	 * @param velocity
	 * @param desiredVelocity
	 * @param lastBoost
	 * @return
	 */
	public static boolean boostVelocity(double velocity, double desiredVelocity, boolean lastBoost){
		double relativeVelocity = desiredVelocity - velocity - (lastBoost ? 5 : 0);
		double threshold;
		if(velocity < THROTTLE_MAX_SPEED){
			if(desiredVelocity < 0){
				threshold = 800;
			}else{
				threshold = 250;
			}
		}else{
			threshold = 50;
		}
		return relativeVelocity > threshold;
	}

	/**
	 * P velocity boost control.
	 *
	 * @param velocity
	 * @param desiredVelocity
	 * @return
	 */
	public static boolean boostVelocity(double velocity, double desiredVelocity){
		return boostVelocity(velocity, desiredVelocity, false);
	}

	/**
	 * Limits any angle a to [-pi, pi] range, example: Range180(270, 180) = -90.
	 *
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
	 * Limits any angle a to [-Math.PI, Math.PI] range, example: Range180(270, 180)
	 * = -90.
	 *
	 * @param a
	 * @return
	 */
	public static double range180(double a){
		return range180(a, Math.PI);
	}

	/**
	 * Limits any angle to [0, 2 * pi] range.
	 *
	 * @param a
	 * @param pi
	 * @return
	 */
	public static double range360(double a, double pi){
		return a - Math.floor((a / (2 * pi)) * 2 * pi);
	}

	/**
	 * Limits any angle to [0, 2 * Math.PI] range.
	 *
	 * @param a
	 * @return
	 */
	public static double range360(double a){
		return range360(a, Math.PI);
	}

}
