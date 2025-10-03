package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Main {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    public static void main(String[] args) {
        ElpriserAPI api = new ElpriserAPI();

        LocalDate datum = LocalDate.now();
        String zon = null, laddningstid = null;
        boolean sorterad = false;
        int laddningstimmar = 0;




        /* VAD GÖR PROGRAMMET?
        FEATURES
          1.   downloadar priser ör varje TIMME (idag och ev kommande dag)
          2.  räknar ut och skriver ut medelvärdet för 24h framåt
          3.  identifierar och skriver ut den dyraste TIMMEN och billigaste TIMMEN (om fler timmar har samma pris, ska den första timmen anges.)

          4. Använder SlidingWindow för billigaste 2h, 4h, 8h.

          5. Zon kan väljas både via interaktivt och via terminal
*/

        //om inga args skickas in ELLER om man skriver hjälp
        if (args.length == 0 || contains(args, "--help")) {
            printHelp();
            return;
        }


        //gå genom alla alternativ i clin
        //todo Snygga till den här koden sen.
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    if (i + 1 < args.length) {
                        zon = args[++i];
                    }
                    else{
                        System.out.println("Du måste ange en zon med --zone SE1|SE2|SE3|SE4");
                        return;
                    }break;
                case "--date":
                    if (i + 1 < args.length) {
                        try {
                            datum = LocalDate.parse(args[++i], DATE_FORMATTER);
                        }  catch (DateTimeParseException felDatum) {
                            System.out.println("Datum måste skrivas ut YYYY-MM-DD");
                            return;
                        }
                    }break;
                case "--sorted":
                        sorterad = true;
                        break;
                case "--charging":
                    if (i + 1 < args.length) {
                        laddningstid = args[++i];
                        switch (laddningstid) {
                            case "2h": laddningstimmar = 2;
                            break;

                            case "4h": laddningstimmar = 4;
                            break;

                            case "8h": laddningstimmar = 8;
                            break;

                            default:
                                System.out.println("Du måste ange 2h, 4h eller 8h");
                        }
                    }break;
                case "--help" :
                    printHelp();
                    return;

                default:
                    System.out.println("Okänt kommando, visar hjälpmenyn");
                    printHelp();

            }



        }

        //todo använd apin



    }

    private static boolean contains(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }

    private static void printHelp() {

        System.out.println("Alternativ:");
        System.out.println("  --date YYYY-MM-DD    Ange datum (om inget anges visas idag)");
        System.out.println("  --sorted             Sortera priser i fallande ordning");
        System.out.println("  --charging 2h|4h|8h  Hitta billigaste laddfönster");
        System.out.println("  --help               Visa denna hjälptext");
    }
}
