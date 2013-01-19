/*
This software is OSI Certified Open Source Software.
OSI Certified is a certification mark of the Open Source Initiative.

The license (Mozilla version 1.0) can be read at the MMBase site.
See http://www.MMBase.org/license

*/

package org.mmbase.util.logging;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * With this class the logging is configured and it supplies the `Logger' objects.
 * <p>
 * For example:
 * <code>
 * <pre>
 * <tt>
 * <b><font color=#0000FF>import</font></b> org.mmbase.util.logging.Logging;
 * <b><font color=#0000FF>import</font></b> org.mmbase.util.logging.Logger;
 * <b><font color=#0000FF>import</font></b> org.mmbase.util.logging.Level;
 *
 * <b><font color=#0000FF>public</font></b> <b><font color=#0000FF>class</font></b> Test {
 *
 *     <b><font color=#0000FF>static</font></b> {
 *         Logging.configure(<font color=#FF0000>"log.xml"</font>);
 *     }
 *
 *     <b><font color=#0000FF>private static final</font></b> Logger LOG = Logging.getLoggerInstance(Test.<b><font color=#0000FF>class</font></b>);
 *
 *     <b><font color=#0000FF>public</font></b> <b><font color=#0000FF>static</font></b> <font color=#009900>void</font> main(String[] args) {
 *         LOG.debug(<font color=#FF0000>"start"</font>);
 *         LOG.info(<font color=#FF0000>"Entering application."</font>);
 *
 *         LOG.setLevel(Level.TRACE);
 *         <b><font color=#0000FF>if</font></b> (log.isDebugEnabled()) {
 *             LOG.debug(<font color=#FF0000>"debug een"</font>);
 *             LOG.trace(<font color=#FF0000>"trace twee"</font>);
 *         }
 *         LOG.info(<font color=#FF0000>"info"</font>);
 *         LOG.service(<font color=#FF0000>"service"</font>);
 *
 *
 *         Logging.shutdown();
 *     }
 * }
 * </tt>
 * </pre>
 * </code>
 * </p>
 *
 * @author Michiel Meeuwissen
 * @version $Id: Logging.java 45316 2011-02-18 20:47:28Z michiel $
 */


public class Logging {

    private static Class<?>  logClass    = SimpleTimeStampImpl.class; // default Logger Implementation
    private static boolean configured = false;
    private static final Logger log   = getLoggerInstance(Logging.class); // logger for this class itself

   /**
    * The category for logging info about pages (like stop / start). Also if pages take the
     * initiative for logging themselves they should log below this category.
     * @since MMBase-1.7
     */
    public final static String PAGE_CATEGORY = "org.mmbase.PAGE";



    private Logging() {
        // this class has no instances.
    }



    /**
     * Calls the 'configure' static method of the used logging class,
     * or does nothing if it doesn't exist. You could call this method
     * if you want to avoid using 'configure', which parses an XML file.
     **/

    public static void configureClass(String configuration) {
        try { // to configure
            // System.out.println("Found class " + logClass.getName());
            Method conf = logClass.getMethod("configure", String.class);
            conf.invoke(null, configuration);
        } catch (NoSuchMethodException e) {
            log.debug("Could not find configure method in " + logClass.getName());
            // okay, simply don't configure
        } catch (java.lang.reflect.InvocationTargetException e) {
            log.error("Invocation Exception while configuration class. " + logClass + " with configuration String '" + configuration + "' :" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("", e);
        }
    }


    /**
     * After configuring the logging system, you can get Logger instances to log with.
     *
     * @param s A string describing the `category' of the Logger. This is a log4j concept.
     */

    public  static Logger getLoggerInstance(String s) {
        // call the getLoggerInstance static method of the logclass:
        try {
            Method getIns = logClass.getMethod("getLoggerInstance", String.class);
            Logger logger =  (Logger) getIns.invoke(null, s);
            if (configured) {
                return logger;
            } else {
                return new LoggerWrapper(logger, s);
            }
        } catch (Exception e) {
            log.warn(e);
            return  SimpleImpl.getLoggerInstance(s);
        }
    }

    /**
     * @since MMBase-1.9.2
     */
    private static ThreadLocal<Map<String, Object>> MDC_MAP = new ThreadLocal<Map<String, Object>>() {
          @Override
          protected Map<String, Object> initialValue() {
              return new HashMap<String, Object>();
          }

    };

    private static MDC mdc = null;

    /**
     * MDC stands for <em>mapped diagnostic contexts</em> See also <a href="http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html">log4j.MDC</a>
     * @since MMBase-1.9.2
     */
    public static MDC getMDC() {
        if (mdc == null) {
            try {
                Method getIns = logClass.getMethod("getMDC");
                mdc = (MDC) getIns.invoke(null);
            } catch (Exception e) {
                log.warn(e);
                mdc = new MDC() {
                    @Override
                        public void put(String key, Object value) {
                            if (value != null) {
                                MDC_MAP.get().put(key, value);
                            } else {
                                MDC_MAP.get().remove(key);
                            }
                        }

                    @Override
                        public Object get(String key) {
                            return MDC_MAP.get().get(key);
                        }
                    };

            }
            log.service("Found MDC " + mdc);
        }
        return mdc;
    }


    /**
     * Most Logger categories in MMBase are based on class name.
     * @since MMBase-1.6.4
     */
    public static Logger getLoggerInstance(Class<?> cl) {
        return getLoggerInstance(cl.getName());
    }

    /**
     * Returns a Set of String which indicates where your logging can
     * be (If this is implemented in the class).
     */
    /*
    public  static Set getLocations() {
        // call the getLoggerInstance static method of the logclass:
        try {
            Method getIns = logClass.getMethod("getLocations", new Class[] {} );
            return  (Set) getIns.invoke(null, new Object[] {});
        } catch (Exception e) {
            HashSet result = new HashSet();
            result.add("<could not be determined>");
            return result;
        }
    }
    */

    /**
     * If the configured Logger implements a shutdown static method,
     * it will be called. (the log4j Category does).
     *
     */
    public static void shutdown() {
        try {
            if (configured) {
                for (LoggerWrapper wrapper : LoggerWrapper.getWrappers()) {
                    wrapper.setLogger(SimpleImpl.getLoggerInstance(wrapper.getName() + ".SHUTDOWN"));
                }
                if (logClass != null) {
                    Method shutdown = logClass.getMethod("shutdown");
                    shutdown.invoke(null);
                }
                mdc = null;
                configured = false;
            }
        } catch (NoSuchMethodException e) {
            // System.err.println("No such method"); // okay, nothing to shutdown.
        } catch (Throwable e) {
            System.err.println(e + stackTrace(e));
        }

    }

    /**
     * Returns the stacktrace of the current call. This can be used to get a stacktrace
     * when no exception was thrown and my help determine the root cause of an error message
     * (what class called the method that gave the error message.
     * @since MMBase-1.7
     *
     **/
    public static String stackTrace() {
        return stackTrace(-1);
    }

    /**
     * @since MMBase-1.7
     */
    public static String stackTrace(int max) {
        Exception e = new Exception("logging.stacktrace");
        /*
        StackTraceElement[] stack = e.getStackTrace();
        java.util.List stackList = new java.util.ArrayList(java.util.Arrays.asList(stack));
        stackList.remove(0); // is Logging.stackTrace, which is hardly interesting
        e.setStackTrace((StackTraceElement[])stackList.toArray());
        */
        return stackTrace(e, max);
    }

    /**
     * Returns the stacktrace of an exception as a string, which can
     * be logged handy.  Doing simply e.printStackTrace() would dump
     * the stack trace to standard error, which with the log4j
     * implementation will appear in the log file too, but this is a
     * little nicer.
     *
     * It is also possible to call 'error' or 'fatal' with an extra argument.
     *
     * @param e the Throwable from which the stack trace must be stringified.
     *
     **/
    public static String stackTrace(Throwable e) {
        return stackTrace(e, -1);
    }

    /**
     * Also returns a stringified stack trace to log, but no deeper than given max.
     * @since MMBase-1.7
     */
    public static String stackTrace(Throwable e, int max) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        String message = e.getMessage();
        StringBuilder buf = new StringBuilder(e.getClass().getName());
        buf.append(": ");
        if (message == null) {

        }  else {
            buf.append(message);
        }
        for (int i = 0; i < stackTrace.length; i++) {
            if (i == max) break;
            buf.append("\n        at ").append(stackTrace[i]);
        }
        Throwable t = e.getCause();
        if (t != null) {
            buf.append("\n").append(stackTrace(t, max));
        }
        return buf.toString();
    }

    /**
     * @since MMBase-1.8
     */
    public static String applicationStacktrace() {
        Exception e = new Exception("logging.showApplicationStacktrace");
        return applicationStacktrace(e);
    }

    /**
     * @since MMBase-1.8
     */
    public static String applicationStacktrace(Throwable e) {
        StringBuilder buf = new StringBuilder("Application stacktrace");

        // Get the stack trace
        StackTraceElement stackTrace[] = e.getStackTrace();
        // stackTrace[0] contains the method that created the exception.
        // stackTrace[stackTrace.length-1] contains the oldest method call.
        // Enumerate each stack element.

        boolean mmbaseClassesFound = false;
        int appended = 0;
        for (StackTraceElement element : stackTrace) {
           String className = element.getClassName();

           if (className.indexOf("org.mmbase") > -1) {
               mmbaseClassesFound = true;
               // show mmbase taglib
               if (className.indexOf("bridge.jsp.taglib") > -1) {
                   buf.append("\n        at ").append(element);
                   appended++;
               }
           } else {
               if (mmbaseClassesFound) {
                   // show no mmbase method which invoked an mmbase method.
                   buf.append("\n        at ").append(element);
                   appended++;
                   break;
               }
               // show compiled jsp lines
               if (className.indexOf("_jsp") > -1) {
                   buf.append("\n        at ").append(element);
                   appended++;
               }
           }
        }
        if (appended == 0) {
            for (int i = 2; i < stackTrace.length; i++) {
                buf.append("\n        at ").append(stackTrace[i]);
            }
        }
        return buf.toString();
    }
    /**
     * Utility method for dynamicly determin the level of logging.
     * @since MMBase-1.9
     */
    public static void log(Level l, Logger log, String mes) {
        switch(l.toInt()) {
        case Level.TRACE_INT:   log.trace(mes); break;
        case Level.DEBUG_INT:   log.debug(mes); break;
        case Level.SERVICE_INT: log.service(mes); break;
        case Level.INFO_INT:    log.info(mes); break;
        case Level.WARN_INT:    log.warn(mes); break;
        case Level.ERROR_INT:   log.error(mes); break;
        case Level.FATAL_INT:   log.fatal(mes); break;
        case Level.OFF_INT:     break;
        default: break;
        }

    }

    /**
     * Utility method for dynamically checking the 'enabled'ness of a logger on a given level.
     * @since MMBase-1.9
     */
    public static boolean isEnabled(Level l, Logger log) {
        switch(l.toInt()) {
        case Level.TRACE_INT:   return log.isTraceEnabled();
        case Level.DEBUG_INT:   return log.isDebugEnabled();
        case Level.SERVICE_INT: return log.isServiceEnabled();
        case Level.OFF_INT:     return false;
        case Level.INFO_INT:
        case Level.WARN_INT:
        case Level.ERROR_INT:
        case Level.FATAL_INT:
        default:
            return true;
        }
    }


}
