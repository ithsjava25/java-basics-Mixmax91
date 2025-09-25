package com.example;

import com.example.api.ElpriserAPI;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    static {
        PRICE_FORMAT.setMinimumFractionDigits(2);
        PRICE_FORMAT.setMaximumFractionDigits(2);
    }

    public static final int CONVERT_TO_ORE = 100;

    public static final DateTimeFormatter HOUR_ONLY = DateTimeFormatter.ofPattern("HH");
    public static final DateTimeFormatter HOUR_AND_MINUTES = DateTimeFormatter.ofPattern("HH:mm");

    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();

        String zone = "";
        int chargingTime = 0;
        LocalDate date = LocalDate.now(); //Set as current date as default
        boolean sorted = false;

        //Start try catch block to catch exception
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--zone":
                        //Check if the zone is entered correctly
                        String zoneArg = args[++i].trim();
                        if (zoneArg.equalsIgnoreCase("SE1")
                                || zoneArg.equalsIgnoreCase("SE2")
                                || zoneArg.equalsIgnoreCase("SE3")
                                || zoneArg.equalsIgnoreCase("SE4")) {
                            zone = zoneArg.toUpperCase();
                        } else {
                            throw new IllegalArgumentException("invalid zone"); //Throw exception if zone is invalid
                        }
                        break;

                    case "--date":
                        //Match date to see if the format is correct, then convert it to LocalDate
                        String dateArg = args[++i].trim();
                        if (dateArg.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            date = LocalDate.parse(dateArg);
                        } else {
                            throw new IllegalArgumentException("invalid date");
                        }
                        break;

                    case "--charging":
                        //Remove the "h" and capture the number in chargingTime if its 2, 4 or 8
                        String timeArg = args[++i].trim();
                        timeArg = timeArg.replace("h", "");
                        chargingTime = Integer.parseInt(timeArg);

                        if (chargingTime != 2 && chargingTime != 4 && chargingTime != 8) {
                            throw new IllegalArgumentException("Not a valid charging time");
                        }
                        break;

                    case "--sorted":
                        //Set sorted to true, so the sorted method runs
                        sorted = true;
                        break;

                    case "--help":
                        helpMenu();
                        break;

                    default:
                        //Set defailt exception to cover for any unknown input errors
                        throw new IllegalArgumentException("unknown input");
                }
            }
        } catch (IllegalArgumentException e) {
            //Capture the exception here
            System.out.println(e.getMessage());
        }
        //If there is no --zone command, it will be empty
        //I set it to SE1 as default and print the helpMenu()
        if (zone.isEmpty()) {
            zone = "SE1";
            helpMenu();
        }

        //Use the string zone from the args to get the enum
        ElpriserAPI.Prisklass prisklass = ElpriserAPI.Prisklass.valueOf(zone);
        //Here we get the days prices for specific zone
        List<ElpriserAPI.Elpris> dagensPriser = elpriserAPI.getPriser(date, prisklass);
        //Here we get tomorrow's prices with date.plusDays(1)
        List<ElpriserAPI.Elpris> framtidaPriser = elpriserAPI.getPriser(date.plusDays(1), prisklass); //
        //Create a list that will contain todays and tomorrow's prices
        List<ElpriserAPI.Elpris> allaPriser =  new ArrayList<>();
        //Now we add todays and tommorrow's prices to the new list
        allaPriser.addAll(dagensPriser);
        allaPriser.addAll(framtidaPriser);

//        This part is not needed and not finished, but it will filter the prices from now and for the next 24hours
//        ZoneId zoneId = ZoneId.of("Europe/Stockholm");
//        ZonedDateTime now = ZonedDateTime.now(zoneId);
//        ZonedDateTime cutoff = now.plusHours(24);

