package com.fivesoft.qplayer.common;

import android.os.strictmode.Violation;

import java.io.PrintWriter;
import java.io.StringWriter;

public class StackTraceUtil {

    public static String getStackTrace(Throwable e){

        if(e == null){
            return "Null stack trace.";
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public static String getStackTrace(Violation v){
        if(v == null){
            return "Null stack trace.";
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        v.printStackTrace(pw);

        return sw.toString();
    }

    public static String getCodeLine(){
        try {
            throw new Exception("__");
        } catch (Exception e){
            return getStackTrace(e);
        }
    }

}
