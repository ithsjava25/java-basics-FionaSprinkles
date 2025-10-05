package com.example;

import com.example.api.ElpriserAPI;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class Main {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {

        //Om --help eller inget anges, printa Alternativsmenyn.
        if (args.length == 0 || Arrays.asList(args).contains("--help")) {
            printHelp();
            return;
        }


        LocalDate datum = LocalDate.now();
        String zon = null;
        boolean sorterad = false;
        int laddningstimmar = 0;



        //Argument
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    if (i + 1 < args.length) {
                        zon = args[++i];
                    } else {
                        System.out.println("Ogiltigt angiven zon. Måste anges med SE1/SE2/SE3/SE4");
                        printHelp();
                        return;
                    }
                    break;

                case "--date":
                    if (i + 1 < args.length) {
                        String d = args[++i];
                        try {
                            datum = LocalDate.parse(d, DATE_FORMATTER);
                        } catch (DateTimeParseException e) {
                            System.out.println("Ogiltigt datum. Datum måste skrivas YYYY-MM-DD");
                            return;
                        }
                    }
                    break;

                case "--sorted":
                    sorterad = true;
                    break;

                case "--charging":
                    if (i + 1 < args.length) {
                        String timme = args[++i].toLowerCase();
                        if (timme.endsWith("h")) timme = timme.substring(0, timme.length() - 1);
                        try {
                            int timmar = Integer.parseInt(timme);
                            if (timmar != 2 && timmar != 4 && timmar != 8) {
                                System.out.println("Ogiltigt laddningsfönster. Ange 2h/4h/8h.");
                                return;
                            }
                            laddningstimmar = timmar;
                        } catch (NumberFormatException felNummer) {
                            System.out.println("Ogiltigt värde. Ange 2h/4h/8h.");
                            return;
                        }
                    } else {
                        System.out.println("Ogiltigt värde. Ange 2h/4h/8h.");
                        return;
                    }
                    break;

                case "--help":
                    printHelp();
                    return;

                default:
                    System.out.println("Okänt kommando: " + args[i]);
                    printHelp();
                    return;
            }
        }

        // Kontrollera om zonen är giltig. Konverterar till enum.
        ElpriserAPI.Prisklass prisklass = hamtaZon(zon);
        if (prisklass == null) return;

        // Hämta priser (idag + eventuell morgondag)
        ElpriserAPI api = new ElpriserAPI();
        List<ElpriserAPI.Elpris> allaPriser = hamtaAllaPriser(api, datum, prisklass);

        if (allaPriser.isEmpty()) {
            System.out.println("Inga priser för vald zon/datum.");
            return;
        }

        // Medelpris för hela dygnet
        double medelSekPerKWh = medelVarde(allaPriser);
        System.out.println("Medelpris för dygn: " + formatOre(medelSekPerKWh) + " öre");

        // Lägsta och högsta timme
        ElpriserAPI.Elpris billig = billigasteTimme(allaPriser);
        ElpriserAPI.Elpris dyrast = dyrasteTimme(allaPriser);

        if (billig != null && dyrast != null) {
            String hittaBillig = String.format("%02d-%02d", billig.timeStart().getHour(), billig.timeEnd().getHour());
            String hittaDyr = String.format("%02d-%02d", dyrast.timeStart().getHour(), dyrast.timeEnd().getHour());
            System.out.println("Lägsta pris: " + hittaBillig + " " + formatOre(billig.sekPerKWh()) + " öre");
            System.out.println("Högsta pris: " + hittaDyr + " " + formatOre(dyrast.sekPerKWh()) + " öre");
        }

        // sortera i stigande ordning
        if (sorterad) {
            prislista(allaPriser, true);
        }

        // Laddningsfönster (sliding window)
        if (laddningstimmar > 0) {
            slidingWindow(allaPriser, laddningstimmar);
        }
    }

    private static void printHelp() {

        System.out.println("Usage: java -cp target/classes com.example.Main");
        System.out.println("Alternativ:");
        System.out.println("--zone <SE1|SE2|SE3|SE4>   Välj Elområde");
        System.out.println("--date YYYY-MM-DD          Datum, standard är dagens datum");
        System.out.println("--sorted                   Sortera priser (billigast först)");
        System.out.println("--charging 2h|4h|8h        Hitta billigaste laddningsfönster");
        System.out.println("--help                     Visa denna hjälptext");
    }

    // Konvertera zon-sträng till enum
    // För praxis skull
    private static ElpriserAPI.Prisklass hamtaZon(String zon) {
        if (zon == null || zon.trim().isEmpty()) {
            System.out.println("Du måste ange en zon (required).");
            return null;
        }
        try {
            return ElpriserAPI.Prisklass.valueOf(zon.trim().toUpperCase());
        } catch (IllegalArgumentException ogiltigZon) {
            System.out.println("Ogiltig zon. Välj mellan SE1, SE2, SE3 eller SE4.");
            return null;
        }
    }

    // Hämta dagens priser och lägg till morgondagen om den finns
    private static List<ElpriserAPI.Elpris> hamtaAllaPriser(ElpriserAPI api, LocalDate datum, ElpriserAPI.Prisklass zon) {
        List<ElpriserAPI.Elpris> alla = new ArrayList<>(api.getPriser(datum, zon));
        List<ElpriserAPI.Elpris> morgon = api.getPriser(datum.plusDays(1), zon);
        if (morgon != null && !morgon.isEmpty()) {
            alla.addAll(morgon);
        }
        return alla;
    }

    // Medelvärde
    private static double medelVarde(List<ElpriserAPI.Elpris> allaPriser) {
        if (allaPriser == null || allaPriser.isEmpty()) return 0.0;
        double sum = 0.0;
        for (ElpriserAPI.Elpris i : allaPriser) sum += i.sekPerKWh();
        return sum / allaPriser.size();
    }

    // Hitta billigaste timmen (vid lika, välj första)
    private static ElpriserAPI.Elpris billigasteTimme(List<ElpriserAPI.Elpris> allaPriser) {
        if (allaPriser.isEmpty()) return null;
        ElpriserAPI.Elpris min = allaPriser.get(0);
        for (ElpriserAPI.Elpris i : allaPriser) {
            if (i.sekPerKWh() < min.sekPerKWh()) min = i;
        }
        return min;
    }

    // Hitta dyraste timmen (vid lika, välj första)
    private static ElpriserAPI.Elpris dyrasteTimme(List<ElpriserAPI.Elpris> allaPriser) {
        if (allaPriser.isEmpty()) return null;
        ElpriserAPI.Elpris max = allaPriser.get(0);
        for (ElpriserAPI.Elpris i : allaPriser) {
            if (i.sekPerKWh() > max.sekPerKWh()) max = i;
        }
        return max;
    }

    // Formatera tillSEK
    private static String formatOre(double sekPerKWh) {
        double ore = sekPerKWh * 100.0;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("sv", "SE"));
        DecimalFormat df = new DecimalFormat("0.00", symbols);
        return df.format(ore);
    }

    // Skriv prislista. Om sorterad==true så sortera efter pris (billigast först) och tid.
    //Varningen för sorterad är för att programmet bara vet att när jag anropar den så är den alltid true.
    private static void prislista(List<ElpriserAPI.Elpris> allaPriser, boolean sorterad) {
        List<ElpriserAPI.Elpris> kopia = new ArrayList<>(allaPriser);

        // Sortera
        kopia.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh)
                .thenComparing(ElpriserAPI.Elpris::timeStart));

        // Filtrera bort exakta dubletter (pris + starttid)
        Set<String> skrivna = new HashSet<>();
        for (ElpriserAPI.Elpris i : kopia) {
            String key = i.timeStart().getHour() + "-" + i.timeEnd().getHour() + ":" + i.sekPerKWh();
            if (skrivna.add(key)) {
                int start = i.timeStart().getHour();
                int end = i.timeEnd().getHour();
                System.out.printf("%02d-%02d %s öre%n", start, end, formatOre(i.sekPerKWh()));
            }
        }
    }


    // Sliding window

    private static void slidingWindow(List<ElpriserAPI.Elpris> allaPriser, int timmar) {
        if (timmar > allaPriser.size()) {
            System.out.println("För få timmar med prisdata för att beräkna ett " + timmar + "h-fönster.");
            return;
        }

        double minSum;
        int bestStart = 0;

        // Första fönstret
        double sum = 0.0;
        for (int i = 0; i < timmar; i++) {
            sum += allaPriser.get(i).sekPerKWh();
        }
        minSum = sum;

        // Sliding window
        for (int i = 1; i <= allaPriser.size() - timmar; i++) {
            sum -= allaPriser.get(i - 1).sekPerKWh();
            sum += allaPriser.get(i + timmar - 1).sekPerKWh();
            if (sum < minSum) {
                minSum = sum;
                bestStart = i;
            }
        }


        ElpriserAPI.Elpris start = allaPriser.get(bestStart);
        int startTimme = start.timeStart().getHour();
        int slutTimme = (startTimme + timmar) % 24;

        System.out.printf("Påbörja laddning kl %02d:00 - %02d:00%n", startTimme, slutTimme);
        System.out.println("Medelpris för fönster: " + formatOre(minSum / timmar) + " öre");
    }



}
