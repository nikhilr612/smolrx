package bfcarm;

import java.util.function.Function;

public class Test implements Function<Object, Object> {

    @Override
    public Object apply(Object input) {
        if (!(input instanceof Integer)) {
            throw new IllegalArgumentException("Input must be an Integer.");
        }

        int n = (Integer) input;
        return isCarmichaelNumber(n);
    }

    private boolean isCarmichaelNumber(int n) {
        if (n < 2 || isPrime(n)) {
            return false;
        }

        for (int a = 2; a < n; a++) {
            if (gcd(a, n) == 1 && modExp(a, n, n) != (a % n)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPrime(int n) {
        if (n < 2) {
            return false;
        }
        for (int i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    private int modExp(int base, int exp, int mod) {
        int result = 1;
        base = base % mod;
        while (exp > 0) {
            if ((exp & 1) == 1) {
                result = (result * base) % mod;
            }
            exp >>= 1;
            base = (base * base) % mod;
        }
        return result;
    }
}