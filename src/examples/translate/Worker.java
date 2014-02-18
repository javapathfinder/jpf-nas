package translate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import com.google.api.GoogleAPI;
import com.google.api.GoogleAPIException;
import com.google.api.translate.Language;
import com.google.api.translate.Translate;

public class Worker extends Thread {

  final String key = "AIzaSyDKhM1hytGQCzJUG5wogKydRNL9EUZaOKE";
  final String referrer = "http://www.nastaran.ca/";

  static String[] frenchTranslations = { "Salut", "Au revoir", "Monde" };
  static String[] spanishTranslations = { "Hola", "Bye", "Mundo" };
  
  private String[] translations;
  
  private Socket socket;
  
  String id;

  Worker (Socket socket) {
    this.socket = socket;
  }

  public void run () {
    GoogleAPI.setHttpReferrer(referrer);
    GoogleAPI.setKey(key);

    try {
      OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
      InputStreamReader istr = new InputStreamReader(socket.getInputStream());
      BufferedReader in = new BufferedReader(istr);

      Language language = null;
      while(language == null) {
        String languageReqest = in.readLine();
        language = processLanaguageRequest(languageReqest);
        
        if(language != null) {
          out.write("valid" + "\n");
          id = "[" + languageReqest.toUpperCase() + " Worker]";
          System.out.println(id + " starts!");
        } else {
          out.write("invalid" + "\n");
        }
      }
      
      String phrase;
      int i = 0;
      while ((phrase = in.readLine()) != null) {
        System.out.println(id + " translating... ");
        
        String translation;
        if(Translator.simulate) {
          // simulated translator
          translation = translations[i++];
        } else {
          translation = Translate.DEFAULT.execute(phrase, Language.ENGLISH, language);
        }
        
        System.out.println(id + " sending translation..." + translation);
        out.write(translation + "\n");
      }
      
      System.out.println(id + " done!");
      socket.close();
    } catch (IOException e) {
    } 
    catch (GoogleAPIException e) {
      System.out.println("GoogleAPI Failed");
      e.printStackTrace();
    }
  }
  
  public Language processLanaguageRequest (String language) {
    if(language == null || !language.startsWith("language::")) {
      return null;
    } 
    
    language = language.substring(language.lastIndexOf(":")+1);
    
    if (language.equalsIgnoreCase("french")) {
      translations = frenchTranslations;
      return Language.FRENCH;
    } else if (language.equalsIgnoreCase("spanish")) {
      translations = spanishTranslations;
      return Language.SPANISH; 
    } else {
      System.out.println("Sorry. We are not supporting " + language);
      return null;
    }
  }
}

