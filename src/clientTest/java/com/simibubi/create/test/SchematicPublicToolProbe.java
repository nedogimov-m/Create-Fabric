package com.simibubi.create.test;

import com.simibubi.create.content.schematics.client.tools.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Observes real public tool entry points; never chooses a tool, modifies selection or sends an action. */
public final class SchematicPublicToolProbe {
    private static final Map<String,AtomicInteger> COUNTS=new ConcurrentHashMap<>();
    private static volatile boolean enabled;
    public static void begin(){enabled=false;COUNTS.clear();enabled=true;}
    public static void clear(){enabled=false;}
    public static void observe(Object tool,String method){if(enabled)COUNTS.computeIfAbsent(tool.getClass().getSimpleName()+"."+method,key->new AtomicInteger()).incrementAndGet();}
    public static void require(ToolType type,String method){var key=type.getTool().getClass().getSimpleName()+"."+method;var count=COUNTS.get(key);if(count==null||count.get()<1)throw new AssertionError("Real native input/extraction never reached public source tool "+key+": "+snapshot());}
    public static Map<String,Integer> snapshot(){var result=new TreeMap<String,Integer>();COUNTS.forEach((key,value)->result.put(key,value.get()));return Map.copyOf(result);}
    public static void requireLifecycle(ToolType type){require(type,"init");require(type,"updateSelection");require(type,"extractOnSchematic");}
    private SchematicPublicToolProbe(){}
}
