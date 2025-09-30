package com.example;

import com.example.api.ElpriserAPI;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    static {PRICE_FORMAT.setMinimumFractionDigits(2); PRICE_FORMAT.setMaximumFractionDigits(2);}
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
                    case "--zone" -> zone = Logic.checkZone(args[++i].trim()); //Check if the zone is entered correctly
                    case "--date" -> date = Logic.checkDate(args[++i].trim()); //Match date to see if the format is correct, then convert it to LocalDate
                    case "--charging" -> chargingTime = Logic.parseCharging(args[++i].trim()); //Remove the "h" and capture the number in chargingTime if its 2, 4 or 8
                    case "--sorted" -> sorted = true; //Set sorted to true, so the sorted method runs
                    case "--help" -> helpMenu();
                    default -> throw new IllegalArgumentException("unknown input"); //Set default exception to cover for any unknown input errors
                }
            }
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage()); //Capture the exception here
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
        //Now we add today's and tomorrow's prices to the new list
        //First we need to check if the user entered a custom date
        if (date.isEqual(LocalDate.now())) {
            allaPriser.addAll(filterPrices(dagensPriser));
            allaPriser.addAll(filterPrices(framtidaPriser));
        } else {
            allaPriser.addAll(dagensPriser);
            allaPriser.addAll(framtidaPriser);
        }

        //First we check if chargingTime is entered
        if (chargingTime != 0) {
            printChargingWindow(allaPriser, chargingTime);
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


    public static List<ElpriserAPI.Elpris> filterPrices (List<ElpriserAPI.Elpris> priser) {
        ZoneId zoneId = ZoneId.of("Europe/Stockholm");
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime until = now.plusHours(24);
        List<ElpriserAPI.Elpris> filtered =  new ArrayList<>();
        //Here we check if the time is after or before the next 24h and adds them to a list that we return
        for (ElpriserAPI.Elpris elpris : priser) {
            if (!elpris.timeEnd().isBefore(now) && !elpris.timeStart().isAfter(until)) {
                filtered.add(elpris);
            }
        }
        return filtered;
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

    public static void printChargingWindow(List<ElpriserAPI.Elpris> allaPriser, int chargingTime) {
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

