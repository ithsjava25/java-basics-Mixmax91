package com.example;

import java.time.LocalDate;

public class Logic {

    public static LocalDate checkDate(String date){
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(date);
        } else {
            throw new IllegalArgumentException("invalid date");
        }
    }

    public static String checkZone(String zone){
        if (zone.equalsIgnoreCase("SE1")
                || zone.equalsIgnoreCase("SE2")
                || zone.equalsIgnoreCase("SE3")
                || zone.equalsIgnoreCase("SE4")) {
            return zone.toUpperCase();
        } else {
            throw new IllegalArgumentException("invalid zone"); //Throw exception if zone is invalid
        }
    }

    public static int parseCharging(String time) {
        time = time.replace("h", "");
        int charging = Integer.parseInt(time);

        if (charging == 2 || charging == 4 || charging == 8) {
            return charging;
        } else {
            throw new IllegalArgumentException("Not a valid charging time");
        }
    }
}
