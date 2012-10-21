package org.glassfish.tyrus;

import java.util.HashMap;

/**
 * For the given primitive type returns finds it's boxing alternative.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class PrimitivesToBoxing{

    private static HashMap<Class<?>, Class<?>> conversionMap = null;

    /**
     * Gets the Boxing class for the primitive type.
     *
     * @param input primitive type
     * @return boxing class if input is primitive type, input otherwise
     */
    public static Class<?> getBoxing(Class<?> input){
        if(!input.isPrimitive()){
            return input;
        }
        if(conversionMap == null){
            initConversionMap();
        }
        return conversionMap.containsKey(input) ? conversionMap.get(input) : input;
    }

    private static void initConversionMap(){
        conversionMap = new HashMap<Class<?>, Class<?>>();
        conversionMap.put(int.class, Integer.class);
        conversionMap.put(short.class, Short.class);
        conversionMap.put(long.class, Long.class);
        conversionMap.put(double.class, Double.class);
        conversionMap.put(float.class, Float.class);
        conversionMap.put(boolean.class, Boolean.class);
        conversionMap.put(byte.class, Byte.class);
        conversionMap.put(char.class, Character.class);
    }
}
