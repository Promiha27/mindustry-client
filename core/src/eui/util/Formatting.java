package eui.util;

/**
 * Compact number formatting shared by the power/resource HUD widgets (net power balance, resource
 * production rate). Ported from utils/formatting.js.
 */
public class Formatting{
    /** e.g. "[stat]+1.2k[white]" - signed, colored, compacted to "k" suffix, /60 tick-rate already applied by the caller (multiplies by 60 here to turn a per-tick value into a per-second one), with an optional "(sep N)" suffix when power is split across several disconnected graphs. */
    public static String powerToString(float currentNetPower, int graphCount){
        long num = Math.round(currentNetPower * 60);

        String graphString = graphCount > 1 ? " (sep " + graphCount + ')' : "";
        String sign = num > 0 ? "+" : "";
        String color = num >= 0 ? "[stat]" : "[red]";

        return color + sign + numberToString(num, 1) + "[white]" + graphString;
    }

    public static String numberToString(long num){
        return numberToString(num, 0);
    }

    /** {@code triplets} shifts the "start compacting" threshold by that many powers of 1000 - e.g. 1 means values under 1000 already get treated as if scaled down one triplet, matching the source's use for power (already a "per interval" figure). */
    public static String numberToString(long num, int triplets){
        if(num == 0) return "0";

        long absNum = Math.abs(num);
        int power = (int)Math.floor(Math.log(absNum) / Math.log(1000)) - triplets;

        if(power > 0){
            String numStr = String.valueOf(num);
            int sliceIndex = -3 * power + 1;

            if(sliceIndex <= 0 || sliceIndex > numStr.length()) return numStr;

            String integerPart = numStr.substring(0, sliceIndex);
            String decimalPart = numStr.substring(sliceIndex, sliceIndex + 1);

            return integerPart + "." + decimalPart + "k".repeat(power);
        }else{
            return String.valueOf(num);
        }
    }
}
