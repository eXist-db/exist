package org.exist.xquery.regex;

import java.util.Arrays;

import org.exist.util.UTF16CharacterSet;

/**
 * This module contains data regarding the classification of characters in XML 1.0 and XML 1.1, and a number
 * of interrogative methods to support queries on this data.
 *
 * For characters in the BMP, the information is tabulated by means of an array of one-byte entries,
 * one for each character, with bit-significant property settings. For characters outside the BMP, the
 * rules are built in to the interrogative methods.
 * 
 * Copied from Saxon-HE 9.2 package net.sf.saxon.regex.
 */
public class XMLCharacterData {

    private final static byte[] data = new byte[65536];

    /**
     * Bit setting to indicate that a character is valid in XML 1.0
     */
    public final static byte VALID_10_MASK = 1;
    /**
     * Bit setting to indicate that a character is valid in an XML 1.0 name
     */
    public final static byte NAME_10_MASK = 2;
    /**
     * Bit setting to indicate that a character is valid at the start of an XML 1.0 name
     */
    public final static byte NAME_START_10_MASK = 4;
    /**
     * Bit setting to indicate that a character is valid in XML 1.1
     */
    public final static byte VALID_11_MASK = 8;
    /**
     * Bit setting to indicate that a character is valid in an XML 1.1 name
     */
    public final static byte NAME_11_MASK = 16;
    /**
     * Bit setting to indicate that a character is valid at the start of an XML 1.1 name
     */
    public final static byte NAME_START_11_MASK = 32;
    /**
     * Maximum code point for a character permitted in an XML 1.1 name
     */
    public final static int MAX_XML11_NAME_CHAR = 0xEFFFF;

    /**
     * Determine whether a character is valid in XML 1.0
     * @param i the character
     * @return true if the character is valid in XML 1.0
     */

    public static boolean isValid10(int i) {
        return i < 65536 ? (data[i]&VALID_10_MASK) != 0 : (UTF16CharacterSet.NONBMP_MIN <= i && i <= UTF16CharacterSet.NONBMP_MAX);
    }

    /**
     * Determine whether a character is valid in an NCName in XML 1.0
     * @param i the character
     * @return true if the character is valid in an NCName in XML 1.0
     */

    public static boolean isNCName10(int i) {
        return i < 65536 && (data[i]&NAME_10_MASK) != 0;
    }

    /**
     * Determine whether a character is valid at the start of an NCName in XML 1.0
     * @param i the character
     * @return true if the character is valid at the start of an NCName in XML 1.0
     */

    public static boolean isNCNameStart10(int i) {
        return i < 65536 && (data[i]&NAME_START_10_MASK) != 0;
    }

    /**
     * Determine whether a character is valid in XML 1.1
     * @param i the character
     * @return true if the character is valid in XML 1.1
     */

    public static boolean isValid11(int i) {
        return i < 65536 ? (data[i]&VALID_11_MASK) != 0 : (UTF16CharacterSet.NONBMP_MIN <= i && i <= UTF16CharacterSet.NONBMP_MAX);
    }

    /**
     * Determine whether a character is valid in an NCName in XML 1.1
     * @param i the character
     * @return true if the character is valid in an NCName in XML 1.1
     */

    public static boolean isNCName11(int i) {
        return i < 65536 ? (data[i]&NAME_11_MASK) != 0 : (UTF16CharacterSet.NONBMP_MIN <= i && i <= MAX_XML11_NAME_CHAR);
    }

    /**
     * Determine whether a character is valid at the start of an NCName in XML 1.1
     * @param i the character
     * @return true if the character is valid at the start of an NCName in XML 1.1
     */

    public static boolean isNCNameStart11(int i) {
        return i < 65536 ? (data[i]&NAME_START_11_MASK) != 0 : (UTF16CharacterSet.NONBMP_MIN <= i && i <= MAX_XML11_NAME_CHAR);
    }

    /**
     * Static code to initialize the data table
     */