//        System.out.println(allaPriser);
//        for (ElpriserAPI.Elpris elpris : allaPriser) {
//            if (elpris.timeStart().isAfter(now) && elpris.timeEnd().isBefore(cutoff)) {
//                System.out.println(elpris);
//            }
//        }

        //First we check if chargingTime is entered
        if (chargingTime != 0) {
            chargingWindow(allaPriser, chargingTime);
        } else if (sorted) {
            //Then we check if sorted is entered
            printPricesSorted(allaPriser);
        } else {
            //If none of those, we print lowest, highest and average
            printLowest(allaPriser);
            printHighest(allaPriser);
            printAveragePrices(allaPriser);
        }
    }

    public static void printAveragePrices(List<ElpriserAPI.Elpris> allaPriser) {
        double sum = 0;
        //Check if list is empty
        if (isEmpty(allaPriser)) return;
        //Loop and add all prices into sum
        for (ElpriserAPI.Elpris pris : allaPriser) {
            sum += pris.sekPerKWh();
        }
        //Calculate average and convert to öre, then print
        double average = (sum / allaPriser.size()) * CONVERT_TO_ORE;
        System.out.printf("Medelpris: %s öre\n", PRICE_FORMAT.format(average));
    }

    public static void printPricesSorted(List<ElpriserAPI.Elpris> allaPriser) {
        //Check if list is empty
        if (isEmpty(allaPriser)) return;
        //Sort the list, then reverse it to get descending order
        allaPriser.sort(Comparator.comparing(ElpriserAPI.Elpris::sekPerKWh).reversed());
        //Now we loop and print the sorted list
        for (ElpriserAPI.Elpris pris : allaPriser) {
            double convertedPrice = pris.sekPerKWh() * CONVERT_TO_ORE;
            System.out.printf("%s-%s %s öre\n", pris.timeStart().format(HOUR_ONLY), pris.timeEnd().format(HOUR_ONLY), PRICE_FORMAT.format(convertedPrice));
        }
    }

    public static boolean isEmpty(List<ElpriserAPI.Elpris> elpriser) {
        //Checks if the list is empty and prints a message, returns true or false
        if (elpriser.isEmpty()) {
            System.out.println("Inga priser tillgängliga.");
            return true;
        }
        return false;
    }

    public static void chargingWindow(List<ElpriserAPI.Elpris> allaPriser, int chargingTime) {
        //Check if list is empty
        if (isEmpty(allaPriser)) return;

        double minSum = Double.MAX_VALUE;
        int bestStart = 0;
        double average = 0;
        //We do a sliding window algorithm, compares the sum of each window and saves the lowest price window
        for (int i = 0; i <= allaPriser.size() - chargingTime; i++) {
            double sum = 0;
            for (int j = 0; j < chargingTime; j++) {
                sum += allaPriser.get(i + j).sekPerKWh();
            }
            if (sum < minSum) {
                minSum = sum;
                bestStart = i; //Save index for printing
            }
            //Calculate average price for the window
            average = minSum / chargingTime;
        }

        ElpriserAPI.Elpris start = allaPriser.get(bestStart); //Set start time
        double convertedPrice = average * CONVERT_TO_ORE;
        System.out.printf("Påbörja laddning kl %s för %d timmars laddning\nMedelpris för fönster: %s öre"
                , start.timeStart().format(HOUR_AND_MINUTES), chargingTime, PRICE_FORMAT.format(convertedPrice));
    }

    public static void printHighest(List<ElpriserAPI.Elpris> allaPriser) {
        //Check if list is empty
        if (isEmpty(allaPriser)) return;
        //Check if the list has 96 entries
        if (allaPriser.size() == 96) {
            printHighest96(allaPriser);
            return;
        }
        //For normal data with 24 entries, we compare each price and saves the largest
        ElpriserAPI.Elpris maxPrice = allaPriser.getFirst();
        for (ElpriserAPI.Elpris pris : allaPriser) {
            if (pris.sekPerKWh() > maxPrice.sekPerKWh()) {
                maxPrice = pris;
            }
        }
        double convertedPrice = maxPrice.sekPerKWh() * CONVERT_TO_ORE;
        System.out.printf("Högsta pris: %s-%s %s öre\n", maxPrice.timeStart().format(HOUR_ONLY),
                                                            maxPrice.timeEnd().format(HOUR_ONLY),
                                                            PRICE_FORMAT.format(convertedPrice));
    }

    public static void printHighest96(List<ElpriserAPI.Elpris> allaPriser) {
        double max = 0;
        int maxIndex = 0;
        //This is a sweet little algorithm
        for (int hour = 0; hour < 24; hour++) {                     //First we loop 24 times, this is the 24 current hours
            double sum = 0;
            for (int j = 0; j < 4; j++) {                         //Now we loop 4 times, since there is 4 quarters for each hour
                sum += allaPriser.get(hour * 4 + j).sekPerKWh(); //We save the sum and uses the (hour * 4) to start at the
            }                                                   //correct hour. We add +j to iterate each of the 4 quarters
            double average = sum / 4;                          // We save the sum of the prices of the quarters and
            if (max < average) {                              // divide by 4 to get the hours average price.
                max = average;                               // Now we can compare and save the max average to print
                maxIndex = hour;                            // We also want to save the index for the start hour
            }
        }
        ElpriserAPI.Elpris start = allaPriser.get(maxIndex * 4); //We need to * 4 again to end up in the correct spot
        ElpriserAPI.Elpris end = allaPriser.get(maxIndex * 4 + 3); //Add 3 to get the end time
        double convertedPrice = max * CONVERT_TO_ORE;
        System.out.printf("Högsta pris: %s-%s %s öre\n",
                start.timeStart().format(HOUR_ONLY),
                end.timeEnd().format(HOUR_ONLY),
                PRICE_FORMAT.format(convertedPrice)
        );
    }

    public static void printLowest(List<ElpriserAPI.Elpris> allaPriser) {
        //This is the same as printHighest but < instead of >
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
        double convertedPrice = minPrice.sekPerKWh() * CONVERT_TO_ORE;
        System.out.printf("Lägsta pris: %s-%s %s öre\n", minPrice.timeStart().format(HOUR_ONLY),
                                                            minPrice.timeEnd().format(HOUR_ONLY),
                                                            PRICE_FORMAT.format(convertedPrice));
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
        double convertedPrice = min * CONVERT_TO_ORE;
        System.out.printf("Lägsta pris: %s-%s %s öre\n",
                start.timeStart().format(HOUR_ONLY),
                end.timeEnd().format(HOUR_ONLY),
                PRICE_FORMAT.format(convertedPrice));
    }

    public static void helpMenu(){
        //Prints info for the user
        System.out.println("Usage:");
        System.out.println("--zone SE1/SE2/SE3/SE4");
        System.out.println("--date YYYY-MM-DD");
        System.out.println("--sorted prints a sorted list");
        System.out.println("--charging 2h/4h/8h/");
    }

}

