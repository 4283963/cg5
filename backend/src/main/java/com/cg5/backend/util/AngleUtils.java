package com.cg5.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngleUtils {

    private static final Logger log = LoggerFactory.getLogger(AngleUtils.class);

    private static final double MIN_ANGLE = -180.0;
    private static final double MAX_ANGLE = 180.0;
    private static final double FULL_CIRCLE = 360.0;
    private static final double HALF_CIRCLE = 180.0;

    public static final int CLOCKWISE = 1;
    public static final int COUNTER_CLOCKWISE = -1;

    private AngleUtils() {
    }

    public static double normalizeTo0To360(double angle) {
        double normalized = angle % FULL_CIRCLE;
        if (normalized < 0) {
            normalized += FULL_CIRCLE;
        }
        return normalized;
    }

    public static double normalizeToMinus180To180(double angle) {
        double normalized = normalizeTo0To360(angle);
        if (normalized > HALF_CIRCLE) {
            normalized -= FULL_CIRCLE;
        }
        return normalized;
    }

    public static double calculateShortestDiff(double currentAngle, double targetAngle) {
        double current0To360 = normalizeTo0To360(currentAngle);
        double target0To360 = normalizeTo0To360(targetAngle);

        double diff = target0To360 - current0To360;

        if (diff > HALF_CIRCLE) {
            diff -= FULL_CIRCLE;
        } else if (diff < -HALF_CIRCLE) {
            diff += FULL_CIRCLE;
        }

        log.debug("角度差值计算: 当前={}°, 目标={}°, 最短差值={}°",
                current0To360, target0To360, diff);

        return diff;
    }

    public static int getRotationDirection(double shortestDiff) {
        if (shortestDiff > 0) {
            return CLOCKWISE;
        } else if (shortestDiff < 0) {
            return COUNTER_CLOCKWISE;
        }
        return 0;
    }

    public static double calculateOptimizedTargetAngle(double currentAngle, double targetAngle) {
        double current0To360 = normalizeTo0To360(currentAngle);
        double target0To360 = normalizeTo0To360(targetAngle);
        double shortestDiff = calculateShortestDiff(current0To360, target0To360);
        double optimizedTarget = current0To360 + shortestDiff;

        log.info("优化目标角度: 当前={}°, 原始目标={}°, 最短路径目标={}°, 旋转方向={}, 差值={}°",
                current0To360,
                target0To360,
                normalizeTo0To360(optimizedTarget),
                shortestDiff >= 0 ? "顺时针" : "逆时针",
                Math.abs(shortestDiff));

        return normalizeToMinus180To180(optimizedTarget);
    }

    public static double getAbsoluteRotationDegrees(double shortestDiff) {
        return Math.abs(shortestDiff);
    }

    public static boolean isValidAngleRange(double angle) {
        return angle >= MIN_ANGLE && angle <= MAX_ANGLE;
    }

    public static boolean isAngleAligned(double currentAngle, double targetAngle, double tolerance) {
        double diff = Math.abs(calculateShortestDiff(currentAngle, targetAngle));
        return diff <= tolerance;
    }

    public static String formatAngle(double angle) {
        return String.format("%.2f°", angle);
    }
}
