package com.example;

import com.example.api.ElpriserAPI;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Labboration 1 for ITHS 2025
 *
 * Uses ElpriserAPI and provides information about current and
 * upcoming electric prices from
 * https://www.elprisetjustnu.se/api/v1/prices
 * Usage:
 * --zone SE1|SE2|SE3|SE4 (required)
 * --date YYYY-MM-DD (optional, defaults to current date)
 * --sorted (optional, to display prices in descending order)
 * --charging 2h|4h|8h (optional, to find optimal charging windows)
 * --help (optional, to display usage information)
 *
 * @author Daniel Marton
 */


public class Main {

    public static final Locale SWEDISH = new Locale("sv", "SE");
    public static final NumberFormat PRICE_FORMAT = NumberFormat.getNumberInstance(SWEDISH);
    public static final int CONVERT_TO_ORE = 100;
    public static final DateTimeFormatter HOUR_ONLY = DateTimeFormatter.ofPattern("HH");
    public static final DateTimeFormatter HOUR_AND_MINUTES = DateTimeFormatter.ofPattern("HH:mm");


    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();
        List<ElpriserAPI.Elpris> allaPriser =  new ArrayList<>();
        String zone = "";
        int chargingTime = 0;
        LocalDate date = LocalDate.now(); //Set as current date as default
        boolean sorted = false;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--zone":
                        String zoneArg = args[++i].trim();
                        if (zoneArg.equalsIgnoreCase("SE1")
                                || zoneArg.equalsIgnoreCase("SE2")
                                || zoneArg.equalsIgnoreCase("SE3")
                                || zoneArg.equalsIgnoreCase("SE4")) {
                            zone = zoneArg.toUpperCase();

                        } else {
                            throw new IllegalArgumentException("invalid zone");
                        }
                        break;

                    case "--date":
                        String dateArg = args[++i].trim();
                        if (dateArg.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            date = LocalDate.parse(dateArg);

                        } else {
                            throw new IllegalArgumentException("invalid date");
                        }
                        break;

                    case "--charging":
                        String timeArg = args[++i].trim();
                        timeArg = timeArg.replace("h", "");
                        chargingTime = Integer.parseInt(timeArg);
                        if (chargingTime != 2 && chargingTime != 4 && chargingTime != 8) {
                            throw new IllegalArgumentException("Not a valid charging time");
                        }
                        break;

                    case "--sorted":
                        sorted = true;
                        break;

                    case "--help":
                        helpMenu();
                        break;

                    default:
                        throw new IllegalArgumentException("unknown input");
                }
            }
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        if (zone.isEmpty()) {
            zone = "SE1";
            helpMenu();
        }

        ElpriserAPI.Prisklass prisklass = ElpriserAPI.Prisklass.valueOf(zone);
        List<ElpriserAPI.Elpris> dagensPriser = elpriserAPI.getPriser(date, prisklass);
        List<ElpriserAPI.Elpris> framtidaPriser = elpriserAPI.getPriser(date.plusDays(1), prisklass);
        allaPriser.addAll(dagensPriser);
        allaPriser.addAll(framtidaPriser);

//        ZoneId zoneId = ZoneId.of("Europe/Stockholm");
//        ZonedDateTime now = ZonedDateTime.now(zoneId);
//        ZonedDateTime cutoff = now.plusHours(24);

