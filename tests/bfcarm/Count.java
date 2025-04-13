package bfcarm;

import java.util.function.Function;

public class Count implements Function<Object, Object> {

    @Override
    public Object apply(Object input) {
        if (!(input instanceof Object[])) {
            throw new IllegalArgumentException("Input must be an Object array");
        }

        Object[] inputArray = (Object[]) input;
        if (inputArray.length != 2 || !(inputArray[0] instanceof Integer) || !(inputArray[1] instanceof Boolean)) {
            throw new IllegalArgumentException("Input array must contain an Integer at index 0 and a Boolean at index 1");
        }

        int count = (Integer) inputArray[0];
        boolean value = (Boolean) inputArray[1];

        if (value) {
            count++;
        }

        return count;
    }
}