    static {
        data[0] = (byte)0;
        Arrays.fill(data, 1, 9, (byte)8);
        Arrays.fill(data, 9, 11, (byte)9);
        Arrays.fill(data, 11, 13, (byte)8);
        data[13] = (byte)9;
        Arrays.fill(data, 14, 32, (byte)8);
        Arrays.fill(data, 32, 45, (byte)9);
        Arrays.fill(data, 45, 47, (byte)27);
        data[47] = (byte)9;
        Arrays.fill(data, 48, 58, (byte)27);
        data[58] = (byte)9;  // colon
        Arrays.fill(data, 59, 65, (byte)9);
        Arrays.fill(data, 65, 91, (byte)63);
        Arrays.fill(data, 91, 95, (byte)9);
        data[95] = (byte)63;
        data[96] = (byte)9;
        Arrays.fill(data, 97, 123, (byte)63);
        Arrays.fill(data, 123, 183, (byte)9);
        data[183] = (byte)27;
        Arrays.fill(data, 184, 192, (byte)9);
        Arrays.fill(data, 192, 215, (byte)63);
        data[215] = (byte)9;
        Arrays.fill(data, 216, 247, (byte)63);
        data[247] = (byte)9;
        Arrays.fill(data, 248, 306, (byte)63);
        Arrays.fill(data, 306, 308, (byte)57);
        Arrays.fill(data, 308, 319, (byte)63);
        Arrays.fill(data, 319, 321, (byte)57);
        Arrays.fill(data, 321, 329, (byte)63);
        data[329] = (byte)57;
        Arrays.fill(data, 330, 383, (byte)63);
        data[383] = (byte)57;
        Arrays.fill(data, 384, 452, (byte)63);
        Arrays.fill(data, 452, 461, (byte)57);
        Arrays.fill(data, 461, 497, (byte)63);
        Arrays.fill(data, 497, 500, (byte)57);
        Arrays.fill(data, 500, 502, (byte)63);
        Arrays.fill(data, 502, 506, (byte)57);
        Arrays.fill(data, 506, 536, (byte)63);
        Arrays.fill(data, 536, 592, (byte)57);
        Arrays.fill(data, 592, 681, (byte)63);
        Arrays.fill(data, 681, 699, (byte)57);
        Arrays.fill(data, 699, 706, (byte)63);
        Arrays.fill(data, 706, 720, (byte)57);
        Arrays.fill(data, 720, 722, (byte)59);
        Arrays.fill(data, 722, 768, (byte)57);
        Arrays.fill(data, 768, 838, (byte)27);
        Arrays.fill(data, 838, 864, (byte)25);
        Arrays.fill(data, 864, 866, (byte)27);
        Arrays.fill(data, 866, 880, (byte)25);
        Arrays.fill(data, 880, 894, (byte)57);
        data[894] = (byte)9;
        Arrays.fill(data, 895, 902, (byte)57);
        data[902] = (byte)63;
        data[903] = (byte)59;
        Arrays.fill(data, 904, 907, (byte)63);
        data[907] = (byte)57;
        data[908] = (byte)63;
        data[909] = (byte)57;
        Arrays.fill(data, 910, 930, (byte)63);
        data[930] = (byte)57;
        Arrays.fill(data, 931, 975, (byte)63);
        data[975] = (byte)57;
        Arrays.fill(data, 976, 983, (byte)63);
        Arrays.fill(data, 983, 986, (byte)57);
        data[986] = (byte)63;
        data[987] = (byte)57;
        data[988] = (byte)63;
        data[989] = (byte)57;
        data[990] = (byte)63;
        data[991] = (byte)57;
        data[992] = (byte)63;
        data[993] = (byte)57;
        Arrays.fill(data, 994, 1012, (byte)63);
        Arrays.fill(data, 1012, 1025, (byte)57);
        Arrays.fill(data, 1025, 1037, (byte)63);
        data[1037] = (byte)57;
        Arrays.fill(data, 1038, 1104, (byte)63);
        data[1104] = (byte)57;
        Arrays.fill(data, 1105, 1117, (byte)63);
        data[1117] = (byte)57;
        Arrays.fill(data, 1118, 1154, (byte)63);
        data[1154] = (byte)57;
        Arrays.fill(data, 1155, 1159, (byte)59);
        Arrays.fill(data, 1159, 1168, (byte)57);
        Arrays.fill(data, 1168, 1221, (byte)63);
        Arrays.fill(data, 1221, 1223, (byte)57);
        Arrays.fill(data, 1223, 1225, (byte)63);
        Arrays.fill(data, 1225, 1227, (byte)57);
        Arrays.fill(data, 1227, 1229, (byte)63);
        Arrays.fill(data, 1229, 1232, (byte)57);
        Arrays.fill(data, 1232, 1260, (byte)63);
        Arrays.fill(data, 1260, 1262, (byte)57);
        Arrays.fill(data, 1262, 1270, (byte)63);
        Arrays.fill(data, 1270, 1272, (byte)57);
        Arrays.fill(data, 1272, 1274, (byte)63);
        Arrays.fill(data, 1274, 1329, (byte)57);
        Arrays.fill(data, 1329, 1367, (byte)63);
        Arrays.fill(data, 1367, 1369, (byte)57);
        data[1369] = (byte)63;
        Arrays.fill(data, 1370, 1377, (byte)57);
        Arrays.fill(data, 1377, 1415, (byte)63);
        Arrays.fill(data, 1415, 1425, (byte)57);
        Arrays.fill(data, 1425, 1442, (byte)59);
        data[1442] = (byte)57;
        Arrays.fill(data, 1443, 1466, (byte)59);
        data[1466] = (byte)57;
        Arrays.fill(data, 1467, 1470, (byte)59);
        data[1470] = (byte)57;
        data[1471] = (byte)59;
        data[1472] = (byte)57;
        Arrays.fill(data, 1473, 1475, (byte)59);
        data[1475] = (byte)57;
        data[1476] = (byte)59;
        Arrays.fill(data, 1477, 1488, (byte)57);
        Arrays.fill(data, 1488, 1515, (byte)63);
        Arrays.fill(data, 1515, 1520, (byte)57);
        Arrays.fill(data, 1520, 1523, (byte)63);
        Arrays.fill(data, 1523, 1569, (byte)57);
        Arrays.fill(data, 1569, 1595, (byte)63);
        Arrays.fill(data, 1595, 1600, (byte)57);
        data[1600] = (byte)59;
        Arrays.fill(data, 1601, 1611, (byte)63);
        Arrays.fill(data, 1611, 1619, (byte)59);
        Arrays.fill(data, 1619, 1632, (byte)57);
        Arrays.fill(data, 1632, 1642, (byte)59);
        Arrays.fill(data, 1642, 1648, (byte)57);
        data[1648] = (byte)59;
        Arrays.fill(data, 1649, 1720, (byte)63);
        Arrays.fill(data, 1720, 1722, (byte)57);
        Arrays.fill(data, 1722, 1727, (byte)63);
        data[1727] = (byte)57;
        Arrays.fill(data, 1728, 1743, (byte)63);
        data[1743] = (byte)57;
        Arrays.fill(data, 1744, 1748, (byte)63);
        data[1748] = (byte)57;
        data[1749] = (byte)63;
        Arrays.fill(data, 1750, 1765, (byte)59);
        Arrays.fill(data, 1765, 1767, (byte)63);
        Arrays.fill(data, 1767, 1769, (byte)59);
        data[1769] = (byte)57;
        Arrays.fill(data, 1770, 1774, (byte)59);
        Arrays.fill(data, 1774, 1776, (byte)57);
        Arrays.fill(data, 1776, 1786, (byte)59);
        Arrays.fill(data, 1786, 2305, (byte)57);
        Arrays.fill(data, 2305, 2308, (byte)59);
        data[2308] = (byte)57;
        Arrays.fill(data, 2309, 2362, (byte)63);
        Arrays.fill(data, 2362, 2364, (byte)57);
        data[2364] = (byte)59;
        data[2365] = (byte)63;
        Arrays.fill(data, 2366, 2382, (byte)59);
        Arrays.fill(data, 2382, 2385, (byte)57);
        Arrays.fill(data, 2385, 2389, (byte)59);
        Arrays.fill(data, 2389, 2392, (byte)57);
        Arrays.fill(data, 2392, 2402, (byte)63);
        Arrays.fill(data, 2402, 2404, (byte)59);
        Arrays.fill(data, 2404, 2406, (byte)57);
        Arrays.fill(data, 2406, 2416, (byte)59);
        Arrays.fill(data, 2416, 2433, (byte)57);
        Arrays.fill(data, 2433, 2436, (byte)59);
        data[2436] = (byte)57;
        Arrays.fill(data, 2437, 2445, (byte)63);
        Arrays.fill(data, 2445, 2447, (byte)57);
        Arrays.fill(data, 2447, 2449, (byte)63);
        Arrays.fill(data, 2449, 2451, (byte)57);
        Arrays.fill(data, 2451, 2473, (byte)63);
        data[2473] = (byte)57;
        Arrays.fill(data, 2474, 2481, (byte)63);
        data[2481] = (byte)57;
        data[2482] = (byte)63;
        Arrays.fill(data, 2483, 2486, (byte)57);
        Arrays.fill(data, 2486, 2490, (byte)63);
        Arrays.fill(data, 2490, 2492, (byte)57);
        data[2492] = (byte)59;
        data[2493] = (byte)57;
        Arrays.fill(data, 2494, 2501, (byte)59);
        Arrays.fill(data, 2501, 2503, (byte)57);
        Arrays.fill(data, 2503, 2505, (byte)59);
        Arrays.fill(data, 2505, 2507, (byte)57);
        Arrays.fill(data, 2507, 2510, (byte)59);
        Arrays.fill(data, 2510, 2519, (byte)57);
        data[2519] = (byte)59;
        Arrays.fill(data, 2520, 2524, (byte)57);
        Arrays.fill(data, 2524, 2526, (byte)63);
        data[2526] = (byte)57;
        Arrays.fill(data, 2527, 2530, (byte)63);
        Arrays.fill(data, 2530, 2532, (byte)59);
        Arrays.fill(data, 2532, 2534, (byte)57);
        Arrays.fill(data, 2534, 2544, (byte)59);
        Arrays.fill(data, 2544, 2546, (byte)63);
        Arrays.fill(data, 2546, 2562, (byte)57);
        data[2562] = (byte)59;
        Arrays.fill(data, 2563, 2565, (byte)57);
        Arrays.fill(data, 2565, 2571, (byte)63);
        Arrays.fill(data, 2571, 2575, (byte)57);
        Arrays.fill(data, 2575, 2577, (byte)63);
        Arrays.fill(data, 2577, 2579, (byte)57);
        Arrays.fill(data, 2579, 2601, (byte)63);
        data[2601] = (byte)57;
        Arrays.fill(data, 2602, 2609, (byte)63);
        data[2609] = (byte)57;
        Arrays.fill(data, 2610, 2612, (byte)63);
        data[2612] = (byte)57;
        Arrays.fill(data, 2613, 2615, (byte)63);
        data[2615] = (byte)57;
        Arrays.fill(data, 2616, 2618, (byte)63);
        Arrays.fill(data, 2618, 2620, (byte)57);
        data[2620] = (byte)59;
        data[2621] = (byte)57;
        Arrays.fill(data, 2622, 2627, (byte)59);
        Arrays.fill(data, 2627, 2631, (byte)57);
        Arrays.fill(data, 2631, 2633, (byte)59);
        Arrays.fill(data, 2633, 2635, (byte)57);
        Arrays.fill(data, 2635, 2638, (byte)59);
        Arrays.fill(data, 2638, 2649, (byte)57);
        Arrays.fill(data, 2649, 2653, (byte)63);
        data[2653] = (byte)57;
        data[2654] = (byte)63;
        Arrays.fill(data, 2655, 2662, (byte)57);
        Arrays.fill(data, 2662, 2674, (byte)59);
        Arrays.fill(data, 2674, 2677, (byte)63);
        Arrays.fill(data, 2677, 2689, (byte)57);
        Arrays.fill(data, 2689, 2692, (byte)59);
        data[2692] = (byte)57;
        Arrays.fill(data, 2693, 2700, (byte)63);
        data[2700] = (byte)57;
        data[2701] = (byte)63;
        data[2702] = (byte)57;
        Arrays.fill(data, 2703, 2706, (byte)63);
        data[2706] = (byte)57;
        Arrays.fill(data, 2707, 2729, (byte)63);
        data[2729] = (byte)57;
        Arrays.fill(data, 2730, 2737, (byte)63);
        data[2737] = (byte)57;
        Arrays.fill(data, 2738, 2740, (byte)63);
        data[2740] = (byte)57;
        Arrays.fill(data, 2741, 2746, (byte)63);
        Arrays.fill(data, 2746, 2748, (byte)57);
        data[2748] = (byte)59;
        data[2749] = (byte)63;
        Arrays.fill(data, 2750, 2758, (byte)59);
        data[2758] = (byte)57;
        Arrays.fill(data, 2759, 2762, (byte)59);
        data[2762] = (byte)57;
        Arrays.fill(data, 2763, 2766, (byte)59);
        Arrays.fill(data, 2766, 2784, (byte)57);
        data[2784] = (byte)63;
        Arrays.fill(data, 2785, 2790, (byte)57);
        Arrays.fill(data, 2790, 2800, (byte)59);
        Arrays.fill(data, 2800, 2817, (byte)57);
        Arrays.fill(data, 2817, 2820, (byte)59);
        data[2820] = (byte)57;
        Arrays.fill(data, 2821, 2829, (byte)63);
        Arrays.fill(data, 2829, 2831, (byte)57);
        Arrays.fill(data, 2831, 2833, (byte)63);
        Arrays.fill(data, 2833, 2835, (byte)57);
        Arrays.fill(data, 2835, 2857, (byte)63);
        data[2857] = (byte)57;
        Arrays.fill(data, 2858, 2865, (byte)63);
        data[2865] = (byte)57;
        Arrays.fill(data, 2866, 2868, (byte)63);
        Arrays.fill(data, 2868, 2870, (byte)57);
        Arrays.fill(data, 2870, 2874, (byte)63);
        Arrays.fill(data, 2874, 2876, (byte)57);
        data[2876] = (byte)59;
        data[2877] = (byte)63;
        Arrays.fill(data, 2878, 2884, (byte)59);
        Arrays.fill(data, 2884, 2887, (byte)57);
        Arrays.fill(data, 2887, 2889, (byte)59);
        Arrays.fill(data, 2889, 2891, (byte)57);
        Arrays.fill(data, 2891, 2894, (byte)59);
        Arrays.fill(data, 2894, 2902, (byte)57);
        Arrays.fill(data, 2902, 2904, (byte)59);
        Arrays.fill(data, 2904, 2908, (byte)57);
        Arrays.fill(data, 2908, 2910, (byte)63);
        data[2910] = (byte)57;
        Arrays.fill(data, 2911, 2914, (byte)63);
        Arrays.fill(data, 2914, 2918, (byte)57);
        Arrays.fill(data, 2918, 2928, (byte)59);
        Arrays.fill(data, 2928, 2946, (byte)57);
        Arrays.fill(data, 2946, 2948, (byte)59);
        data[2948] = (byte)57;
        Arrays.fill(data, 2949, 2955, (byte)63);
        Arrays.fill(data, 2955, 2958, (byte)57);
        Arrays.fill(data, 2958, 2961, (byte)63);
        data[2961] = (byte)57;
        Arrays.fill(data, 2962, 2966, (byte)63);
        Arrays.fill(data, 2966, 2969, (byte)57);
        Arrays.fill(data, 2969, 2971, (byte)63);
        data[2971] = (byte)57;
        data[2972] = (byte)63;
        data[2973] = (byte)57;
        Arrays.fill(data, 2974, 2976, (byte)63);
        Arrays.fill(data, 2976, 2979, (byte)57);
        Arrays.fill(data, 2979, 2981, (byte)63);
        Arrays.fill(data, 2981, 2984, (byte)57);
        Arrays.fill(data, 2984, 2987, (byte)63);
        Arrays.fill(data, 2987, 2990, (byte)57);
        Arrays.fill(data, 2990, 2998, (byte)63);
        data[2998] = (byte)57;
        Arrays.fill(data, 2999, 3002, (byte)63);
        Arrays.fill(data, 3002, 3006, (byte)57);
        Arrays.fill(data, 3006, 3011, (byte)59);
        Arrays.fill(data, 3011, 3014, (byte)57);
        Arrays.fill(data, 3014, 3017, (byte)59);
        data[3017] = (byte)57;
        Arrays.fill(data, 3018, 3022, (byte)59);
        Arrays.fill(data, 3022, 3031, (byte)57);
        data[3031] = (byte)59;
        Arrays.fill(data, 3032, 3047, (byte)57);
        Arrays.fill(data, 3047, 3056, (byte)59);
        Arrays.fill(data, 3056, 3073, (byte)57);
        Arrays.fill(data, 3073, 3076, (byte)59);
        data[3076] = (byte)57;
        Arrays.fill(data, 3077, 3085, (byte)63);
        data[3085] = (byte)57;
        Arrays.fill(data, 3086, 3089, (byte)63);
        data[3089] = (byte)57;
        Arrays.fill(data, 3090, 3113, (byte)63);
        data[3113] = (byte)57;
        Arrays.fill(data, 3114, 3124, (byte)63);
        data[3124] = (byte)57;
        Arrays.fill(data, 3125, 3130, (byte)63);
        Arrays.fill(data, 3130, 3134, (byte)57);
        Arrays.fill(data, 3134, 3141, (byte)59);
        data[3141] = (byte)57;
        Arrays.fill(data, 3142, 3145, (byte)59);
        data[3145] = (byte)57;
        Arrays.fill(data, 3146, 3150, (byte)59);
        Arrays.fill(data, 3150, 3157, (byte)57);
        Arrays.fill(data, 3157, 3159, (byte)59);
        Arrays.fill(data, 3159, 3168, (byte)57);
        Arrays.fill(data, 3168, 3170, (byte)63);
        Arrays.fill(data, 3170, 3174, (byte)57);
        Arrays.fill(data, 3174, 3184, (byte)59);
        Arrays.fill(data, 3184, 3202, (byte)57);
        Arrays.fill(data, 3202, 3204, (byte)59);
        data[3204] = (byte)57;
        Arrays.fill(data, 3205, 3213, (byte)63);
        data[3213] = (byte)57;
        Arrays.fill(data, 3214, 3217, (byte)63);
        data[3217] = (byte)57;
        Arrays.fill(data, 3218, 3241, (byte)63);
        data[3241] = (byte)57;
        Arrays.fill(data, 3242, 3252, (byte)63);
        data[3252] = (byte)57;
        Arrays.fill(data, 3253, 3258, (byte)63);
        Arrays.fill(data, 3258, 3262, (byte)57);
        Arrays.fill(data, 3262, 3269, (byte)59);
        data[3269] = (byte)57;
        Arrays.fill(data, 3270, 3273, (byte)59);
        data[3273] = (byte)57;
        Arrays.fill(data, 3274, 3278, (byte)59);
        Arrays.fill(data, 3278, 3285, (byte)57);
        Arrays.fill(data, 3285, 3287, (byte)59);
        Arrays.fill(data, 3287, 3294, (byte)57);
        data[3294] = (byte)63;
        data[3295] = (byte)57;
        Arrays.fill(data, 3296, 3298, (byte)63);
        Arrays.fill(data, 3298, 3302, (byte)57);
        Arrays.fill(data, 3302, 3312, (byte)59);
        Arrays.fill(data, 3312, 3330, (byte)57);
        Arrays.fill(data, 3330, 3332, (byte)59);
        data[3332] = (byte)57;
        Arrays.fill(data, 3333, 3341, (byte)63);
        data[3341] = (byte)57;
        Arrays.fill(data, 3342, 3345, (byte)63);
        data[3345] = (byte)57;
        Arrays.fill(data, 3346, 3369, (byte)63);
        data[3369] = (byte)57;
        Arrays.fill(data, 3370, 3386, (byte)63);
        Arrays.fill(data, 3386, 3390, (byte)57);
        Arrays.fill(data, 3390, 3396, (byte)59);
        Arrays.fill(data, 3396, 3398, (byte)57);
        Arrays.fill(data, 3398, 3401, (byte)59);
        data[3401] = (byte)57;
        Arrays.fill(data, 3402, 3406, (byte)59);
        Arrays.fill(data, 3406, 3415, (byte)57);
        data[3415] = (byte)59;
        Arrays.fill(data, 3416, 3424, (byte)57);
        Arrays.fill(data, 3424, 3426, (byte)63);
        Arrays.fill(data, 3426, 3430, (byte)57);
        Arrays.fill(data, 3430, 3440, (byte)59);
        Arrays.fill(data, 3440, 3585, (byte)57);
        Arrays.fill(data, 3585, 3631, (byte)63);
        data[3631] = (byte)57;
        data[3632] = (byte)63;
        data[3633] = (byte)59;
        Arrays.fill(data, 3634, 3636, (byte)63);
        Arrays.fill(data, 3636, 3643, (byte)59);
        Arrays.fill(data, 3643, 3648, (byte)57);
        Arrays.fill(data, 3648, 3654, (byte)63);
        Arrays.fill(data, 3654, 3663, (byte)59);
        data[3663] = (byte)57;
        Arrays.fill(data, 3664, 3674, (byte)59);
        Arrays.fill(data, 3674, 3713, (byte)57);
        Arrays.fill(data, 3713, 3715, (byte)63);
        data[3715] = (byte)57;
        data[3716] = (byte)63;
        Arrays.fill(data, 3717, 3719, (byte)57);
        Arrays.fill(data, 3719, 3721, (byte)63);
        data[3721] = (byte)57;
        data[3722] = (byte)63;
        Arrays.fill(data, 3723, 3725, (byte)57);
        data[3725] = (byte)63;
        Arrays.fill(data, 3726, 3732, (byte)57);
        Arrays.fill(data, 3732, 3736, (byte)63);
        data[3736] = (byte)57;
        Arrays.fill(data, 3737, 3744, (byte)63);
        data[3744] = (byte)57;
        Arrays.fill(data, 3745, 3748, (byte)63);
        data[3748] = (byte)57;
        data[3749] = (byte)63;
        data[3750] = (byte)57;
        data[3751] = (byte)63;
        Arrays.fill(data, 3752, 3754, (byte)57);
        Arrays.fill(data, 3754, 3756, (byte)63);
        data[3756] = (byte)57;
        Arrays.fill(data, 3757, 3759, (byte)63);
        data[3759] = (byte)57;
        data[3760] = (byte)63;
        data[3761] = (byte)59;
        Arrays.fill(data, 3762, 3764, (byte)63);
        Arrays.fill(data, 3764, 3770, (byte)59);
        data[3770] = (byte)57;
        Arrays.fill(data, 3771, 3773, (byte)59);
        data[3773] = (byte)63;
        Arrays.fill(data, 3774, 3776, (byte)57);
        Arrays.fill(data, 3776, 3781, (byte)63);
        data[3781] = (byte)57;
        data[3782] = (byte)59;
        data[3783] = (byte)57;
        Arrays.fill(data, 3784, 3790, (byte)59);
        Arrays.fill(data, 3790, 3792, (byte)57);
        Arrays.fill(data, 3792, 3802, (byte)59);
        Arrays.fill(data, 3802, 3864, (byte)57);
        Arrays.fill(data, 3864, 3866, (byte)59);
        Arrays.fill(data, 3866, 3872, (byte)57);
        Arrays.fill(data, 3872, 3882, (byte)59);
        Arrays.fill(data, 3882, 3893, (byte)57);
        data[3893] = (byte)59;
        data[3894] = (byte)57;
        data[3895] = (byte)59;
        data[3896] = (byte)57;
        data[3897] = (byte)59;
        Arrays.fill(data, 3898, 3902, (byte)57);
        Arrays.fill(data, 3902, 3904, (byte)59);
        Arrays.fill(data, 3904, 3912, (byte)63);
        data[3912] = (byte)57;
        Arrays.fill(data, 3913, 3946, (byte)63);
        Arrays.fill(data, 3946, 3953, (byte)57);
        Arrays.fill(data, 3953, 3973, (byte)59);
        data[3973] = (byte)57;
        Arrays.fill(data, 3974, 3980, (byte)59);
        Arrays.fill(data, 3980, 3984, (byte)57);
        Arrays.fill(data, 3984, 3990, (byte)59);
        data[3990] = (byte)57;
        data[3991] = (byte)59;
        data[3992] = (byte)57;
        Arrays.fill(data, 3993, 4014, (byte)59);
        Arrays.fill(data, 4014, 4017, (byte)57);
        Arrays.fill(data, 4017, 4024, (byte)59);
        data[4024] = (byte)57;
        data[4025] = (byte)59;
        Arrays.fill(data, 4026, 4256, (byte)57);
        Arrays.fill(data, 4256, 4294, (byte)63);
        Arrays.fill(data, 4294, 4304, (byte)57);
        Arrays.fill(data, 4304, 4343, (byte)63);
        Arrays.fill(data, 4343, 4352, (byte)57);
        data[4352] = (byte)63;
        data[4353] = (byte)57;
        Arrays.fill(data, 4354, 4356, (byte)63);
        data[4356] = (byte)57;
        Arrays.fill(data, 4357, 4360, (byte)63);
        data[4360] = (byte)57;
        data[4361] = (byte)63;
        data[4362] = (byte)57;
        Arrays.fill(data, 4363, 4365, (byte)63);
        data[4365] = (byte)57;
        Arrays.fill(data, 4366, 4371, (byte)63);
        Arrays.fill(data, 4371, 4412, (byte)57);
        data[4412] = (byte)63;
        data[4413] = (byte)57;
        data[4414] = (byte)63;
        data[4415] = (byte)57;
        data[4416] = (byte)63;
        Arrays.fill(data, 4417, 4428, (byte)57);
        data[4428] = (byte)63;
        data[4429] = (byte)57;
        data[4430] = (byte)63;
        data[4431] = (byte)57;
        data[4432] = (byte)63;
        Arrays.fill(data, 4433, 4436, (byte)57);
        Arrays.fill(data, 4436, 4438, (byte)63);
        Arrays.fill(data, 4438, 4441, (byte)57);
        data[4441] = (byte)63;
        Arrays.fill(data, 4442, 4447, (byte)57);
        Arrays.fill(data, 4447, 4450, (byte)63);
        data[4450] = (byte)57;
        data[4451] = (byte)63;
        data[4452] = (byte)57;
        data[4453] = (byte)63;
        data[4454] = (byte)57;
        data[4455] = (byte)63;
        data[4456] = (byte)57;
        data[4457] = (byte)63;
        Arrays.fill(data, 4458, 4461, (byte)57);
        Arrays.fill(data, 4461, 4463, (byte)63);
        Arrays.fill(data, 4463, 4466, (byte)57);
        Arrays.fill(data, 4466, 4468, (byte)63);
        data[4468] = (byte)57;
        data[4469] = (byte)63;
        Arrays.fill(data, 4470, 4510, (byte)57);
        data[4510] = (byte)63;
        Arrays.fill(data, 4511, 4520, (byte)57);
        data[4520] = (byte)63;
        Arrays.fill(data, 4521, 4523, (byte)57);
        data[4523] = (byte)63;
        Arrays.fill(data, 4524, 4526, (byte)57);
        Arrays.fill(data, 4526, 4528, (byte)63);
        Arrays.fill(data, 4528, 4535, (byte)57);
        Arrays.fill(data, 4535, 4537, (byte)63);
        data[4537] = (byte)57;
        data[4538] = (byte)63;
        data[4539] = (byte)57;
        Arrays.fill(data, 4540, 4547, (byte)63);
        Arrays.fill(data, 4547, 4587, (byte)57);
        data[4587] = (byte)63;
        Arrays.fill(data, 4588, 4592, (byte)57);
        data[4592] = (byte)63;
        Arrays.fill(data, 4593, 4601, (byte)57);
        data[4601] = (byte)63;
        Arrays.fill(data, 4602, 7680, (byte)57);
        Arrays.fill(data, 7680, 7836, (byte)63);
        Arrays.fill(data, 7836, 7840, (byte)57);
        Arrays.fill(data, 7840, 7930, (byte)63);
        Arrays.fill(data, 7930, 7936, (byte)57);
        Arrays.fill(data, 7936, 7958, (byte)63);
        Arrays.fill(data, 7958, 7960, (byte)57);
        Arrays.fill(data, 7960, 7966, (byte)63);
        Arrays.fill(data, 7966, 7968, (byte)57);
        Arrays.fill(data, 7968, 8006, (byte)63);
        Arrays.fill(data, 8006, 8008, (byte)57);
        Arrays.fill(data, 8008, 8014, (byte)63);
        Arrays.fill(data, 8014, 8016, (byte)57);
        Arrays.fill(data, 8016, 8024, (byte)63);
        data[8024] = (byte)57;
        data[8025] = (byte)63;
        data[8026] = (byte)57;
        data[8027] = (byte)63;
        data[8028] = (byte)57;
        data[8029] = (byte)63;
        data[8030] = (byte)57;
        Arrays.fill(data, 8031, 8062, (byte)63);
        Arrays.fill(data, 8062, 8064, (byte)57);
        Arrays.fill(data, 8064, 8117, (byte)63);
        data[8117] = (byte)57;
        Arrays.fill(data, 8118, 8125, (byte)63);
        data[8125] = (byte)57;
        data[8126] = (byte)63;
        Arrays.fill(data, 8127, 8130, (byte)57);
        Arrays.fill(data, 8130, 8133, (byte)63);
        data[8133] = (byte)57;
        Arrays.fill(data, 8134, 8141, (byte)63);
        Arrays.fill(data, 8141, 8144, (byte)57);
        Arrays.fill(data, 8144, 8148, (byte)63);
        Arrays.fill(data, 8148, 8150, (byte)57);
        Arrays.fill(data, 8150, 8156, (byte)63);
        Arrays.fill(data, 8156, 8160, (byte)57);
        Arrays.fill(data, 8160, 8173, (byte)63);
        Arrays.fill(data, 8173, 8178, (byte)57);
        Arrays.fill(data, 8178, 8181, (byte)63);
        data[8181] = (byte)57;
        Arrays.fill(data, 8182, 8189, (byte)63);
        Arrays.fill(data, 8189, 8192, (byte)57);
        Arrays.fill(data, 8192, 8204, (byte)9);
        Arrays.fill(data, 8204, 8206, (byte)57);
        Arrays.fill(data, 8206, 8255, (byte)9);
        Arrays.fill(data, 8255, 8257, (byte)25);
        Arrays.fill(data, 8257, 8304, (byte)9);
        Arrays.fill(data, 8304, 8400, (byte)57);
        Arrays.fill(data, 8400, 8413, (byte)59);
        Arrays.fill(data, 8413, 8417, (byte)57);
        data[8417] = (byte)59;
        Arrays.fill(data, 8418, 8486, (byte)57);
        data[8486] = (byte)63;
        Arrays.fill(data, 8487, 8490, (byte)57);
        Arrays.fill(data, 8490, 8492, (byte)63);
        Arrays.fill(data, 8492, 8494, (byte)57);
        data[8494] = (byte)63;
        Arrays.fill(data, 8495, 8576, (byte)57);
        Arrays.fill(data, 8576, 8579, (byte)63);
        Arrays.fill(data, 8579, 8592, (byte)57);
        Arrays.fill(data, 8592, 11264, (byte)9);
        Arrays.fill(data, 11264, 12272, (byte)57);
        Arrays.fill(data, 12272, 12289, (byte)9);
        Arrays.fill(data, 12289, 12293, (byte)57);
        data[12293] = (byte)59;
        data[12294] = (byte)57;
        data[12295] = (byte)63;
        Arrays.fill(data, 12296, 12321, (byte)57);
        Arrays.fill(data, 12321, 12330, (byte)63);
        Arrays.fill(data, 12330, 12336, (byte)59);
        data[12336] = (byte)57;
        Arrays.fill(data, 12337, 12342, (byte)59);
        Arrays.fill(data, 12342, 12353, (byte)57);
        Arrays.fill(data, 12353, 12437, (byte)63);
        Arrays.fill(data, 12437, 12441, (byte)57);
        Arrays.fill(data, 12441, 12443, (byte)59);
        Arrays.fill(data, 12443, 12445, (byte)57);
        Arrays.fill(data, 12445, 12447, (byte)59);
        Arrays.fill(data, 12447, 12449, (byte)57);
        Arrays.fill(data, 12449, 12539, (byte)63);
        data[12539] = (byte)57;
        Arrays.fill(data, 12540, 12543, (byte)59);
        Arrays.fill(data, 12543, 12549, (byte)57);
        Arrays.fill(data, 12549, 12589, (byte)63);
        Arrays.fill(data, 12589, 19968, (byte)57);
        Arrays.fill(data, 19968, 40870, (byte)63);
        Arrays.fill(data, 40870, 44032, (byte)57);
        Arrays.fill(data, 44032, 55204, (byte)63);
        Arrays.fill(data, 55204, 55296, (byte)57);
        Arrays.fill(data, 55296, 57344, (byte)0);
        Arrays.fill(data, 57344, 63744, (byte)9);
        Arrays.fill(data, 63744, 64976, (byte)57);
        Arrays.fill(data, 64976, 65008, (byte)9);
        Arrays.fill(data, 65008, 65534, (byte)57);
        Arrays.fill(data, 65534, 65536, (byte)0);
    }

    /**
     * Get all the characters in a given category, as an integer set. This must be one of the four
     * name classes: Name characters or Name Start characters in XML 1.0 or XML 1.1. (This method
     * is used to populate the data tables used by the regular expression translators)
     * @param mask identifies the properties of the required category
     * @return the set of characters in the given category.
     */

    public static IntRangeSet getCategory(byte mask) {
        final IntRangeSet irs = new IntRangeSet();
        for (int i=0; i<65536; i++) {
            if ((data[i]&mask) != 0) {
                irs.add(i);
            }
        }
        if ((mask & (NAME_START_11_MASK | NAME_11_MASK)) != 0) {
            irs.addRange(UTF16CharacterSet.NONBMP_MIN, MAX_XML11_NAME_CHAR);
        }
        return irs;
    }
    
}

// Copyright (c) 2009 Saxonica Limited. All rights reserved.

// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Saxonica Limited.
//
// Portions created by __ are Copyright (C) __. All Rights Reserved.
//
// Contributor(s): 	None