//        System.out.println(allaPriser);
//        for (ElpriserAPI.Elpris elpris : allaPriser) {
//            if (elpris.timeStart().isAfter(now) && elpris.timeEnd().isBefore(cutoff)) {
//                System.out.println(elpris);
//            }
//        }


        if (chargingTime != 0) {
            chargingWindow(allaPriser, chargingTime);
        } else {
            if (sorted) {
                printPricesSorted(allaPriser);
            } else {
                printLowest(allaPriser);
                printHighest(allaPriser);
                printPrices(allaPriser);
            }
        }
    }


    public static void printPrices(List<ElpriserAPI.Elpris> allaPriser) {
        double sum = 0;
        double average = 0;

        if (isEmpty(allaPriser)) return;

        for (ElpriserAPI.Elpris pris : allaPriser) {
            sum += pris.sekPerKWh();
        }

        average = (sum / allaPriser.size()) * CONVERT_TO_ORE;
        System.out.printf("Medelpris: %.2f öre\n", average);
    }

    public static void printPricesSorted(List<ElpriserAPI.Elpris> allaPriser) {
        if (isEmpty(allaPriser)) return;
        allaPriser.sort(Comparator.comparing(ElpriserAPI.Elpris::sekPerKWh).reversed());
        for (ElpriserAPI.Elpris pris : allaPriser) {
            System.out.printf("%s-%s %.2f öre\n", pris.timeStart().format(HOUR_ONLY), pris.timeEnd().format(HOUR_ONLY), (pris.sekPerKWh() * CONVERT_TO_ORE));
        }
    }

    public static boolean isEmpty(List<ElpriserAPI.Elpris> elpriser) {
        if (elpriser.isEmpty()) {
            System.out.println("Inga priser tillgängliga.");
            return true;
        }
        return false;
    }

    public static void chargingWindow(List<ElpriserAPI.Elpris> allaPriser, int chargingTime) {
        if (isEmpty(allaPriser)) return;

        double minSum = Double.MAX_VALUE;
        int bestStart = 0;
        double average = 0;

        for (int i = 0; i <= allaPriser.size() - chargingTime; i++) {
            double sum = 0;
            for (int j = 0; j < chargingTime; j++) {
                sum += allaPriser.get(i + j).sekPerKWh();
            }
            if (sum < minSum) {
                minSum = sum;
                bestStart = i;
            }
            average = minSum / chargingTime;
        }
        ElpriserAPI.Elpris start = allaPriser.get(bestStart);
        ElpriserAPI.Elpris end = allaPriser.get(bestStart + chargingTime - 1);


        System.out.printf("Påbörja laddning kl %s för %d timmars laddning\nMedelpris för fönster: %.2f öre"
                , start.timeStart().format(HOUR_AND_MINUTES), chargingTime, average *  CONVERT_TO_ORE);
    }

    public static void printHighest(List<ElpriserAPI.Elpris> allaPriser) {
        if (isEmpty(allaPriser)) return;

        if (allaPriser.size() == 96) {
            printHighest96(allaPriser);
            return;
        }

        ElpriserAPI.Elpris maxPrice = allaPriser.getFirst();
        for (ElpriserAPI.Elpris pris : allaPriser) {
            if (pris.sekPerKWh() > maxPrice.sekPerKWh()) {
                maxPrice = pris;
            }
        }
        System.out.printf("Högsta pris: %s-%s %.2f öre\n", maxPrice.timeStart().format(HOUR_ONLY), maxPrice.timeEnd().format(HOUR_ONLY), (maxPrice.sekPerKWh()) * CONVERT_TO_ORE);
    }

    public static void printHighest96(List<ElpriserAPI.Elpris> allaPriser) {
        List<ElpriserAPI> hourlyPrices = new ArrayList<>();
        double max = 0;
        int maxIndex = 0;

        for (int hour = 0; hour < 24; hour++) {
            double sum = 0;
            for (int j = 0; j < 4; j++) {
                sum += allaPriser.get(hour * 4 + j).sekPerKWh();
            }
            double average = sum / 4;
            if (max < average) {
                max = average;
                maxIndex = hour;
            }

        }
        ElpriserAPI.Elpris start = allaPriser.get(maxIndex * 4);
        ElpriserAPI.Elpris end = allaPriser.get(maxIndex * 4 + 3);

        System.out.printf("Högsta pris: %s-%s %.2f öre\n",
                start.timeStart().format(HOUR_ONLY),
                end.timeEnd().format(HOUR_ONLY),
                max * CONVERT_TO_ORE
        );
    }

    public static void printLowest(List<ElpriserAPI.Elpris> allaPriser) {
        if (isEmpty(allaPriser)) return;
        if (allaPriser.size() == 96) {
            printLowest96(allaPriser);
            return;
        }

        ElpriserAPI.Elpris minPrice = allaPriser.getFirst();
        for (ElpriserAPI.Elpris pris : allaPriser) {
            if (pris.sekPerKWh() < minPrice.sekPerKWh()) {
                minPrice = pris;
            }
        }
        System.out.printf("Lägsta pris: %s-%s %.2f öre\n", minPrice.timeStart().format(HOUR_ONLY), minPrice.timeEnd().format(HOUR_ONLY), (minPrice.sekPerKWh()) * CONVERT_TO_ORE);
    }

    public static void printLowest96(List<ElpriserAPI.Elpris> allaPriser) {
        double min = Double.MAX_VALUE;
        int minIndex = 0;

        for (int hour = 0; hour < 24; hour++) {
            double sum = 0;
            for (int q = 0; q < 4; q++) {
                sum += allaPriser.get(hour * 4 + q).sekPerKWh();
            }
            double average = sum / 4;
            if (average < min) {
                min = average;
                minIndex = hour;
            }
        }

        ElpriserAPI.Elpris start = allaPriser.get(minIndex * 4);
        ElpriserAPI.Elpris end = allaPriser.get(minIndex * 4 + 3);

        System.out.printf("Lägsta pris: %s-%s %.2f öre\n",
                start.timeStart().format(HOUR_ONLY),
                end.timeEnd().format(HOUR_ONLY),
                min * CONVERT_TO_ORE);
    }

    public static void helpMenu(){
        System.out.println("Usage:");
        System.out.println("--zone SE1/SE2/SE3/SE4");
        System.out.println("--date YYYY-MM-DD");
        System.out.println("--sorted prints a sorted list");
        System.out.println("--charging 2h/4h/8h/");
    }

}

