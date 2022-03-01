package xyz.panyi.cloudstroage.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class LogUtil {
    private static Logger logger = LoggerFactory.getLogger("log");

    public static void log(final String msg , Object... params){
        logger.info(msg , params);
    }
}
